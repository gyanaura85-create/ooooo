package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserLocation
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import org.json.JSONObject

class LocalStore(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("nearkart_prefs", Context.MODE_PRIVATE)

  var language: String
    get() = prefs.getString("key_lang", "en") ?: "en"
    set(value) = prefs.edit().putString("key_lang", value).apply()

  var vibrationEnabled: Boolean
    get() = prefs.getBoolean("key_vib", true)
    set(value) = prefs.edit().putBoolean("key_vib", value).apply()

  fun saveSession(session: UserSession?) {
    if (session == null) {
      prefs.edit().remove("key_session").apply()
      return
    }
    val json = JSONObject().apply {
      put("token", session.token)
      put("refresh", session.refresh)
      put("uid", session.uid)
      put("email", session.email)
      put("role", session.role)
      put("expiresAt", session.expiresAt)
    }
    prefs.edit().putString("key_session", json.toString()).apply()
  }

  fun getSession(): UserSession? {
    val str = prefs.getString("key_session", null) ?: return null
    return try {
      val json = JSONObject(str)
      UserSession(
        token = json.getString("token"),
        refresh = json.optString("refresh", ""),
        uid = json.getString("uid"),
        email = json.getString("email"),
        role = json.optString("role", "buyer"),
        expiresAt = json.optLong("expiresAt", 0L)
      )
    } catch (_: Exception) {
      null
    }
  }

  fun saveProfile(profile: UserProfile) {
    val json = JSONObject().apply {
      put("name", profile.name)
      put("phone", profile.phone)
      put("address", profile.address)
      put("area", profile.area)
      put("location", profile.location)
      put("role", profile.role)
    }
    prefs.edit().putString("key_profile", json.toString()).apply()
  }

  fun getProfile(): UserProfile {
    val str = prefs.getString("key_profile", null) ?: return UserProfile()
    return try {
      val json = JSONObject(str)
      UserProfile(
        name = json.optString("name", ""),
        phone = json.optString("phone", ""),
        address = json.optString("address", ""),
        area = json.optString("area", ""),
        location = json.optString("location", ""),
        role = json.optString("role", "buyer")
      )
    } catch (_: Exception) {
      UserProfile()
    }
  }

  fun saveLocation(loc: UserLocation) {
    val json = JSONObject().apply {
      put("area", loc.area)
      put("fullAddress", loc.fullAddress)
      loc.lat?.let { put("lat", it) }
      loc.lng?.let { put("lng", it) }
    }
    prefs.edit().putString("key_location", json.toString()).apply()
  }

  fun getLocation(): UserLocation {
    val str = prefs.getString("key_location", null) ?: return UserLocation("Select your location")
    return try {
      val json = JSONObject(str)
      UserLocation(
        area = json.optString("area", "Select your location"),
        fullAddress = json.optString("fullAddress", ""),
        lat = if (json.has("lat")) json.getDouble("lat") else null,
        lng = if (json.has("lng")) json.getDouble("lng") else null
      )
    } catch (_: Exception) {
      UserLocation("Select your location")
    }
  }

  fun saveCart(cart: com.example.data.model.Cart?) {
    if (cart == null || cart.items.isEmpty()) {
      prefs.edit().remove("key_cart").apply()
      return
    }
    try {
      val shopJson = JSONObject().apply {
        put("id", cart.shop.id)
        put("name", cart.shop.name)
        put("phone", cart.shop.phone)
        put("area", cart.shop.area)
        put("category", cart.shop.category)
        put("photoUrl", cart.shop.photoUrl ?: JSONObject.NULL)
        put("isOpen", cart.shop.isOpen)
        put("rating", cart.shop.rating)
        put("ratingCount", cart.shop.ratingCount)
        put("offer", cart.shop.offer ?: JSONObject.NULL)
        put("upiId", cart.shop.upiId ?: JSONObject.NULL)
        put("qrUrl", cart.shop.qrUrl ?: JSONObject.NULL)
      }
      val itemsArr = org.json.JSONArray()
      cart.items.values.forEach { ci ->
        val p = ci.product
        val pJson = JSONObject().apply {
          put("id", p.id)
          put("shopId", p.shopId)
          put("name", p.name)
          put("category", p.category)
          put("unit", p.unit)
          put("price", p.price)
          p.originalPrice?.let { put("originalPrice", it) }
          put("onlineQty", p.onlineQty)
          put("shopQty", p.shopQty)
          put("lowAlert", p.lowAlert)
          put("description", p.description ?: JSONObject.NULL)
          put("imageUrl", p.imageUrl ?: JSONObject.NULL)
          put("active", p.active)
          p.stockVersion?.let { put("stockVersion", it) }
          put("shopName", p.shopName ?: JSONObject.NULL)
        }
        itemsArr.put(JSONObject().apply {
          put("product", pJson)
          put("qty", ci.qty)
        })
      }
      val root = JSONObject().apply {
        put("shop", shopJson)
        put("items", itemsArr)
      }
      prefs.edit().putString("key_cart", root.toString()).apply()
    } catch (_: Exception) {}
  }

  fun getCart(): com.example.data.model.Cart? {
    val str = prefs.getString("key_cart", null) ?: return null
    return try {
      val root = JSONObject(str)
      val s = root.getJSONObject("shop")
      val shop = com.example.data.model.Shop(
        id = s.getString("id"),
        name = s.optString("name", "Shop"),
        phone = s.optString("phone", ""),
        area = s.optString("area", ""),
        category = s.optString("category", "Grocery"),
        photoUrl = if (s.isNull("photoUrl")) null else s.optString("photoUrl"),
        isOpen = s.optBoolean("isOpen", true),
        rating = s.optDouble("rating", 0.0),
        ratingCount = s.optInt("ratingCount", 0),
        offer = if (s.isNull("offer")) null else s.optString("offer"),
        upiId = if (s.isNull("upiId")) null else s.optString("upiId"),
        qrUrl = if (s.isNull("qrUrl")) null else s.optString("qrUrl")
      )
      val arr = root.getJSONArray("items")
      val map = mutableMapOf<String, com.example.data.model.CartItem>()
      for (i in 0 until arr.length()) {
        val entry = arr.getJSONObject(i)
        val pJson = entry.getJSONObject("product")
        val product = com.example.data.model.Product(
          id = pJson.getString("id"),
          shopId = pJson.optString("shopId", shop.id),
          name = pJson.optString("name", "Product"),
          category = pJson.optString("category", "Other"),
          unit = pJson.optString("unit", "kg"),
          price = pJson.optDouble("price", 0.0),
          originalPrice = if (pJson.isNull("originalPrice")) null else pJson.optDouble("originalPrice"),
          onlineQty = pJson.optDouble("onlineQty", 0.0),
          shopQty = pJson.optDouble("shopQty", 0.0),
          lowAlert = pJson.optDouble("lowAlert", 5.0),
          description = if (pJson.isNull("description")) null else pJson.optString("description"),
          imageUrl = if (pJson.isNull("imageUrl")) null else pJson.optString("imageUrl"),
          active = pJson.optBoolean("active", true),
          stockVersion = if (pJson.isNull("stockVersion")) null else pJson.optInt("stockVersion"),
          shopName = if (pJson.isNull("shopName")) null else pJson.optString("shopName")
        )
        val qty = entry.optDouble("qty", 1.0)
        if (qty > 0.0) {
          map[product.id] = com.example.data.model.CartItem(product, qty)
        }
      }
      if (map.isEmpty()) null else com.example.data.model.Cart(shop, map)
    } catch (_: Exception) {
      null
    }
  }
}
