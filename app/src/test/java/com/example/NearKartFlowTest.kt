package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.api.SupabaseClient
import com.example.data.localization.Localization
import com.example.data.model.Cart
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.model.Shop
import com.example.data.model.ShopPaymentInfo
import com.example.data.model.UserProfile
import com.example.ui.screens.CartScreen
import com.example.ui.screens.SellerRegistrationScreen
import com.example.ui.theme.NearKartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NearKartFlowTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  // ---- Unit tests for Supabase mapping, order parsing, and search aliases ----

  @Test
  fun statusMapping_translatesBetweenDbAndUiConsistently() {
    assertEquals("on_the_way", SupabaseClient.dbToUiStatus("out_for_delivery"))
    assertEquals("ready", SupabaseClient.dbToUiStatus("ready_for_pickup"))
    assertEquals("new", SupabaseClient.dbToUiStatus("new"))
    assertEquals("delivered", SupabaseClient.dbToUiStatus("delivered"))

    assertEquals("out_for_delivery", SupabaseClient.uiToDbStatus("on_the_way"))
    assertEquals("ready_for_pickup", SupabaseClient.uiToDbStatus("ready"))
    assertEquals("accepted", SupabaseClient.uiToDbStatus("accepted"))
    assertEquals("cancelled", SupabaseClient.uiToDbStatus("cancelled"))
  }

  @Test
  fun parsePlaceOrderResponse_handlesArrayObjectNestedPlainAndErrors() {
    val arrayRes = """[{"order_id":"ord-101","order_number":42}]"""
    assertEquals("ord-101" to 42L, SupabaseClient.parsePlaceOrderResponse(arrayRes))

    val objRes = """{"id":"ord-202","order_number":99}"""
    assertEquals("ord-202" to 99L, SupabaseClient.parsePlaceOrderResponse(objRes))

    val nestedRes = """{"order":{"id":"ord-303","order_number":7}}"""
    assertEquals("ord-303" to 7L, SupabaseClient.parsePlaceOrderResponse(nestedRes))

    val plainRes = "\"ord-404\""
    assertEquals("ord-404" to null, SupabaseClient.parsePlaceOrderResponse(plainRes))

    assertThrows(Exception::class.java) {
      SupabaseClient.parsePlaceOrderResponse("""{"success":false,"error":"Out of stock"}""")
    }
    assertThrows(Exception::class.java) {
      SupabaseClient.parsePlaceOrderResponse("   ")
    }
  }

  @Test
  fun parseOrderList_mapsStatusesAndNestedShopAndItems() {
    val json = """
      [
        {
          "id": "o1",
          "order_number": 1001,
          "shop_id": "s1",
          "status": "out_for_delivery",
          "customer_name": "Asha",
          "customer_phone": "9876543210",
          "customer_address": "Main Road",
          "delivery_type": "delivery",
          "payment_method": "cod",
          "total": 120.0,
          "shops": {"name": "Fresh Mart", "phone": "9999999999"},
          "order_items": [
            {"id": "oi1", "product_id": "p1", "name": "Rice", "price": 60.0, "qty": 2.0, "unit": "kg"}
          ]
        },
        {
          "id": "o2",
          "shop_id": "s1",
          "status": "ready_for_pickup",
          "total": 50.0
        }
      ]
    """.trimIndent()

    val orders = SupabaseClient.parseOrderList(json)
    assertEquals(2, orders.size)
    assertEquals("on_the_way", orders[0].status)
    assertEquals("Fresh Mart", orders[0].shopName)
    assertEquals("9999999999", orders[0].shopPhone)
    assertEquals(1, orders[0].orderItems.size)
    assertEquals("ready", orders[1].status)
  }

  @Test
  fun localizationMatchText_matchesMultilingualAndTransliteratedAliases() {
    assertTrue(Localization.matchText("Fresh Potato 1kg", "aloo"))
    assertTrue(Localization.matchText("Amul Milk 500ml", "doodh"))
    assertTrue(Localization.matchText("Green Vegetables", "sabzi"))
    assertFalse(Localization.matchText("Basmati Rice", "doodh"))
  }

  // ---- Robolectric Compose UI tests ----

  @Test
  fun sellerRegistration_categorySelectionAndValidationWork() {
    var submittedCategory: String? = null
    var registerCalled = false

    composeTestRule.setContent {
      NearKartTheme {
        SellerRegistrationScreen(
          defaultArea = "Select your location",
          lang = Localization.EN,
          isLoading = false,
          onRegister = { _, _, _, _, cat, _, onResult ->
            registerCalled = true
            submittedCategory = cat
            onResult(true, null)
          },
          onLogout = {}
        )
      }
    }

    composeTestRule.waitForIdle()

    // Submit empty -> validation error, callback not triggered
    composeTestRule.onNodeWithTag("submit_shop_registration_button")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    assertFalse(registerCalled)
    composeTestRule.onNodeWithText("Enter shop name, owner, 10-digit phone and area").assertIsDisplayed()

    // Fill fields and select Pharmacy category
    composeTestRule.onNodeWithTag("seller_shop_name_input").performScrollTo().performTextInput("Care Plus")
    composeTestRule.onNodeWithTag("seller_owner_name_input").performScrollTo().performTextInput("Ramesh")
    composeTestRule.onNodeWithTag("seller_phone_input").performScrollTo().performTextInput("9876543210")
    composeTestRule.onNodeWithTag("seller_area_input").performScrollTo().performTextInput("Station Road")

    composeTestRule.onNodeWithTag("seller_categories_row")
      .performScrollTo()
      .performScrollToNode(hasTestTag("seller_cat_Pharmacy"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("seller_cat_Pharmacy").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("submit_shop_registration_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue(registerCalled)
    assertEquals("Pharmacy", submittedCategory)
  }

  @Test
  fun cartScreen_guestTriggersLogin_andMissingUpiBlocksDemoFallback() {
    val testShop = Shop(id = "s1", name = "Daily Needs", phone = "9876543210", area = "Sector 1")
    val testProduct = Product(
      id = "p1",
      shopId = "s1",
      name = "Toor Dal",
      unit = "kg",
      price = 140.0,
      onlineQty = 5.0
    )
    val testCart = Cart(
      shop = testShop,
      items = mapOf("p1" to CartItem(product = testProduct, qty = 1.0))
    )

    var loginTriggered = false

    composeTestRule.setContent {
      NearKartTheme {
        CartScreen(
          cart = testCart,
          profile = UserProfile(name = "Kiran", phone = "9876543210", address = "Line 2"),
          isLoggedIn = false,
          lang = Localization.EN,
          onBack = {},
          onRemoveItem = {},
          onNeedLogin = { loginTriggered = true },
          onFetchShopPayment = { _, cb -> cb(ShopPaymentInfo(upiId = null, qrUrl = null), null) },
          onPlaceOrder = { _, _, _, _, _, _, cb -> cb(true, null) }
        )
      }
    }

    composeTestRule.waitForIdle()

    // Switch to UPI and verify real missing-UPI guard (no demo "shop@upi")
    composeTestRule.onNodeWithText("UPI").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(
      "This shop has not added UPI details yet. Please choose Cash on Delivery.",
      substring = true
    ).performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("shop@upi", substring = true).fetchSemanticsNodes().size)

    // Guest clicking Place order must prompt login instead of placing order
    composeTestRule.onNodeWithTag("cart_lazy_column")
      .performScrollToNode(hasTestTag("place_order_button"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("place_order_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(loginTriggered)
  }
}
