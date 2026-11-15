package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.localization.Localization
import com.example.data.model.Cart
import com.example.data.model.ShopPaymentInfo
import com.example.data.model.UserProfile
import com.example.ui.components.PhotoAvatar
import com.example.ui.components.QrCodeImage
import com.example.ui.theme.NearKartBg
import com.example.ui.theme.NearKartBlue
import com.example.ui.theme.NearKartGreen
import com.example.ui.theme.NearKartInk
import com.example.ui.theme.NearKartLine
import com.example.ui.theme.NearKartMute
import com.example.ui.theme.NearKartRed
import com.example.ui.theme.nearKartInputTextStyle
import com.example.ui.theme.nearKartTextFieldColors

@Composable
fun CartScreen(
  cart: Cart?,
  profile: UserProfile,
  isLoggedIn: Boolean,
  lang: String,
  onBack: () -> Unit,
  onRemoveItem: (String) -> Unit,
  onNeedLogin: () -> Unit,
  onFetchShopPayment: (shopId: String, onResult: (ShopPaymentInfo?, String?) -> Unit) -> Unit,
  onPlaceOrder: (
    deliveryType: String,
    paymentMethod: String,
    utr: String,
    customerName: String,
    customerPhone: String,
    customerAddress: String,
    onResult: (Boolean, String?) -> Unit
  ) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var deliveryType by remember { mutableStateOf("delivery") } // "delivery" | "pickup"
  var paymentMethod by remember { mutableStateOf("cod") }     // "cod" | "upi"
  var utrNumber by remember { mutableStateOf("") }
  var validationError by remember { mutableStateOf<String?>(null) }
  var isPlacingOrder by remember { mutableStateOf(false) }

  var customerName by remember { mutableStateOf(profile.name) }
  var customerPhone by remember { mutableStateOf(profile.phone) }
  var customerAddress by remember { mutableStateOf(profile.address) }

  var shopPaymentInfo by remember(cart?.shop?.id) {
    mutableStateOf(
      cart?.shop?.let { ShopPaymentInfo(upiId = it.upiId, qrUrl = it.qrUrl) }
    )
  }
  var isLoadingPayment by remember { mutableStateOf(false) }
  var paymentFetchError by remember { mutableStateOf<String?>(null) }

  // Keep fields in sync when user logs in or profile loads from Supabase
  LaunchedEffect(profile) {
    if (customerName.isBlank() && profile.name.isNotBlank()) customerName = profile.name
    if (customerPhone.isBlank() && profile.phone.isNotBlank()) customerPhone = profile.phone
    if (customerAddress.isBlank() && profile.address.isNotBlank()) customerAddress = profile.address
  }

  // Fetch authoritative UPI / QR details from Supabase when UPI is selected
  LaunchedEffect(cart?.shop?.id, paymentMethod) {
    val shopId = cart?.shop?.id
    if (shopId != null && paymentMethod == "upi") {
      isLoadingPayment = true
      paymentFetchError = null
      onFetchShopPayment(shopId) { info, err ->
        isLoadingPayment = false
        if (info != null) {
          shopPaymentInfo = info
        } else if (!err.isNullOrBlank()) {
          paymentFetchError = err
        }
      }
    }
  }

  if (cart == null || cart.itemCount == 0) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier
        .fillMaxSize()
        .background(NearKartBg)
        .padding(24.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "🛒", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = Localization.tr("Your cart is empty.", lang),
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = NearKartInk
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = Localization.tr("Open a shop and add products.", lang),
          fontSize = 14.sp,
          color = NearKartMute,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
          onClick = onBack,
          colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.testTag("back_to_home_empty_cart_button")
        ) {
          Text(Localization.tr("Go to Home", lang), fontWeight = FontWeight.Bold)
        }
      }
    }
    return
  }

  val cartList = remember(cart.items) { cart.items.values.toList() }

  Box(modifier = modifier.fillMaxSize().background(NearKartBg)) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("cart_lazy_column")) {
      // Header
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(NearKartBlue)
            .statusBarsPadding()
            .padding(16.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
              Text(
                text = Localization.tr("Cart", lang),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = Color.White
              )
              Text(
                text = cart.shop.name,
                color = Color(0xFFCFDCF7),
                fontSize = 13.sp
              )
            }
          }
        }
      }

      // Items list
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            cartList.forEach { item ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 8.dp)
              ) {
                PhotoAvatar(
                  imageUrl = item.product.imageUrl,
                  fallbackName = item.product.name,
                  size = 46.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NearKartInk
                  )
                  Text(
                    text = "${Localization.formatQty(item.qty, item.product.unit)} × ${Localization.formatMoney(item.product.price)}/${item.product.unit}",
                    fontSize = 12.sp,
                    color = NearKartMute
                  )
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = Localization.formatMoney(item.product.price * item.qty),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NearKartInk
                  )
                  Text(
                    text = Localization.tr("Remove", lang),
                    color = NearKartRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                      .clickable { onRemoveItem(item.product.id) }
                      .padding(top = 4.dp)
                  )
                }
              }
              Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NearKartLine))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = Localization.tr("Total", lang),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = NearKartInk
              )
              Text(
                text = Localization.formatMoney(cart.totalAmount),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = NearKartGreen
              )
            }
            Text(
              text = Localization.tr("Delivery: agree with the shop (free for now)", lang),
              fontSize = 11.sp,
              color = NearKartMute,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }

      // Delivery Type
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = Localization.tr("Delivery type", lang),
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = NearKartInk
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { deliveryType = "delivery" },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (deliveryType == "delivery") NearKartBlue else NearKartBg
                ),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  text = Localization.tr("Home Delivery", lang),
                  color = if (deliveryType == "delivery") Color.White else NearKartInk,
                  fontWeight = FontWeight.Bold
                )
              }

              Button(
                onClick = { deliveryType = "pickup" },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (deliveryType == "pickup") NearKartBlue else NearKartBg
                ),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  text = Localization.tr("Pickup", lang),
                  color = if (deliveryType == "pickup") Color.White else NearKartInk,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }

      // Delivery Details Form
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = Localization.tr("Delivery details", lang),
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = NearKartInk
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = customerName,
              onValueChange = { customerName = it },
              label = { Text(Localization.tr("Your name", lang)) },
              singleLine = true,
              textStyle = nearKartInputTextStyle(),
              shape = RoundedCornerShape(12.dp),
              colors = nearKartTextFieldColors(),
              modifier = Modifier.fillMaxWidth().testTag("buyer_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = customerPhone,
              onValueChange = {
                customerPhone = it.filter { ch -> ch.isDigit() }.take(10)
              },
              label = { Text(Localization.tr("Phone (10 digits)", lang)) },
              singleLine = true,
              textStyle = nearKartInputTextStyle(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              shape = RoundedCornerShape(12.dp),
              colors = nearKartTextFieldColors(),
              modifier = Modifier.fillMaxWidth().testTag("buyer_phone_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = customerAddress,
              onValueChange = { customerAddress = it },
              label = {
                Text(
                  if (deliveryType == "delivery") Localization.tr("Full address", lang)
                  else Localization.tr("Full address (optional for pickup)", lang)
                )
              },
              textStyle = nearKartInputTextStyle(),
              shape = RoundedCornerShape(12.dp),
              minLines = 2,
              colors = nearKartTextFieldColors(),
              modifier = Modifier.fillMaxWidth().testTag("buyer_address_input")
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = Localization.tr("Payment", lang),
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = NearKartInk
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = {
                  paymentMethod = "cod"
                  validationError = null
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (paymentMethod == "cod") NearKartBlue else NearKartBg
                ),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  text = Localization.tr("Cash on Delivery", lang),
                  color = if (paymentMethod == "cod") Color.White else NearKartInk,
                  fontWeight = FontWeight.Bold
                )
              }

              Button(
                onClick = {
                  paymentMethod = "upi"
                  validationError = null
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (paymentMethod == "upi") NearKartBlue else NearKartBg
                ),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  text = Localization.tr("UPI", lang),
                  color = if (paymentMethod == "upi") Color.White else NearKartInk,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // UPI Section
            if (paymentMethod == "upi") {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = Localization.tr("UPI payment is confirmed only after the shop verifies it.", lang),
                fontSize = 12.sp,
                color = NearKartMute
              )
              Spacer(modifier = Modifier.height(10.dp))

              val liveUpi = shopPaymentInfo?.upiId?.trim()?.takeIf { it.isNotBlank() }
              val liveQr = shopPaymentInfo?.qrUrl?.trim()?.takeIf { it.isNotBlank() }

              Card(
                colors = CardDefaults.cardColors(containerColor = NearKartBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                if (isLoadingPayment) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                  ) {
                    CircularProgressIndicator(color = NearKartBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(Localization.tr("Please wait...", lang), fontSize = 12.sp, color = NearKartMute)
                  }
                } else if (liveUpi == null && liveQr == null) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                  ) {
                    Text(
                      text = paymentFetchError ?: "This shop has not added UPI details yet. Please choose Cash on Delivery.",
                      color = NearKartRed,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      textAlign = TextAlign.Center
                    )
                  }
                } else {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                  ) {
                    Text(
                      text = Localization.tr("Pay with UPI", lang),
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 16.sp,
                      color = NearKartInk
                    )
                    Text(
                      text = "Pay ${Localization.formatMoney(cart.totalAmount)} to ${cart.shop.name}",
                      fontSize = 12.sp,
                      color = NearKartMute,
                      modifier = Modifier.padding(top = 2.dp)
                    )

                    val qrToRender = liveQr ?: (liveUpi?.let {
                      "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${
                        Uri.encode("upi://pay?pa=$it&pn=${cart.shop.name}&am=${cart.totalAmount}&cu=INR")
                      }"
                    })
                    Spacer(modifier = Modifier.height(10.dp))
                    QrCodeImage(
                      qrData = qrToRender,
                      contentDescription = "Shop UPI QR Code",
                      modifier = Modifier.size(180.dp)
                    )

                    if (liveUpi != null) {
                      Spacer(modifier = Modifier.height(8.dp))
                      Text(
                        text = "UPI ID: $liveUpi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NearKartInk
                      )

                      Spacer(modifier = Modifier.height(8.dp))
                      Button(
                        onClick = {
                          val upiUri = Uri.parse(
                            "upi://pay?pa=${Uri.encode(liveUpi)}&pn=${Uri.encode(cart.shop.name)}&am=${cart.totalAmount}&cu=INR"
                          )
                          val intent = Intent(Intent.ACTION_VIEW, upiUri)
                          try {
                            context.startActivity(intent)
                          } catch (_: Exception) {
                            Toast.makeText(context, "No UPI app found. Please scan the QR code.", Toast.LENGTH_SHORT).show()
                          }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartBlue),
                        shape = RoundedCornerShape(10.dp)
                      ) {
                        Text(
                          text = Localization.tr("Open UPI app", lang),
                          color = NearKartBlue,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                      text = "After paying, enter the 12-digit UTR / transaction number below:",
                      fontSize = 11.sp,
                      color = NearKartMute,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }

              if (liveUpi != null || liveQr != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                  value = utrNumber,
                  onValueChange = { input ->
                    utrNumber = input.filter { it.isDigit() }.take(12)
                  },
                  label = { Text("${Localization.tr("UTR Number", lang)} (12 digits)") },
                  placeholder = { Text("123456789012") },
                  singleLine = true,
                  textStyle = nearKartInputTextStyle(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  shape = RoundedCornerShape(12.dp),
                  colors = nearKartTextFieldColors(),
                  modifier = Modifier.fillMaxWidth().testTag("utr_number_input")
                )
              }
            }
          }
        }
      }

      // Error Message if any
      validationError?.let { err ->
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE0DB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Text(
              text = err,
              color = NearKartRed,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
      }

      // Submit Button
      item {
        Column(modifier = Modifier.padding(14.dp)) {
          Button(
            onClick = {
              if (isPlacingOrder) return@Button
              if (!isLoggedIn) {
                onNeedLogin()
                return@Button
              }
              if (customerName.isBlank()) {
                validationError = "Please enter your name"
                return@Button
              }
              if (customerPhone.length != 10) {
                validationError = "Please enter a valid 10-digit phone number"
                return@Button
              }
              if (deliveryType == "delivery" && customerAddress.isBlank()) {
                validationError = "Please enter your full delivery address"
                return@Button
              }
              if (paymentMethod == "upi") {
                val hasPayment = shopPaymentInfo?.hasPaymentDetails == true
                if (!hasPayment) {
                  validationError = "This shop has not added UPI details yet. Please choose Cash on Delivery."
                  return@Button
                }
                if (utrNumber.length != 12) {
                  validationError = "Pay first, then enter the 12-digit UTR number to place the order."
                  return@Button
                }
              }

              isPlacingOrder = true
              validationError = null
              onPlaceOrder(
                deliveryType,
                paymentMethod,
                utrNumber,
                customerName,
                customerPhone,
                customerAddress
              ) { success, errMsg ->
                isPlacingOrder = false
                if (!success) {
                  validationError = errMsg ?: "Could not place order. Please try again."
                }
              }
            },
            enabled = !isPlacingOrder,
            colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("place_order_button")
          ) {
            if (isPlacingOrder) {
              CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
              Text(
                text = if (!isLoggedIn) {
                  "${Localization.tr("Login", lang)} & ${Localization.tr("Place order", lang)} • ${Localization.formatMoney(cart.totalAmount)}"
                } else {
                  "${Localization.tr("Place order", lang)} • ${Localization.formatMoney(cart.totalAmount)}"
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(40.dp))
      }
    }
  }
}
