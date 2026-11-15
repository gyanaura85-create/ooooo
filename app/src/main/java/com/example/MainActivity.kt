package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.localization.Localization
import com.example.data.model.Shop
import com.example.ui.NearKartViewModel
import com.example.ui.components.LanguageSelectorBottomSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BuyerHomeScreen
import com.example.ui.screens.BuyerOrdersScreen
import com.example.ui.screens.BuyerProfileScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.LocationSelectorBottomSheet
import com.example.ui.screens.OrderSuccessScreen
import com.example.ui.screens.SellerHubScreen
import com.example.ui.screens.SellerRegistrationScreen
import com.example.ui.screens.ShopDetailScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.NearKartBlue
import com.example.ui.theme.NearKartGreenBg
import com.example.ui.theme.NearKartRed
import com.example.ui.theme.NearKartTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      splashScreen.setOnExitAnimationListener { splashScreenView ->
        splashScreenView.remove()
      }
    }
    enableEdgeToEdge()
    setContent {
      NearKartTheme {
        NearKartApp()
      }
    }
  }
}

@Composable
fun NearKartApp(viewModel: NearKartViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  var isSplashFinished by remember { mutableStateOf(false) }
  var currentTab by remember { mutableStateOf("home") } // home, cats, cart, orders, profile
  var selectedShop by remember { mutableStateOf<Shop?>(null) }
  var authRole by remember { mutableStateOf<String?>(null) } // null, "buyer", "seller"
  var isLanguagePickerOpen by remember { mutableStateOf(false) }
  var isLocationSheetOpen by remember { mutableStateOf(false) }
  var categoryToOpen by remember { mutableStateOf<String?>(null) }

  // Surface any ViewModel error messages globally via SnackbarHost
  LaunchedEffect(uiState.errorMessage) {
    val msg = uiState.errorMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearErrorMessage()
    }
  }

  // 1. Splash Screen
  if (!isSplashFinished) {
    SplashScreen(
      onSplashFinished = { isSplashFinished = true },
      modifier = Modifier.fillMaxSize()
    )
    return
  }

  // 2. Auth Modal
  if (authRole != null && uiState.session == null) {
    AuthScreen(
      role = authRole!!,
      lang = uiState.language,
      isLoading = uiState.isLoading,
      onLogin = { email, pass, onResult ->
        viewModel.login(email, pass, authRole!!) { success, err ->
          if (success) authRole = null
          onResult(success, err)
        }
      },
      onSignUp = { email, pass, onResult ->
        viewModel.signUp(email, pass, authRole!!) { success, msg ->
          if (success && viewModel.uiState.value.session != null) {
            authRole = null
          }
          onResult(success, msg)
        }
      },
      onClose = { authRole = null }
    )
    return
  }

  // 3. Seller Hub (if signed in as seller)
  val isSeller = uiState.session?.role == "seller"
  if (isSeller) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (uiState.myShop == null) {
        SellerRegistrationScreen(
          defaultArea = uiState.location.area,
          lang = uiState.language,
          isLoading = uiState.isLoading,
          onRegister = { name, owner, phone, area, category, photoUrl, onResult ->
            viewModel.registerShop(name, owner, phone, area, category, photoUrl, onResult)
          },
          onLogout = { viewModel.logout() }
        )
      } else {
        SellerHubScreen(
          shop = uiState.myShop!!,
          orders = uiState.sellerOrders,
          products = uiState.sellerProducts,
          lang = uiState.language,
          vibrationEnabled = uiState.vibrationEnabled,
          newOrderAlert = uiState.newOrderAlert,
          onClearAlert = { viewModel.clearNewOrderAlert() },
          onRefreshDashboard = { viewModel.refreshSellerDashboard() },
          onFetchMyShopPayment = {
            uiState.myShop?.id?.let { id -> viewModel.fetchShopPayment(id) { _, _ -> } }
          },
          onRefreshOrderPayment = { orderId -> viewModel.refreshOrderPayment(orderId, isSeller = true) },
          onToggleShopOpen = { viewModel.updateShopOpenStatus(it) },
          onPublishOffer = { viewModel.publishShopOffer(it) },
          onUpdatePayment = { upi, qr, onResult -> viewModel.updateShopPayment(upi, qr, onResult) },
          onUpdateOrderStatus = { id, st, reason -> viewModel.updateOrderStatus(id, st, reason) },
          onVerifyOrderPayment = { id, verified -> viewModel.verifyOrderPayment(id, verified) },
          onSaveProduct = { prod, onResult -> viewModel.saveSellerProduct(prod, onResult) },
          onBumpStock = { viewModel.bumpStock(it) },
          onSetProductActive = { prod, active -> viewModel.setProductActive(prod, active) },
          onToggleVibration = { viewModel.toggleVibration(it) },
          onOpenLanguage = { isLanguagePickerOpen = true },
          onLogout = { viewModel.logout() }
        )
      }

      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp)
      )
    }

    if (isLanguagePickerOpen) {
      LanguageSelectorBottomSheet(
        currentLang = uiState.language,
        onDismiss = { isLanguagePickerOpen = false },
        onSelectLanguage = {
          viewModel.setLanguage(it)
          isLanguagePickerOpen = false
        }
      )
    }
    return
  }

  // 4. Buyer CUJ
  BackHandler(enabled = selectedShop != null || uiState.lastPlacedOrder != null || currentTab != "home") {
    if (uiState.lastPlacedOrder != null) {
      viewModel.clearLastPlacedOrder()
      currentTab = "home"
    } else if (selectedShop != null) {
      selectedShop = null
    } else if (currentTab != "home") {
      currentTab = "home"
    }
  }

  // Order Success Screen
  if (uiState.lastPlacedOrder != null) {
    OrderSuccessScreen(
      order = uiState.lastPlacedOrder!!,
      lang = uiState.language,
      onViewOrders = {
        viewModel.clearLastPlacedOrder()
        currentTab = "orders"
      },
      onGoHome = {
        viewModel.clearLastPlacedOrder()
        currentTab = "home"
      }
    )
    return
  }

  // Shop Detail Screen
  val liveSelectedShop = selectedShop?.let { sel ->
    uiState.shops.find { it.id == sel.id } ?: sel
  }
  if (liveSelectedShop != null) {
    ShopDetailScreen(
      shop = liveSelectedShop,
      products = uiState.products,
      cart = uiState.cart,
      lang = uiState.language,
      onBack = { selectedShop = null },
      onAddToCart = { shop, product, qty ->
        viewModel.addToCart(shop, product, qty)
      },
      onOpenCart = {
        selectedShop = null
        currentTab = "cart"
      }
    )
    return
  }

  // Main Buyer Tab Layout
  val cartBadgeCount = uiState.cart?.itemCount ?: 0

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    bottomBar = {
      NavigationBar(
        containerColor = Color.White,
        tonalElevation = 6.dp,
        modifier = Modifier.testTag("buyer_bottom_navigation")
      ) {
        NavigationBarItem(
          selected = currentTab == "home",
          onClick = { currentTab = "home" },
          icon = { Text("🏠", fontSize = 20.sp) },
          label = { Text(Localization.tr("Home", uiState.language), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "cats",
          onClick = { currentTab = "cats"; categoryToOpen = null },
          icon = { Text("🗂️", fontSize = 20.sp) },
          label = { Text(Localization.tr("Categories", uiState.language), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "cart",
          onClick = { currentTab = "cart" },
          icon = {
            Box {
              Text("🛒", fontSize = 20.sp)
              if (cartBadgeCount > 0) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .offset(x = 12.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(NearKartRed)
                ) {
                  Text(text = cartBadgeCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          },
          label = { Text(Localization.tr("Cart", uiState.language), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "orders",
          onClick = { currentTab = "orders" },
          icon = { Text("📦", fontSize = 20.sp) },
          label = { Text(Localization.tr("Orders", uiState.language), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "profile",
          onClick = { currentTab = "profile" },
          icon = { Text("👤", fontSize = 20.sp) },
          label = { Text(Localization.tr("Profile", uiState.language), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
      }
    },
    modifier = Modifier.fillMaxSize()
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (currentTab) {
        "home" -> BuyerHomeScreen(
          shops = uiState.shops,
          products = uiState.products,
          searchResults = uiState.searchResults,
          isSearching = uiState.isSearching,
          buyerOrders = uiState.buyerOrders,
          cart = uiState.cart,
          location = uiState.location,
          lang = uiState.language,
          onOpenShop = { selectedShop = it },
          onOpenCategory = { cat ->
            categoryToOpen = cat
            currentTab = "cats"
          },
          onOpenCart = { currentTab = "cart" },
          onOpenOrders = { currentTab = "orders" },
          onOpenProfile = { currentTab = "profile" },
          onOpenMenu = { categoryToOpen = null; currentTab = "cats" },
          onOpenLocationSheet = { isLocationSheetOpen = true },
          onSearchQueryChange = { viewModel.searchProducts(it) },
          onAddToCart = { shop, product, qty ->
            viewModel.addToCart(shop, product, qty)
          }
        )
        "cats" -> CategoriesScreen(
          shops = uiState.shops,
          initialCategory = categoryToOpen,
          lang = uiState.language,
          onOpenShop = { selectedShop = it },
          onBack = { currentTab = "home" }
        )
        "cart" -> CartScreen(
          cart = uiState.cart,
          profile = uiState.profile,
          isLoggedIn = uiState.session != null,
          lang = uiState.language,
          onBack = { currentTab = "home" },
          onRemoveItem = { viewModel.removeFromCart(it) },
          onNeedLogin = { authRole = "buyer" },
          onFetchShopPayment = { shopId, onResult -> viewModel.fetchShopPayment(shopId, onResult) },
          onPlaceOrder = { deliveryType, paymentMethod, utr, name, phone, address, onResult ->
            viewModel.placeOrder(deliveryType, paymentMethod, utr, name, phone, address, onResult)
          }
        )
        "orders" -> BuyerOrdersScreen(
          orders = uiState.buyerOrders,
          lang = uiState.language,
          onReorder = { order ->
            viewModel.reorder(order) {
              currentTab = "cart"
            }
          },
          onCancelOrder = { viewModel.cancelOrder(it) },
          onRefreshOrders = { viewModel.refreshBuyerOrders() },
          onRefreshOrderPayment = { orderId -> viewModel.refreshOrderPayment(orderId, isSeller = false) },
          onNeedLogin = { authRole = "buyer" },
          isLoggedIn = uiState.session != null
        )
        "profile" -> BuyerProfileScreen(
          session = uiState.session,
          profile = uiState.profile,
          location = uiState.location,
          lang = uiState.language,
          onNeedLogin = { authRole = "buyer" },
          onPartnerOnboarding = { authRole = "seller" },
          onOpenOrders = { currentTab = "orders" },
          onOpenLanguage = { isLanguagePickerOpen = true },
          onOpenLocation = { isLocationSheetOpen = true },
          onSaveProfile = { prof, onComplete -> viewModel.saveProfile(prof, syncRemote = true, onComplete = onComplete) },
          onLogout = { viewModel.logout() }
        )
      }
    }
  }

  if (isLocationSheetOpen) {
    LocationSelectorBottomSheet(
      currentLocation = uiState.location,
      lang = uiState.language,
      onDismiss = { isLocationSheetOpen = false },
      onSaveLocation = { newLoc ->
        viewModel.setLocation(newLoc)
        isLocationSheetOpen = false
      }
    )
  }

  if (isLanguagePickerOpen) {
    LanguageSelectorBottomSheet(
      currentLang = uiState.language,
      onDismiss = { isLanguagePickerOpen = false },
      onSelectLanguage = {
        viewModel.setLanguage(it)
        isLanguagePickerOpen = false
      }
    )
  }
}
