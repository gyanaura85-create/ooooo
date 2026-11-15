package com.example.data.api

import android.net.Uri
import android.util.Log
import com.example.data.localization.Localization
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderPaymentInfo
import com.example.data.model.Product
import com.example.data.model.Shop
import com.example.data.model.ShopPaymentInfo
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseHttpException(
  val statusCode: Int,
  val errorCode: String? = null,
  val rawMessage: String,
  message: String
) : Exception(message)

class SupabaseClient {
  companion object {
    private const val TAG = "SupabaseClient"
    const val BASE_URL = "https://mfdiicegsgbcbvyeuxgi.supabase.co"
    const val API_KEY = "sb_publishable_I_ypFDUGPQdtct0U5FMhAQ_02rIepBx"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun dbToUiStatus(status: String): String = when (status) {
      "out_for_delivery" -> "on_the_way"
      "ready_for_pickup" -> "ready"
      else -> status
    }

    fun uiToDbStatus(status: String): String = when (status) {
      "on_the_way" -> "out_for_delivery"
      "ready" -> "ready_for_pickup"
      else -> status
    }

    fun parsePlaceOrderResponse(res: String): Pair<String, Long?> {
      val trimmed = res.trim()
      if (trimmed.isBlank()) throw Exception("Order could not be placed. Empty response.")

      val parsedObj: JSONObject? = when {
        trimmed.startsWith("[") -> {
          val arr = JSONArray(trimmed)
          if (arr.length() > 0) arr.optJSONObject(0) else null
        }
        trimmed.startsWith("{") -> JSONObject(trimmed)
        else -> null
      }

      if (parsedObj != null) {
        if (parsedObj.has("success") && !parsedObj.optBoolean("success", true)) {
          throw Exception(parsedObj.optString("error", parsedObj.optString("message", "Order could not be placed.")))
        }
        if (parsedObj.has("ok") && !parsedObj.optBoolean("ok", true)) {
          throw Exception(parsedObj.optString("error", parsedObj.optString("message", "Order could not be placed.")))
        }
        val nestedOrder = parsedObj.optJSONObject("order")
        val id = parsedObj.optString("order_id", "")
          .ifBlank { parsedObj.optString("id", "") }
          .ifBlank { nestedOrder?.optString("id", "") ?: "" }
        val orderNum = when {
          parsedObj.has("order_number") && !parsedObj.isNull("order_number") -> parsedObj.optLong("order_number")
          nestedOrder != null && nestedOrder.has("order_number") && !nestedOrder.isNull("order_number") -> nestedOrder.optLong("order_number")
          else -> null
        }
        if (id.isNotBlank()) return id to orderNum
      }

      val plainId = trimmed.trim('"', ' ')
      if (plainId.isNotBlank() && !plainId.startsWith("{") && !plainId.startsWith("[")) {
        return plainId to null
      }
      throw Exception("Order response did not include an order ID.")
    }

    fun parseOrderList(jsonStr: String): List<Order> {
      val list = mutableListOf<Order>()
      val array = try {
        JSONArray(jsonStr)
      } catch (_: Exception) {
        val obj = JSONObject(jsonStr)
        obj.optJSONArray("orders") ?: obj.optJSONArray("data") ?: JSONArray()
      }
      for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        val itemsList = mutableListOf<OrderItem>()
        val itemsArray = o.optJSONArray("order_items") ?: o.optJSONArray("items") ?: JSONArray()
        for (j in 0 until itemsArray.length()) {
          val it = itemsArray.getJSONObject(j)
          itemsList.add(
            OrderItem(
              id = it.optString("id", "$i-$j"),
              orderId = if (it.isNull("order_id")) null else it.optString("order_id"),
              productId = it.optString("product_id", ""),
              name = it.optString("name", "Item $j"),
              price = it.optDouble("price", 0.0),
              qty = it.optDouble("qty", 1.0),
              unit = it.optString("unit", "kg")
            )
          )
        }

        val shopsObj = o.optJSONObject("shops")
        val shopName = shopsObj?.let { if (it.isNull("name")) null else it.optString("name") }
          ?: if (o.isNull("shop_name")) null else o.optString("shop_name")
        val shopPhone = shopsObj?.let { if (it.isNull("phone")) null else it.optString("phone") }
          ?: if (o.isNull("shop_phone")) null else o.optString("shop_phone")

        val rawStatus = o.optString("status", "new")
        list.add(
          Order(
            id = o.optString("id", i.toString()),
            orderNumber = if (o.isNull("order_number")) null else o.optLong("order_number"),
            shopId = o.optString("shop_id", ""),
            userId = if (o.isNull("user_id")) null else o.optString("user_id"),
            status = dbToUiStatus(rawStatus),
            customerName = o.optString("customer_name", o.optString("name", "")),
            customerPhone = o.optString("customer_phone", o.optString("phone", "")),
            customerAddress = o.optString("customer_address", o.optString("address", "")),
            deliveryType = o.optString("delivery_type", "delivery"),
            paymentMethod = o.optString("payment_method", "cod"),
            total = o.optDouble("total", 0.0),
            createdAt = o.optString("created_at", ""),
            updatedAt = o.optString("updated_at", ""),
            cancelReason = if (o.isNull("cancel_reason")) null else o.optString("cancel_reason"),
            orderItems = itemsList,
            shopName = shopName,
            shopPhone = shopPhone,
            utrNumber = if (o.isNull("utr_number")) null else o.optString("utr_number"),
            paymentStatus = if (o.isNull("payment_status")) null else o.optString("payment_status")
          )
        )
      }
      return list
    }
  }

  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

  suspend fun makeRequest(
    path: String,
    method: String = "GET",
    bodyJson: String? = null,
    token: String? = null
  ): String = withContext(Dispatchers.IO) {
    val builder = Request.Builder()
      .url(BASE_URL + path)
      .addHeader("apikey", API_KEY)
      .addHeader("Content-Type", "application/json")

    if (!token.isNullOrBlank()) {
      builder.addHeader("Authorization", "Bearer $token")
    }

    when (method.uppercase()) {
      "GET" -> builder.get()
      "POST" -> {
        val reqBody = (bodyJson ?: "{}").toRequestBody(JSON_MEDIA)
        builder.post(reqBody)
      }
      "PATCH" -> {
        val reqBody = (bodyJson ?: "{}").toRequestBody(JSON_MEDIA)
        builder.patch(reqBody)
      }
      "DELETE" -> builder.delete()
      else -> builder.get()
    }

    client.newCall(builder.build()).execute().use { response ->
      val resString = response.body?.string() ?: ""
      if (!response.isSuccessful) {
        var rawMsg = "HTTP ${response.code}"
        var errCode: String? = null
        try {
          val errObj = JSONObject(resString)
          errCode = if (errObj.isNull("code")) null else errObj.optString("code")
          rawMsg = errObj.optString("message", "").ifBlank {
            errObj.optString("msg", "").ifBlank {
              errObj.optString("error_description", "").ifBlank {
                errObj.optString("error", rawMsg)
              }
            }
          }
        } catch (_: Exception) {}
        val cleanMsg = cleanErrorMessage(rawMsg)
        throw SupabaseHttpException(
          statusCode = response.code,
          errorCode = errCode,
          rawMessage = rawMsg,
          message = cleanMsg
        )
      }
      resString
    }
  }

  fun isJwtExpiredError(e: Throwable): Boolean {
    if (e is SupabaseHttpException && e.statusCode == 401) return true
    val raw = (e as? SupabaseHttpException)?.rawMessage ?: e.message ?: ""
    return raw.contains("jwt", ignoreCase = true) ||
      raw.contains("token is expired", ignoreCase = true) ||
      raw.contains("not authenticated", ignoreCase = true)
  }

  fun isDuplicateKeyError(e: Throwable): Boolean {
    val code = (e as? SupabaseHttpException)?.errorCode
    val raw = (e as? SupabaseHttpException)?.rawMessage ?: e.message ?: ""
    return code == "23505" || raw.contains("23505") || raw.contains("duplicate", ignoreCase = true)
  }

  fun isConflictError(e: Throwable): Boolean {
    if (e is SupabaseHttpException && e.statusCode == 409) return true
    val raw = (e as? SupabaseHttpException)?.rawMessage ?: e.message ?: ""
    return raw.contains("version", ignoreCase = true) || raw.contains("conflict", ignoreCase = true)
  }

  fun cleanErrorMessage(e: Throwable): String {
    return cleanErrorMessage((e as? SupabaseHttpException)?.rawMessage ?: e.message ?: "Something went wrong")
  }

  private fun cleanErrorMessage(m: String): String {
    if (m.contains("invalid login credentials", ignoreCase = true)) {
      return "Wrong email or password."
    }
    if (m.contains("already registered", ignoreCase = true) ||
      m.contains("already exists", ignoreCase = true) ||
      m.contains("user already", ignoreCase = true)
    ) {
      return "This email is already registered. Please log in."
    }
    if (m.contains("email not confirmed", ignoreCase = true)) {
      return "Please confirm your email first, then log in."
    }
    if (m.contains("jwt", ignoreCase = true) ||
      m.contains("not authenticated", ignoreCase = true) ||
      m.contains("not logged in", ignoreCase = true)
    ) {
      return "Session expired. Please log in again."
    }
    if (m.contains("23505") || m.contains("duplicate", ignoreCase = true)) {
      return "This record was already submitted."
    }
    if (m.contains("42501") || m.contains("permission denied", ignoreCase = true)) {
      return "Permission denied on database."
    }
    return m
  }

  // --- Auth API ---
  suspend fun login(email: String, pass: String): UserSession {
    val body = JSONObject().apply {
      put("email", email)
      put("password", pass)
    }
    val res = makeRequest("/auth/v1/token?grant_type=password", "POST", body.toString())
    return parseAuthSession(res, email, "buyer")
  }

  suspend fun signUp(email: String, pass: String, role: String): UserSession? {
    val body = JSONObject().apply {
      put("email", email)
      put("password", pass)
      put("data", JSONObject().put("role", role))
    }
    val res = makeRequest("/auth/v1/signup", "POST", body.toString())
    val obj = JSONObject(res)
    if (obj.has("access_token")) {
      return parseAuthSession(res, email, role)
    }
    return null
  }

  suspend fun refreshSession(refreshToken: String): UserSession {
    val body = JSONObject().put("refresh_token", refreshToken)
    val res = makeRequest("/auth/v1/token?grant_type=refresh_token", "POST", body.toString())
    return parseAuthSession(res, "", "buyer")
  }

  suspend fun logout(token: String) {
    try {
      makeRequest("/auth/v1/logout", "POST", "{}", token)
    } catch (_: Exception) {}
  }

  private fun parseAuthSession(res: String, fallbackEmail: String, fallbackRole: String): UserSession {
    val obj = JSONObject(res)
    val userObj = obj.optJSONObject("user") ?: JSONObject()
    val userMeta = userObj.optJSONObject("user_metadata")
    val role = userMeta?.optString("role", fallbackRole)?.ifBlank { fallbackRole } ?: fallbackRole
    return UserSession(
      token = obj.getString("access_token"),
      refresh = obj.optString("refresh_token", ""),
      uid = userObj.optString("id", obj.optString("id", "")),
      email = userObj.optString("email", fallbackEmail),
      role = role,
      expiresAt = System.currentTimeMillis() + (obj.optLong("expires_in", 3600L) * 1000L)
    )
  }

  // --- User Profile ---
  suspend fun fetchProfile(uid: String, token: String): UserProfile? {
    val cols = listOf("id", "user_id")
    for (col in cols) {
      try {
        val res = makeRequest("/rest/v1/profiles?$col=eq.$uid&select=*", "GET", token = token)
        val array = JSONArray(res)
        if (array.length() > 0) {
          val o = array.getJSONObject(0)
          return UserProfile(
            name = o.optString("name", ""),
            phone = o.optString("phone", ""),
            address = o.optString("address", ""),
            area = o.optString("area", ""),
            location = o.optString("location", ""),
            role = o.optString("role", "buyer")
          )
        }
      } catch (e: Exception) {
        if (isJwtExpiredError(e)) throw e
      }
    }
    return null
  }

  suspend fun updateMyProfile(
    name: String?,
    phone: String?,
    location: String?,
    area: String?,
    address: String?,
    token: String
  ) {
    val body = JSONObject().apply {
      put("p_name", name ?: JSONObject.NULL)
      put("p_phone", phone ?: JSONObject.NULL)
      put("p_location", location ?: JSONObject.NULL)
      put("p_area", area ?: JSONObject.NULL)
      put("p_address", address ?: JSONObject.NULL)
    }
    makeRequest("/rest/v1/rpc/update_my_profile", "POST", body.toString(), token)
  }

  // --- Public Shops ---
  suspend fun getPublicShops(): List<Shop> {
    return try {
      val res = makeRequest("/rest/v1/rpc/get_public_shops", "POST", "{}")
      parseShopList(res)
    } catch (e: Exception) {
      Log.w(TAG, "get_public_shops fallback: ${e.message}")
      try {
        val res = makeRequest("/rest/v1/shops?select=*")
        parseShopList(res)
      } catch (_: Exception) {
        emptyList()
      }
    }
  }

  private fun parseShopList(jsonStr: String): List<Shop> {
    val list = mutableListOf<Shop>()
    val array = try {
      JSONArray(jsonStr)
    } catch (_: Exception) {
      val obj = JSONObject(jsonStr)
      obj.optJSONArray("shops") ?: obj.optJSONArray("data") ?: JSONArray()
    }
    for (i in 0 until array.length()) {
      val o = array.getJSONObject(i)
      list.add(
        Shop(
          id = o.optString("id", i.toString()),
          name = o.optString("name", "Shop $i"),
          ownerName = if (o.isNull("owner_name")) null else o.optString("owner_name"),
          phone = o.optString("phone", ""),
          area = o.optString("area", ""),
          category = o.optString("category", "Grocery"),
          photoUrl = if (o.isNull("photo_url")) null else o.optString("photo_url"),
          isOpen = o.optBoolean("is_open", true),
          rating = o.optDouble("rating", 0.0),
          ratingCount = o.optInt("rating_count", 0),
          offer = if (o.isNull("offer")) null else o.optString("offer"),
          upiId = if (o.isNull("upi_id")) null else o.optString("upi_id"),
          qrUrl = if (o.isNull("qr_url")) null else o.optString("qr_url")
        )
      )
    }
    return list
  }

  // --- Products ---
  suspend fun getPublicProducts(): List<Product> {
    return try {
      val res = makeRequest("/rest/v1/products?active=eq.true&select=*&order=name&limit=60")
      parseProductList(res)
    } catch (e: Exception) {
      Log.w(TAG, "getPublicProducts failed: ${e.message}")
      emptyList()
    }
  }

  suspend fun searchPublicProducts(query: String): List<Product> {
    val words = query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val terms = words.flatMap { Localization.getTermsFor(it) }
      .map { it.replace(",", "").replace("(", "").replace(")", "").trim() }
      .filter { it.isNotBlank() }
      .distinct()
      .take(12)
    if (terms.isEmpty()) return emptyList()

    val orClause = terms.joinToString(",") { term ->
      "name.ilike.*${Uri.encode(term)}*"
    }
    return try {
      val res = makeRequest("/rest/v1/products?active=eq.true&select=*&limit=40&or=($orClause)")
      parseProductList(res)
    } catch (e: Exception) {
      Log.w(TAG, "searchPublicProducts failed: ${e.message}")
      emptyList()
    }
  }

  suspend fun getShopProducts(shopId: String): List<Product> {
    return try {
      val res = makeRequest("/rest/v1/products?shop_id=eq.${Uri.encode(shopId)}&active=eq.true&select=*&order=name")
      parseProductList(res)
    } catch (_: Exception) {
      emptyList()
    }
  }

  suspend fun checkProductsStock(productIds: List<String>): List<Product> {
    if (productIds.isEmpty()) return emptyList()
    val idList = productIds.joinToString(",") { Uri.encode(it) }
    val res = makeRequest("/rest/v1/products?id=in.($idList)&select=id,name,online_qty,active,unit")
    return parseProductList(res)
  }

  private fun parseProductList(jsonStr: String): List<Product> {
    val list = mutableListOf<Product>()
    val array = try {
      JSONArray(jsonStr)
    } catch (_: Exception) {
      val obj = JSONObject(jsonStr)
      obj.optJSONArray("products") ?: obj.optJSONArray("data") ?: JSONArray()
    }
    for (i in 0 until array.length()) {
      val o = array.getJSONObject(i)
      list.add(
        Product(
          id = o.optString("id", i.toString()),
          shopId = o.optString("shop_id", ""),
          name = o.optString("name", "Product $i"),
          category = o.optString("category", "Other"),
          unit = o.optString("unit", "kg"),
          price = o.optDouble("price", 0.0),
          originalPrice = if (o.isNull("original_price")) null else o.optDouble("original_price"),
          onlineQty = o.optDouble("online_qty", 0.0),
          shopQty = o.optDouble("shop_qty", 0.0),
          lowAlert = o.optDouble("low_alert", 5.0),
          description = if (o.isNull("description")) null else o.optString("description"),
          imageUrl = if (o.isNull("image_url")) null else o.optString("image_url"),
          active = o.optBoolean("active", true),
          stockVersion = if (o.isNull("stock_version")) null else o.optInt("stock_version")
        )
      )
    }
    return list
  }

  // --- Orders ---
  suspend fun placeOrder(
    shopId: String,
    items: List<Pair<String, Double>>,
    name: String,
    phone: String,
    address: String,
    deliveryType: String,
    paymentMethod: String,
    token: String
  ): Pair<String, Long?> {
    val itemsArray = JSONArray()
    items.forEach { (pId, qty) ->
      itemsArray.put(JSONObject().apply {
        put("product_id", pId)
        put("qty", qty)
      })
    }
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      put("p_items", itemsArray)
      put("p_customer_name", name)
      put("p_customer_phone", phone)
      put("p_customer_address", address)
      put("p_delivery_type", deliveryType)
      put("p_payment_method", paymentMethod)
    }
    val res = makeRequest("/rest/v1/rpc/place_order", "POST", body.toString(), token)
    return parsePlaceOrderResponse(res)
  }

  suspend fun submitOrderUtr(orderId: String, utr: String, token: String) {
    val body = JSONObject().apply {
      put("p_order", orderId)
      put("p_utr", utr)
    }
    makeRequest("/rest/v1/rpc/submit_order_utr", "POST", body.toString(), token)
  }

  suspend fun getOrderPayment(orderId: String, token: String): OrderPaymentInfo? {
    return try {
      val body = JSONObject().put("p_order", orderId)
      val res = makeRequest("/rest/v1/rpc/get_order_payment", "POST", body.toString(), token)
      val obj = when {
        res.trim().startsWith("[") -> {
          val arr = JSONArray(res)
          if (arr.length() > 0) arr.optJSONObject(0) else null
        }
        res.trim().startsWith("{") -> JSONObject(res)
        else -> null
      } ?: return null
      OrderPaymentInfo(
        utrNumber = if (obj.isNull("utr_number")) null else obj.optString("utr_number").ifBlank { null },
        paymentStatus = if (obj.isNull("payment_status")) null else obj.optString("payment_status").ifBlank { null }
      )
    } catch (_: Exception) {
      null
    }
  }

  suspend fun getMyOrders(token: String, limit: Int = 30): List<Order> {
    val body = JSONObject().apply {
      put("p_limit", limit)
      put("p_offset", 0)
    }
    val res = makeRequest("/rest/v1/rpc/get_my_orders_paginated", "POST", body.toString(), token)
    return parseOrderList(res)
  }

  suspend fun getShopOrders(shopId: String, token: String, limit: Int = 50): List<Order> {
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      put("p_limit", limit)
      put("p_offset", 0)
    }
    val res = makeRequest("/rest/v1/rpc/get_shop_orders_paginated", "POST", body.toString(), token)
    return parseOrderList(res)
  }

  suspend fun setOrderStatus(orderId: String, status: String, reason: String? = null, token: String) {
    val body = JSONObject().apply {
      put("p_order", orderId)
      put("p_status", uiToDbStatus(status))
      if (reason != null) put("p_reason", reason)
    }
    makeRequest("/rest/v1/rpc/set_order_status", "POST", body.toString(), token)
  }

  suspend fun verifyOrderPayment(orderId: String, verified: Boolean, token: String) {
    val body = JSONObject().apply {
      put("p_order", orderId)
      put("p_verified", verified)
    }
    makeRequest("/rest/v1/rpc/verify_order_payment", "POST", body.toString(), token)
  }

  // --- Seller Shop & Products ---
  suspend fun getMyShop(token: String): Shop? {
    return try {
      val res = makeRequest("/rest/v1/rpc/get_my_shop", "POST", "{}", token)
      val trimmed = res.trim()
      if (trimmed.isBlank() || trimmed == "null" || trimmed == "[]") return null
      val parsed = parseShopList(if (trimmed.startsWith("[")) trimmed else "[$trimmed]")
      parsed.firstOrNull()
    } catch (e: Exception) {
      if (isJwtExpiredError(e)) throw e
      null
    }
  }

  suspend fun registerMyShop(
    name: String,
    owner: String,
    phone: String,
    area: String,
    category: String,
    photoUrl: String?,
    token: String
  ): Shop {
    val body = JSONObject().apply {
      put("p_name", name)
      put("p_owner_name", owner)
      put("p_phone", phone)
      put("p_area", area)
      put("p_category", category)
      if (!photoUrl.isNullOrBlank()) put("p_photo_url", photoUrl)
    }
    val res = makeRequest("/rest/v1/rpc/register_my_shop", "POST", body.toString(), token)
    val parsed = parseShopList(if (res.trim().startsWith("[")) res else "[$res]")
    return parsed.first()
  }

  suspend fun updateMyShop(
    shopId: String,
    isOpen: Boolean? = null,
    offer: String? = null,
    token: String
  ) {
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      if (isOpen != null) put("p_is_open", isOpen)
      if (offer != null) {
        put("p_offer", offer)
        put("p_update_offer", true)
      }
    }
    makeRequest("/rest/v1/rpc/update_my_shop", "POST", body.toString(), token)
  }

  suspend fun getShopPayment(shopId: String): ShopPaymentInfo {
    val body = JSONObject().put("p_shop_id", shopId)
    val res = makeRequest("/rest/v1/rpc/get_shop_payment", "POST", body.toString())
    val obj = try {
      val trimmed = res.trim()
      if (trimmed.startsWith("[")) {
        val arr = JSONArray(trimmed)
        if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
      } else {
        JSONObject(trimmed)
      }
    } catch (_: Exception) {
      JSONObject()
    }
    val upi = if (obj.isNull("upi_id")) null else obj.optString("upi_id").ifBlank { null }
    val qr = if (obj.isNull("qr_url")) null else obj.optString("qr_url").ifBlank { null }
    return ShopPaymentInfo(upiId = upi, qrUrl = qr)
  }

  suspend fun updateMyPayment(shopId: String, upiId: String?, qrUrl: String?, token: String) {
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      put("p_upi_id", upiId ?: JSONObject.NULL)
      put("p_qr_url", qrUrl ?: JSONObject.NULL)
    }
    makeRequest("/rest/v1/rpc/update_my_payment", "POST", body.toString(), token)
  }

  suspend fun getSellerProducts(shopId: String, token: String): List<Product> {
    return try {
      val body = JSONObject().put("p_shop_id", shopId)
      val res = makeRequest("/rest/v1/rpc/seller_get_products", "POST", body.toString(), token)
      parseProductList(res)
    } catch (e: Exception) {
      if (isJwtExpiredError(e)) throw e
      emptyList()
    }
  }

  suspend fun saveSellerProduct(
    shopId: String,
    productId: String?,
    product: Product,
    token: String
  ): Int? {
    val prodObj = JSONObject().apply {
      put("name", product.name)
      put("category", product.category)
      put("unit", product.unit)
      put("price", product.price)
      if (product.originalPrice != null) put("original_price", product.originalPrice)
      else put("original_price", JSONObject.NULL)
      put("description", product.description ?: JSONObject.NULL)
      put("image_url", product.imageUrl ?: JSONObject.NULL)
      put("low_alert", product.lowAlert)
      put("active", product.active)
      if (productId == null) {
        put("online_qty", product.onlineQty)
        put("shop_qty", product.shopQty)
      }
    }
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      put("p_product_id", productId ?: JSONObject.NULL)
      put("p_product", prodObj)
    }
    val res = makeRequest("/rest/v1/rpc/seller_save_product", "POST", body.toString(), token)
    return try {
      val trimmed = res.trim()
      val obj = when {
        trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
        trimmed.startsWith("{") -> JSONObject(trimmed)
        else -> null
      }
      if (obj != null && obj.has("stock_version") && !obj.isNull("stock_version")) {
        obj.optInt("stock_version")
      } else null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun setProductActive(shopId: String, productId: String, active: Boolean, token: String) {
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      put("p_product_id", productId)
      put("p_active", active)
    }
    makeRequest("/rest/v1/rpc/seller_set_product_active", "POST", body.toString(), token)
  }

  suspend fun updateShopProductStock(
    shopId: String,
    productId: String,
    onlineQty: Double,
    shopQty: Double,
    expectedVersion: Int?,
    token: String
  ) {
    val body = JSONObject().apply {
      put("p_shop_id", shopId)
      put("p_product_id", productId)
      put("p_online_qty", onlineQty)
      put("p_shop_qty", shopQty)
      if (expectedVersion != null) put("p_expected_version", expectedVersion)
    }
    makeRequest("/rest/v1/rpc/update_shop_product_stock", "POST", body.toString(), token)
  }
}
