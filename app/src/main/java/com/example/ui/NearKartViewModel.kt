package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.SupabaseClient
import com.example.data.local.LocalStore
import com.example.data.localization.Localization
import com.example.data.model.Cart
import com.example.data.model.CartItem
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.Product
import com.example.data.model.Shop
import com.example.data.model.ShopPaymentInfo
import com.example.data.model.UserLocation
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

data class NearKartUiState(
  val language: String = Localization.EN,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val session: UserSession? = null,
  val profile: UserProfile = UserProfile(),
  val location: UserLocation = UserLocation(),
  val shops: List<Shop> = emptyList(),
  val products: List<Product> = emptyList(),
  val searchResults: List<Product>? = null,
  val isSearching: Boolean = false,
  val cart: Cart? = null,
  val buyerOrders: List<Order> = emptyList(),
  // Seller State
  val myShop: Shop? = null,
  val sellerOrders: List<Order> = emptyList(),
  val sellerProducts: List<Product> = emptyList(),
  val vibrationEnabled: Boolean = true,
  val newOrderAlert: String? = null,
  val lastPlacedOrder: Order? = null
)

class NearKartViewModel(application: Application) : AndroidViewModel(application) {
  private val api = SupabaseClient()
  private val localStore = LocalStore(application)
  private val refreshMutex = Mutex()
  private var searchJob: Job? = null

  private val _uiState = MutableStateFlow(
    NearKartUiState(
      language = localStore.language,
      session = localStore.getSession(),
      profile = localStore.getProfile(),
      location = localStore.getLocation(),
      cart = localStore.getCart(),
      vibrationEnabled = localStore.vibrationEnabled,
      shops = emptyList(),
      products = emptyList()
    )
  )
  val uiState: StateFlow<NearKartUiState> = _uiState.asStateFlow()

  private var previousNewOrderCount = 0

  init {
    loadInitialData()
    startPeriodicSync()
  }

  fun setLanguage(lang: String) {
    localStore.language = lang
    _uiState.value = _uiState.value.copy(language = lang)
  }

  fun setErrorMessage(msg: String?) {
    _uiState.value = _uiState.value.copy(errorMessage = msg)
  }

  fun setLocation(location: UserLocation) {
    localStore.saveLocation(location)
    _uiState.value = _uiState.value.copy(location = location)
    if (_uiState.value.session != null) {
      viewModelScope.launch {
        try {
          withValidToken { sess ->
            api.updateMyProfile(
              name = _uiState.value.profile.name.ifBlank { null },
              phone = _uiState.value.profile.phone.ifBlank { null },
              location = location.fullAddress.ifBlank { location.area },
              area = location.area,
              address = _uiState.value.profile.address.ifBlank { null },
              token = sess.token
            )
          }
        } catch (_: Exception) {}
      }
    }
  }

  fun toggleVibration(enabled: Boolean) {
    localStore.vibrationEnabled = enabled
    _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
  }

  fun clearNewOrderAlert() {
    _uiState.value = _uiState.value.copy(newOrderAlert = null)
  }

  fun clearErrorMessage() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }

  fun clearLastPlacedOrder() {
    _uiState.value = _uiState.value.copy(lastPlacedOrder = null)
  }

  // --- Token Refresh & Single-Flight Guard ---
  private suspend fun ensureValidSession(forceRefresh: Boolean = false): UserSession? {
    val current = _uiState.value.session ?: return null
    val needsRefresh = forceRefresh ||
      (current.expiresAt > 0L && System.currentTimeMillis() > current.expiresAt - 30_000L)
    if (!needsRefresh) return current
    if (current.refresh.isBlank()) {
      clearDeadSession("Session expired. Please log in again.")
      return null
    }

    return refreshMutex.withLock {
      val latest = _uiState.value.session ?: return@withLock null
      if (!forceRefresh && latest.token != current.token &&
        (latest.expiresAt == 0L || System.currentTimeMillis() <= latest.expiresAt - 30_000L)
      ) {
        return@withLock latest
      }
      try {
        val refreshed = api.refreshSession(latest.refresh)
        val merged = refreshed.copy(
          uid = refreshed.uid.ifBlank { latest.uid },
          email = refreshed.email.ifBlank { latest.email },
          role = latest.role.ifBlank { refreshed.role }
        )
        localStore.saveSession(merged)
        _uiState.value = _uiState.value.copy(session = merged)
        merged
      } catch (e: Exception) {
        Log.w("NearKartVM", "Token refresh failed: ${e.message}")
        if (e is com.example.data.api.SupabaseHttpException || api.isJwtExpiredError(e)) {
          clearDeadSession("Session expired. Please log in again.")
          null
        } else {
          latest
        }
      }
    }
  }

  private fun clearDeadSession(reason: String) {
    localStore.saveSession(null)
    localStore.saveProfile(UserProfile())
    _uiState.value = _uiState.value.copy(
      session = null,
      profile = UserProfile(),
      myShop = null,
      sellerOrders = emptyList(),
      sellerProducts = emptyList(),
      buyerOrders = emptyList(),
      errorMessage = reason
    )
  }

  private suspend fun <T> withValidToken(block: suspend (UserSession) -> T): T {
    val sess = ensureValidSession(forceRefresh = false)
      ?: throw Exception("Please log in to continue.")
    return try {
      block(sess)
    } catch (e: Exception) {
      if (api.isJwtExpiredError(e)) {
        val refreshed = ensureValidSession(forceRefresh = true)
          ?: throw Exception("Session expired. Please log in again.")
        block(refreshed)
      } else {
        throw e
      }
    }
  }

  // --- Real Supabase Data Loading ---
  fun loadInitialData() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true)
      try {
        val remoteShops = api.getPublicShops()
        val remoteProducts = api.getPublicProducts()

        val shopMap = remoteShops.associateBy { it.id }
        val mappedProducts = remoteProducts.map { p ->
          p.copy(shopName = shopMap[p.shopId]?.name)
        }

        _uiState.value = _uiState.value.copy(
          shops = remoteShops,
          products = mappedProducts,
          isLoading = false
        )

        if (_uiState.value.session != null) {
          val validSess = ensureValidSession(forceRefresh = false) ?: return@launch
          var effectiveSess = validSess
          try {
            val remoteProfile = withValidToken { s -> api.fetchProfile(s.uid, s.token) }
            if (remoteProfile != null) {
              val effectiveRole = if (remoteProfile.role.isNotBlank()) remoteProfile.role else validSess.role
              effectiveSess = validSess.copy(role = effectiveRole)
              localStore.saveSession(effectiveSess)
              localStore.saveProfile(remoteProfile)
              _uiState.value = _uiState.value.copy(
                session = effectiveSess,
                profile = remoteProfile
              )
              if (remoteProfile.area.isNotBlank() &&
                _uiState.value.location.area == "Select your location"
              ) {
                val restoredLoc = UserLocation(
                  area = remoteProfile.area,
                  fullAddress = remoteProfile.location
                )
                localStore.saveLocation(restoredLoc)
                _uiState.value = _uiState.value.copy(location = restoredLoc)
              }
            }
          } catch (e: Exception) {
            Log.w("NearKartVM", "fetchProfile: ${e.message}")
          }

          if (effectiveSess.role == "seller") {
            loadSellerData()
          } else {
            loadBuyerOrders()
          }
        }
      } catch (e: Exception) {
        Log.e("NearKartVM", "loadInitialData failed", e)
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          errorMessage = api.cleanErrorMessage(e)
        )
      }
    }
  }

  fun searchProducts(query: String) {
    searchJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.length < 2) {
      _uiState.value = _uiState.value.copy(searchResults = null, isSearching = false)
      return
    }
    searchJob = viewModelScope.launch {
      delay(300L)
      _uiState.value = _uiState.value.copy(isSearching = true)
      val results = api.searchPublicProducts(trimmed)
      val shopMap = _uiState.value.shops.associateBy { it.id }
      val mapped = results.map { p -> p.copy(shopName = shopMap[p.shopId]?.name) }
      _uiState.value = _uiState.value.copy(searchResults = mapped, isSearching = false)
    }
  }

  private fun startPeriodicSync() {
    viewModelScope.launch {
      while (true) {
        delay(12_000L)
        val sess = _uiState.value.session
        if (sess != null) {
          val shop = _uiState.value.myShop
          if (sess.role == "seller" && shop != null) {
            refreshSellerOrdersSilently(shop.id)
          } else if (sess.role == "buyer") {
            refreshBuyerOrdersSilently()
          }
        }
      }
    }
  }

  // --- Real Supabase Auth Flow ---
  fun login(email: String, pass: String, requestedRole: String, onComplete: (Boolean, String?) -> Unit) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
      try {
        val sess = api.login(email.trim(), pass)
        val remoteProfile = try {
          api.fetchProfile(sess.uid, sess.token)
        } catch (_: Exception) {
          null
        }

        val profileRole = remoteProfile?.role?.takeIf { it.isNotBlank() }
        val effectiveRole = profileRole ?: sess.role.ifBlank { requestedRole }
        val roleMismatch = requestedRole == "seller" && effectiveRole == "buyer"

        val fullSession = sess.copy(role = effectiveRole)
        localStore.saveSession(fullSession)

        val effectiveProfile = remoteProfile ?: UserProfile(role = effectiveRole)
        localStore.saveProfile(effectiveProfile)

        val mismatchMsg = if (roleMismatch) {
          "This is a buyer account. Please use a seller account for the Partner Hub."
        } else null

        _uiState.value = _uiState.value.copy(
          session = fullSession,
          profile = effectiveProfile,
          isLoading = false,
          errorMessage = mismatchMsg
        )

        if (effectiveProfile.area.isNotBlank() &&
          _uiState.value.location.area == "Select your location"
        ) {
          val restoredLoc = UserLocation(area = effectiveProfile.area, fullAddress = effectiveProfile.location)
          localStore.saveLocation(restoredLoc)
          _uiState.value = _uiState.value.copy(location = restoredLoc)
        }

        if (effectiveRole == "seller") {
          loadSellerData()
        } else {
          loadBuyerOrders()
        }
        onComplete(true, null)
      } catch (e: Exception) {
        val msg = api.cleanErrorMessage(e)
        Log.e("NearKartVM", "Real login failed: $msg", e)
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
        onComplete(false, msg)
      }
    }
  }

  fun signUp(email: String, pass: String, role: String, onComplete: (Boolean, String?) -> Unit) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
      try {
        val maybeSession = api.signUp(email.trim(), pass, role)
        if (maybeSession != null) {
          localStore.saveSession(maybeSession)
          val profile = UserProfile(role = maybeSession.role)
          localStore.saveProfile(profile)
          _uiState.value = _uiState.value.copy(
            session = maybeSession,
            profile = profile,
            isLoading = false
          )
          if (maybeSession.role == "seller") {
            loadSellerData()
          } else {
            loadBuyerOrders()
          }
          onComplete(true, "Welcome! You are now logged in.")
        } else {
          _uiState.value = _uiState.value.copy(isLoading = false)
          onComplete(true, "Account created. Please log in.")
        }
      } catch (e: Exception) {
        val msg = api.cleanErrorMessage(e)
        Log.e("NearKartVM", "Real signup failed: $msg", e)
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
        onComplete(false, msg)
      }
    }
  }

  fun logout() {
    val token = _uiState.value.session?.token
    if (!token.isNullOrBlank()) {
      viewModelScope.launch { api.logout(token) }
    }
    localStore.saveSession(null)
    localStore.saveProfile(UserProfile())
    localStore.saveCart(null)
    previousNewOrderCount = 0
    _uiState.value = _uiState.value.copy(
      session = null,
      profile = UserProfile(),
      myShop = null,
      sellerOrders = emptyList(),
      sellerProducts = emptyList(),
      buyerOrders = emptyList(),
      cart = null,
      lastPlacedOrder = null,
      newOrderAlert = null
    )
    loadInitialData()
  }

  fun saveProfile(profile: UserProfile, syncRemote: Boolean = true, onComplete: (String?) -> Unit = {}) {
    localStore.saveProfile(profile)
    _uiState.value = _uiState.value.copy(profile = profile)

    if (syncRemote && _uiState.value.session != null) {
      viewModelScope.launch {
        try {
          withValidToken { sess ->
            api.updateMyProfile(
              name = profile.name.trim().ifBlank { null },
              phone = profile.phone.trim().ifBlank { null },
              location = _uiState.value.location.fullAddress.ifBlank { _uiState.value.location.area },
              area = _uiState.value.location.area,
              address = profile.address.trim().ifBlank { null },
              token = sess.token
            )
          }
          onComplete(null)
        } catch (e: Exception) {
          val msg = api.cleanErrorMessage(e)
          onComplete(msg)
        }
      }
    } else {
      onComplete(null)
    }
  }

  // --- Real Cart & Checkout Flow ---
  fun addToCart(shop: Shop, product: Product, qty: Double) {
    val currentCart = _uiState.value.cart
    val newCart = if (currentCart == null || currentCart.shop.id != shop.id) {
      if (qty <= 0.0) null else Cart(shop = shop, items = mapOf(product.id to CartItem(product, qty)))
    } else {
      val mutableItems = currentCart.items.toMutableMap()
      if (qty <= 0.0) {
        mutableItems.remove(product.id)
      } else {
        mutableItems[product.id] = CartItem(product, qty)
      }
      if (mutableItems.isEmpty()) null else currentCart.copy(items = mutableItems)
    }
    localStore.saveCart(newCart)
    _uiState.value = _uiState.value.copy(cart = newCart)
  }

  fun removeFromCart(productId: String) {
    val currentCart = _uiState.value.cart ?: return
    val mutableItems = currentCart.items.toMutableMap()
    mutableItems.remove(productId)
    val updated = if (mutableItems.isEmpty()) null else currentCart.copy(items = mutableItems)
    localStore.saveCart(updated)
    _uiState.value = _uiState.value.copy(cart = updated)
  }

  fun clearCart() {
    localStore.saveCart(null)
    _uiState.value = _uiState.value.copy(cart = null)
  }

  fun fetchShopPayment(shopId: String, onResult: (ShopPaymentInfo?, String?) -> Unit) {
    viewModelScope.launch {
      try {
        val info = api.getShopPayment(shopId)
        val updatedShops = _uiState.value.shops.map { s ->
          if (s.id == shopId) s.copy(upiId = info.upiId, qrUrl = info.qrUrl) else s
        }
        val currentCart = _uiState.value.cart
        val updatedCart = if (currentCart != null && currentCart.shop.id == shopId) {
          currentCart.copy(shop = currentCart.shop.copy(upiId = info.upiId, qrUrl = info.qrUrl))
        } else currentCart
        val updatedMyShop = _uiState.value.myShop?.let { ms ->
          if (ms.id == shopId) ms.copy(upiId = info.upiId, qrUrl = info.qrUrl) else ms
        }
        _uiState.value = _uiState.value.copy(
          shops = updatedShops,
          cart = updatedCart,
          myShop = updatedMyShop
        )
        onResult(info, null)
      } catch (e: Exception) {
        onResult(null, api.cleanErrorMessage(e))
      }
    }
  }

  fun placeOrder(
    deliveryType: String,
    paymentMethod: String,
    utr: String,
    customerName: String,
    customerPhone: String,
    customerAddress: String,
    onComplete: (Boolean, String?) -> Unit
  ) {
    val currentCart = _uiState.value.cart ?: run {
      onComplete(false, "Cart is empty")
      return
    }
    if (_uiState.value.session == null) {
      onComplete(false, "Please log in to place an order")
      return
    }

    val cleanName = customerName.trim()
    val cleanPhone = customerPhone.trim()
    val cleanAddress = customerAddress.trim()
    val cleanUtr = utr.trim()

    if (cleanName.isBlank() || cleanPhone.length != 10) {
      onComplete(false, "Please enter name and 10-digit phone")
      return
    }
    if (deliveryType == "delivery" && cleanAddress.isBlank()) {
      onComplete(false, "Please enter your full delivery address")
      return
    }
    if (paymentMethod == "upi" && !cleanUtr.matches(Regex("^\\d{12}$"))) {
      onComplete(false, "Pay first, then enter the 12-digit UTR number to place the order.")
      return
    }

    val updatedProfile = _uiState.value.profile.copy(
      name = cleanName,
      phone = cleanPhone,
      address = cleanAddress
    )
    localStore.saveProfile(updatedProfile)
    _uiState.value = _uiState.value.copy(profile = updatedProfile)

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
      try {
        // 1. Live stock check right before placing order (matches App.js)
        val stockList = api.checkProductsStock(currentCart.items.keys.toList())
        val stockMap = stockList.associateBy { it.id }
        for (ci in currentCart.items.values) {
          val live = stockMap[ci.product.id]
          if (live == null || !live.active || live.onlineQty + 1e-9 < ci.qty) {
            val msg = "Not enough stock for ${ci.product.name}. Available: ${live?.onlineQty ?: 0.0} ${ci.product.unit}. Please update your cart."
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
            onComplete(false, msg)
            return@launch
          }
        }

        // 2. Place order RPC
        val (orderId, orderNumber) = withValidToken { sess ->
          api.placeOrder(
            shopId = currentCart.shop.id,
            items = currentCart.items.values.map { it.product.id to it.qty },
            name = cleanName,
            phone = cleanPhone,
            address = if (deliveryType == "pickup" && cleanAddress.isBlank()) "Pickup from shop" else cleanAddress,
            deliveryType = deliveryType,
            paymentMethod = paymentMethod,
            token = sess.token
          )
        }

        // 3. Submit UTR without cancelling placed order on failure
        var utrWarning: String? = null
        if (paymentMethod == "upi") {
          try {
            withValidToken { sess ->
              api.submitOrderUtr(orderId, cleanUtr, sess.token)
            }
          } catch (e2: Exception) {
            Log.w("NearKartVM", "submitOrderUtr: ${e2.message}")
            utrWarning = if (api.isDuplicateKeyError(e2)) {
              "This UTR number was already used for another order. Please contact the shop."
            } else {
              "UTR could not be saved (${api.cleanErrorMessage(e2)}). Please tell the shop your UTR number: $cleanUtr"
            }
          }
        }

        // 4. Sync profile silently
        try {
          withValidToken { sess ->
            api.updateMyProfile(
              name = cleanName,
              phone = cleanPhone,
              location = _uiState.value.location.fullAddress.ifBlank { _uiState.value.location.area },
              area = _uiState.value.location.area,
              address = cleanAddress.ifBlank { null },
              token = sess.token
            )
          }
        } catch (_: Exception) {}

        // 5. Reload real orders & public stock
        val freshOrders = try {
          withValidToken { sess -> api.getMyOrders(sess.token) }
        } catch (_: Exception) {
          emptyList()
        }
        val refreshedPublic = try {
          val shopMap = _uiState.value.shops.associateBy { it.id }
          api.getPublicProducts().map { p -> p.copy(shopName = shopMap[p.shopId]?.name) }
        } catch (_: Exception) {
          _uiState.value.products
        }

        val fallbackItems = currentCart.items.values.map {
          OrderItem(
            id = it.product.id,
            orderId = orderId,
            productId = it.product.id,
            name = it.product.name,
            price = it.product.price,
            qty = it.qty,
            unit = it.product.unit
          )
        }
        val matched = freshOrders.find { it.id == orderId }
        val placedOrder = (matched ?: Order(
          id = orderId,
          orderNumber = orderNumber,
          shopId = currentCart.shop.id,
          shopName = currentCart.shop.name,
          shopPhone = currentCart.shop.phone,
          customerName = cleanName,
          customerPhone = cleanPhone,
          customerAddress = if (deliveryType == "pickup" && cleanAddress.isBlank()) "Pickup from shop" else cleanAddress,
          deliveryType = deliveryType,
          paymentMethod = paymentMethod,
          total = currentCart.totalAmount,
          orderItems = fallbackItems,
          utrNumber = if (paymentMethod == "upi") cleanUtr else null,
          paymentStatus = if (paymentMethod == "upi") "pending_verification" else null
        )).copy(utrWarning = utrWarning)

        localStore.saveCart(null)
        _uiState.value = _uiState.value.copy(
          cart = null,
          products = refreshedPublic,
          buyerOrders = freshOrders.ifEmpty { listOf(placedOrder) },
          lastPlacedOrder = placedOrder,
          isLoading = false
        )

        onComplete(true, null)
      } catch (e: Exception) {
        val msg = api.cleanErrorMessage(e)
        Log.e("NearKartVM", "Real placeOrder failed: $msg", e)
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
        onComplete(false, msg)
      }
    }
  }

  // --- Real Buyer Orders ---
  private fun loadBuyerOrders() {
    viewModelScope.launch {
      try {
        val orders = withValidToken { sess -> api.getMyOrders(sess.token) }
        _uiState.value = _uiState.value.copy(buyerOrders = orders)
      } catch (e: Exception) {
        Log.w("NearKartVM", "loadBuyerOrders: ${e.message}")
      }
    }
  }

  fun refreshBuyerOrders(onDone: () -> Unit = {}) {
    if (_uiState.value.session == null) {
      onDone()
      return
    }
    viewModelScope.launch {
      refreshBuyerOrdersSilently()
      onDone()
    }
  }

  private suspend fun refreshBuyerOrdersSilently() {
    try {
      val orders = withValidToken { sess -> api.getMyOrders(sess.token) }
      _uiState.value = _uiState.value.copy(buyerOrders = orders)
    } catch (_: Exception) {}
  }

  fun refreshOrderPayment(orderId: String, isSeller: Boolean) {
    if (_uiState.value.session == null) return
    viewModelScope.launch {
      try {
        val info = withValidToken { sess -> api.getOrderPayment(orderId, sess.token) } ?: return@launch
        if (isSeller) {
          _uiState.value = _uiState.value.copy(
            sellerOrders = _uiState.value.sellerOrders.map { o ->
              if (o.id == orderId) o.copy(
                utrNumber = info.utrNumber ?: o.utrNumber,
                paymentStatus = info.paymentStatus ?: o.paymentStatus
              ) else o
            }
          )
        } else {
          _uiState.value = _uiState.value.copy(
            buyerOrders = _uiState.value.buyerOrders.map { o ->
              if (o.id == orderId) o.copy(
                utrNumber = info.utrNumber ?: o.utrNumber,
                paymentStatus = info.paymentStatus ?: o.paymentStatus
              ) else o
            }
          )
        }
      } catch (_: Exception) {}
    }
  }

  fun cancelOrder(orderId: String, reason: String = "Cancelled by customer") {
    if (_uiState.value.session == null) return
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.setOrderStatus(orderId, "cancelled", reason, sess.token) }
        val freshOrders = withValidToken { sess -> api.getMyOrders(sess.token) }
        _uiState.value = _uiState.value.copy(buyerOrders = freshOrders)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = api.cleanErrorMessage(e))
      }
    }
  }

  fun reorder(order: Order, onComplete: () -> Unit) {
    viewModelScope.launch {
      try {
        val shopProducts = api.getShopProducts(order.shopId)
        val shop = _uiState.value.shops.find { it.id == order.shopId }
          ?: Shop(id = order.shopId, name = order.shopName ?: "Shop", phone = order.shopPhone ?: "")

        val itemsMap = mutableMapOf<String, CartItem>()
        order.orderItems.forEach { item ->
          val prod = shopProducts.find { it.id == item.productId || it.name.equals(item.name, ignoreCase = true) }
          if (prod != null && prod.onlineQty > 0) {
            val qty = minOf(item.qty, prod.onlineQty)
            itemsMap[prod.id] = CartItem(prod, qty)
          }
        }

        if (itemsMap.isNotEmpty()) {
          val newCart = Cart(shop, itemsMap)
          localStore.saveCart(newCart)
          _uiState.value = _uiState.value.copy(cart = newCart)
          onComplete()
        } else {
          _uiState.value = _uiState.value.copy(errorMessage = "Products are currently out of stock.")
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = api.cleanErrorMessage(e))
      }
    }
  }

  // --- Real Seller Hub Flow ---
  fun registerShop(
    name: String,
    owner: String,
    phone: String,
    area: String,
    category: String,
    photoUrl: String?,
    onComplete: (Boolean, String?) -> Unit
  ) {
    if (_uiState.value.session == null) {
      onComplete(false, "Not logged in")
      return
    }

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
      try {
        val newShop = withValidToken { sess ->
          api.registerMyShop(name, owner, phone, area, category, photoUrl, sess.token)
        }
        val allShops = api.getPublicShops()

        _uiState.value = _uiState.value.copy(
          myShop = newShop,
          shops = allShops.ifEmpty { listOf(newShop) },
          isLoading = false
        )
        loadSellerData()
        onComplete(true, null)
      } catch (e: Exception) {
        val msg = api.cleanErrorMessage(e)
        Log.e("NearKartVM", "registerShop error: $msg", e)
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
        onComplete(false, msg)
      }
    }
  }

  fun refreshSellerDashboard() {
    loadSellerData()
  }

  private fun loadSellerData() {
    viewModelScope.launch {
      try {
        val shop = withValidToken { sess -> api.getMyShop(sess.token) }
        var hydratedShop = shop
        if (shop != null) {
          val paymentInfo = try {
            api.getShopPayment(shop.id)
          } catch (_: Exception) {
            null
          }
          if (paymentInfo != null) {
            hydratedShop = shop.copy(
              upiId = paymentInfo.upiId ?: shop.upiId,
              qrUrl = paymentInfo.qrUrl ?: shop.qrUrl
            )
          }
        }
        _uiState.value = _uiState.value.copy(myShop = hydratedShop)

        if (hydratedShop != null) {
          val orders = withValidToken { sess -> api.getShopOrders(hydratedShop.id, sess.token) }
          val prods = withValidToken { sess -> api.getSellerProducts(hydratedShop.id, sess.token) }
          previousNewOrderCount = orders.count { it.status == "new" }
          _uiState.value = _uiState.value.copy(
            sellerOrders = orders,
            sellerProducts = prods
          )
        }
      } catch (e: Exception) {
        Log.w("NearKartVM", "loadSellerData: ${e.message}")
      }
    }
  }

  private suspend fun refreshSellerOrdersSilently(shopId: String) {
    try {
      val orders = withValidToken { sess -> api.getShopOrders(shopId, sess.token) }
      val newCount = orders.count { it.status == "new" }
      if (newCount > previousNewOrderCount && previousNewOrderCount != 0) {
        triggerOrderVibration()
        _uiState.value = _uiState.value.copy(newOrderAlert = "🔔 New order received!")
      }
      previousNewOrderCount = newCount
      _uiState.value = _uiState.value.copy(sellerOrders = orders)
    } catch (_: Exception) {}
  }

  fun updateShopOpenStatus(isOpen: Boolean) {
    val currentShop = _uiState.value.myShop ?: return
    if (_uiState.value.session == null) return
    val updatedShop = currentShop.copy(isOpen = isOpen)
    _uiState.value = _uiState.value.copy(
      myShop = updatedShop,
      shops = _uiState.value.shops.map { if (it.id == updatedShop.id) updatedShop else it }
    )
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.updateMyShop(currentShop.id, isOpen = isOpen, token = sess.token) }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          myShop = currentShop,
          shops = _uiState.value.shops.map { if (it.id == currentShop.id) currentShop else it },
          errorMessage = api.cleanErrorMessage(e)
        )
      }
    }
  }

  fun publishShopOffer(offer: String) {
    val currentShop = _uiState.value.myShop ?: return
    if (_uiState.value.session == null) return
    val updatedShop = currentShop.copy(offer = offer.ifBlank { null })
    _uiState.value = _uiState.value.copy(
      myShop = updatedShop,
      shops = _uiState.value.shops.map { if (it.id == updatedShop.id) updatedShop else it }
    )
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.updateMyShop(currentShop.id, offer = offer, token = sess.token) }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          myShop = currentShop,
          shops = _uiState.value.shops.map { if (it.id == currentShop.id) currentShop else it },
          errorMessage = api.cleanErrorMessage(e)
        )
      }
    }
  }

  fun updateShopPayment(upiId: String?, qrUrl: String?, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
    val currentShop = _uiState.value.myShop ?: run {
      onComplete(false, "Shop not found")
      return
    }
    if (_uiState.value.session == null) {
      onComplete(false, "Not logged in")
      return
    }
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.updateMyPayment(currentShop.id, upiId, qrUrl, sess.token) }
        val updatedShop = currentShop.copy(upiId = upiId, qrUrl = qrUrl)
        _uiState.value = _uiState.value.copy(
          myShop = updatedShop,
          shops = _uiState.value.shops.map { if (it.id == updatedShop.id) updatedShop else it }
        )
        onComplete(true, null)
      } catch (e: Exception) {
        val msg = api.cleanErrorMessage(e)
        _uiState.value = _uiState.value.copy(errorMessage = msg)
        onComplete(false, msg)
      }
    }
  }

  fun updateOrderStatus(orderId: String, status: String, reason: String? = null) {
    if (_uiState.value.session == null) return
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.setOrderStatus(orderId, status, reason, sess.token) }
        _uiState.value.myShop?.let { shop ->
          val freshOrders = withValidToken { sess -> api.getShopOrders(shop.id, sess.token) }
          previousNewOrderCount = freshOrders.count { it.status == "new" }
          _uiState.value = _uiState.value.copy(sellerOrders = freshOrders)
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = api.cleanErrorMessage(e))
      }
    }
  }

  fun verifyOrderPayment(orderId: String, verified: Boolean) {
    if (_uiState.value.session == null) return
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.verifyOrderPayment(orderId, verified, sess.token) }
        _uiState.value.myShop?.let { shop ->
          val freshOrders = withValidToken { sess -> api.getShopOrders(shop.id, sess.token) }
          _uiState.value = _uiState.value.copy(sellerOrders = freshOrders)
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = api.cleanErrorMessage(e))
      }
    }
  }

  fun bumpStock(product: Product, delta: Double = 10.0) {
    val currentShop = _uiState.value.myShop ?: return
    if (_uiState.value.session == null) return
    viewModelScope.launch {
      try {
        val newOnline = product.onlineQty + delta
        withValidToken { sess ->
          api.updateShopProductStock(
            shopId = currentShop.id,
            productId = product.id,
            onlineQty = newOnline,
            shopQty = product.shopQty,
            expectedVersion = product.stockVersion,
            token = sess.token
          )
        }
        val freshProds = withValidToken { sess -> api.getSellerProducts(currentShop.id, sess.token) }
        val shopMap = _uiState.value.shops.associateBy { it.id }
        val allPublic = api.getPublicProducts().map { p -> p.copy(shopName = shopMap[p.shopId]?.name) }
        _uiState.value = _uiState.value.copy(sellerProducts = freshProds, products = allPublic)
      } catch (e: Exception) {
        val msg = if (api.isConflictError(e)) {
          val freshProds = try {
            withValidToken { sess -> api.getSellerProducts(currentShop.id, sess.token) }
          } catch (_: Exception) {
            _uiState.value.sellerProducts
          }
          _uiState.value = _uiState.value.copy(sellerProducts = freshProds)
          "Stock changed recently. List refreshed — please tap +10 again."
        } else {
          api.cleanErrorMessage(e)
        }
        _uiState.value = _uiState.value.copy(errorMessage = msg)
      }
    }
  }

  fun saveSellerProduct(product: Product, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
    val currentShop = _uiState.value.myShop ?: run {
      onComplete(false, "Shop not found")
      return
    }
    if (_uiState.value.session == null) {
      onComplete(false, "Not logged in")
      return
    }
    viewModelScope.launch {
      try {
        val isNew = product.id.startsWith("prod_")
        val prodId = if (isNew) null else product.id
        val prev = if (!isNew) _uiState.value.sellerProducts.find { it.id == product.id } else null
        val qtyChanged = prev != null &&
          (abs(product.onlineQty - prev.onlineQty) > 1e-9 || abs(product.shopQty - prev.shopQty) > 1e-9)

        val returnedVersion = withValidToken { sess ->
          api.saveSellerProduct(currentShop.id, prodId, product, sess.token)
        }

        if (!isNew && qtyChanged) {
          try {
            withValidToken { sess ->
              api.updateShopProductStock(
                shopId = currentShop.id,
                productId = product.id,
                onlineQty = product.onlineQty,
                shopQty = product.shopQty,
                expectedVersion = returnedVersion ?: product.stockVersion,
                token = sess.token
              )
            }
          } catch (stockErr: Exception) {
            val freshProds = try {
              withValidToken { sess -> api.getSellerProducts(currentShop.id, sess.token) }
            } catch (_: Exception) {
              _uiState.value.sellerProducts
            }
            _uiState.value = _uiState.value.copy(sellerProducts = freshProds)
            val errMsg = if (api.isConflictError(stockErr)) {
              "Product saved, but stock version changed elsewhere. Refreshed stock — please re-edit quantity."
            } else {
              "Product saved, but stock update failed: ${api.cleanErrorMessage(stockErr)}"
            }
            _uiState.value = _uiState.value.copy(errorMessage = errMsg)
            onComplete(false, errMsg)
            return@launch
          }
        }

        val freshProds = withValidToken { sess -> api.getSellerProducts(currentShop.id, sess.token) }
        val shopMap = _uiState.value.shops.associateBy { it.id }
        val allPublic = api.getPublicProducts().map { p -> p.copy(shopName = shopMap[p.shopId]?.name) }
        _uiState.value = _uiState.value.copy(
          sellerProducts = freshProds,
          products = allPublic
        )
        onComplete(true, null)
      } catch (e: Exception) {
        val msg = api.cleanErrorMessage(e)
        _uiState.value = _uiState.value.copy(errorMessage = msg)
        onComplete(false, msg)
      }
    }
  }

  fun setProductActive(product: Product, active: Boolean) {
    val currentShop = _uiState.value.myShop ?: return
    if (_uiState.value.session == null) return
    viewModelScope.launch {
      try {
        withValidToken { sess -> api.setProductActive(currentShop.id, product.id, active, sess.token) }
        val freshProds = withValidToken { sess -> api.getSellerProducts(currentShop.id, sess.token) }
        val shopMap = _uiState.value.shops.associateBy { it.id }
        val allPublic = api.getPublicProducts().map { p -> p.copy(shopName = shopMap[p.shopId]?.name) }
        _uiState.value = _uiState.value.copy(sellerProducts = freshProds, products = allPublic)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = api.cleanErrorMessage(e))
      }
    }
  }

  private fun triggerOrderVibration() {
    if (!_uiState.value.vibrationEnabled) return
    try {
      val ctx = getApplication<Application>()
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 120, 300), -1))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(longArrayOf(0, 300, 120, 300), -1)
      }
    } catch (_: Exception) {}
  }
}
