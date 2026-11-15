package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.localization.Localization
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.Shop
import com.example.ui.components.NearKartTag
import com.example.ui.components.PhotoAvatar
import com.example.ui.components.QrCodeImage
import com.example.ui.theme.NearKartBg
import com.example.ui.theme.NearKartBlue
import com.example.ui.theme.NearKartGreen
import com.example.ui.theme.NearKartGreenBg
import com.example.ui.theme.NearKartInk
import com.example.ui.theme.NearKartLine
import com.example.ui.theme.NearKartMute
import com.example.ui.theme.NearKartRed
import com.example.ui.theme.nearKartInputTextStyle
import com.example.ui.theme.nearKartTextFieldColors
import java.io.ByteArrayOutputStream

private val UPI_RE = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")

@Composable
fun SellerHubScreen(
  shop: Shop,
  orders: List<Order>,
  products: List<Product>,
  lang: String,
  vibrationEnabled: Boolean,
  newOrderAlert: String?,
  onClearAlert: () -> Unit,
  onRefreshDashboard: () -> Unit,
  onFetchMyShopPayment: () -> Unit,
  onRefreshOrderPayment: (String) -> Unit,
  onToggleShopOpen: (Boolean) -> Unit,
  onPublishOffer: (String) -> Unit,
  onUpdatePayment: (upiId: String?, qrUrl: String?, onResult: (Boolean, String?) -> Unit) -> Unit,
  onUpdateOrderStatus: (orderId: String, status: String, reason: String?) -> Unit,
  onVerifyOrderPayment: (orderId: String, verified: Boolean) -> Unit,
  onSaveProduct: (Product, onResult: (Boolean, String?) -> Unit) -> Unit,
  onBumpStock: (Product) -> Unit,
  onSetProductActive: (Product, Boolean) -> Unit,
  onToggleVibration: (Boolean) -> Unit,
  onOpenLanguage: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentTab by remember { mutableStateOf("dash") } // dash, orders, products, more
  var selectedOrderId by remember { mutableStateOf<String?>(null) }
  var productToEdit by remember { mutableStateOf<Product?>(null) }
  var isAddProductSheetOpen by remember { mutableStateOf(false) }
  var orderToConfirmReject by remember { mutableStateOf<Order?>(null) }

  val selectedOrder = orders.find { it.id == selectedOrderId }
  val newOrdersCount = orders.count { it.status == "new" }

  BackHandler(enabled = selectedOrder != null || currentTab != "dash") {
    if (selectedOrder != null) {
      selectedOrderId = null
    } else if (currentTab != "dash") {
      currentTab = "dash"
    }
  }

  LaunchedEffect(selectedOrder?.id, selectedOrder?.isUpi) {
    val ord = selectedOrder
    if (ord != null && ord.isUpi) {
      onRefreshOrderPayment(ord.id)
    }
  }

  Scaffold(
    bottomBar = {
      NavigationBar(
        containerColor = Color.White,
        tonalElevation = 6.dp,
        modifier = Modifier.testTag("seller_bottom_navigation")
      ) {
        NavigationBarItem(
          selected = currentTab == "dash",
          onClick = { currentTab = "dash"; selectedOrderId = null },
          icon = { Text("📊", fontSize = 20.sp) },
          label = { Text(Localization.tr("Dashboard", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "orders",
          onClick = { currentTab = "orders"; selectedOrderId = null },
          icon = {
            Box {
              Text("📋", fontSize = 20.sp)
              if (newOrdersCount > 0) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .offset(x = 12.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(NearKartRed)
                ) {
                  Text(text = newOrdersCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          },
          label = { Text(Localization.tr("Orders", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = false,
          onClick = { isAddProductSheetOpen = true },
          icon = { Text("➕", fontSize = 22.sp) },
          label = { Text(Localization.tr("Add", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "products",
          onClick = { currentTab = "products"; selectedOrderId = null },
          icon = { Text("📦", fontSize = 20.sp) },
          label = { Text(Localization.tr("Products", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
        NavigationBarItem(
          selected = currentTab == "more",
          onClick = {
            currentTab = "more"
            selectedOrderId = null
            onFetchMyShopPayment()
          },
          icon = { Text("⋯", fontSize = 20.sp) },
          label = { Text(Localization.tr("More", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
          colors = NavigationBarItemDefaults.colors(selectedIconColor = NearKartBlue, indicatorColor = NearKartGreenBg)
        )
      }
    },
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding).background(NearKartBg)) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Notification banner if new order arrived
        if (!newOrderAlert.isNullOrBlank()) {
          Surface(
            color = NearKartGreen,
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                onClearAlert()
                currentTab = "orders"
                selectedOrderId = null
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
              Text(newOrderAlert, color = Color.White, fontWeight = FontWeight.Bold)
              Text("Tap to view", color = Color.White, fontSize = 12.sp)
            }
          }
        }

        if (selectedOrder != null) {
          SellerOrderDetailView(
            order = selectedOrder,
            lang = lang,
            onBack = { selectedOrderId = null },
            onUpdateStatus = { st, why ->
              onUpdateOrderStatus(selectedOrder.id, st, why)
            },
            onRequestReject = {
              orderToConfirmReject = selectedOrder
            },
            onVerifyPayment = { verified ->
              onVerifyOrderPayment(selectedOrder.id, verified)
            }
          )
        } else {
          when (currentTab) {
            "dash" -> SellerDashboardTab(
              shop = shop,
              orders = orders,
              products = products,
              lang = lang,
              onRefresh = onRefreshDashboard,
              onToggleOpen = onToggleShopOpen,
              onPublishOffer = onPublishOffer,
              onBumpStock = onBumpStock,
              onOpenOrder = { selectedOrderId = it.id },
              onViewAllOrders = { currentTab = "orders" }
            )
            "orders" -> SellerOrdersTab(
              orders = orders,
              lang = lang,
              onRefresh = onRefreshDashboard,
              onSelectOrder = { selectedOrderId = it.id },
              onAcceptOrder = { onUpdateOrderStatus(it.id, "accepted", null) },
              onRejectOrder = { orderToConfirmReject = it }
            )
            "products" -> SellerProductsTab(
              products = products,
              lang = lang,
              onAddProduct = { isAddProductSheetOpen = true },
              onEditProduct = { productToEdit = it },
              onBumpStock = onBumpStock,
              onSetActive = onSetProductActive
            )
            "more" -> SellerMoreTab(
              shop = shop,
              orders = orders,
              lang = lang,
              vibrationEnabled = vibrationEnabled,
              onToggleVibration = onToggleVibration,
              onToggleShopOpen = onToggleShopOpen,
              onOpenInventory = { currentTab = "products" },
              onUpdatePayment = onUpdatePayment,
              onOpenLanguage = onOpenLanguage,
              onLogout = onLogout
            )
          }
        }
      }
    }
  }

  // Reject Order Confirmation Dialog
  orderToConfirmReject?.let { ord ->
    AlertDialog(
      onDismissRequest = { orderToConfirmReject = null },
      title = { Text(Localization.tr("Reject Order", lang), fontWeight = FontWeight.Bold) },
      text = {
        Text("Are you sure you want to reject order #${ord.orderNumber ?: ord.id.takeLast(4).uppercase()} from ${ord.customerName}?")
      },
      confirmButton = {
        TextButton(
          onClick = {
            orderToConfirmReject = null
            onUpdateOrderStatus(ord.id, "cancelled", "Rejected by shop")
            if (selectedOrderId == ord.id) selectedOrderId = null
          }
        ) {
          Text(Localization.tr("Reject", lang), color = NearKartRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { orderToConfirmReject = null }) {
          Text(Localization.tr("Back", lang))
        }
      }
    )
  }

  // Add / Edit Product Sheet
  if (isAddProductSheetOpen || productToEdit != null) {
    ProductFormBottomSheet(
      shop = shop,
      product = productToEdit,
      lang = lang,
      onDismiss = {
        isAddProductSheetOpen = false
        productToEdit = null
      },
      onSave = { savedProduct, onResult ->
        onSaveProduct(savedProduct) { success, errMsg ->
          onResult(success, errMsg)
          if (success) {
            isAddProductSheetOpen = false
            productToEdit = null
          }
        }
      }
    )
  }
}

// ----------------- Dashboard Tab -----------------
@Composable
private fun SellerDashboardTab(
  shop: Shop,
  orders: List<Order>,
  products: List<Product>,
  lang: String,
  onRefresh: () -> Unit,
  onToggleOpen: (Boolean) -> Unit,
  onPublishOffer: (String) -> Unit,
  onBumpStock: (Product) -> Unit,
  onOpenOrder: (Order) -> Unit,
  onViewAllOrders: () -> Unit
) {
  var offerInput by remember(shop.offer) { mutableStateOf(shop.offer ?: "") }
  val newOrders = orders.count { it.status == "new" }
  val totalOrders = orders.size
  val totalSales = orders.filter { it.status != "cancelled" }.sumOf { it.total }
  val lowStockProducts = products.filter { it.active && it.onlineQty <= it.lowAlert }

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    // Header
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(NearKartBlue)
          .statusBarsPadding()
          .padding(16.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            PhotoAvatar(imageUrl = shop.photoUrl, fallbackName = shop.name, size = 52.dp, isRound = true)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(text = "${shop.name} ✔", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
              Text(text = "${Localization.tr(shop.category, lang)} • ${shop.area}", color = Color(0xFFCFDCF7), fontSize = 12.sp)
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
            Surface(
              color = if (shop.isOpen) Color(0xFFDCF5E1) else Color(0xFFFBE0DB),
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.clickable { onToggleOpen(!shop.isOpen) }
            ) {
              Text(
                text = if (shop.isOpen) Localization.tr("● Open", lang) else Localization.tr("● Closed", lang),
                color = if (shop.isOpen) Color(0xFF0A6B3F) else Color(0xFF9A2314),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        }
      }
    }

    // Summary 4-box Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = Localization.tr("Today's Summary", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NearKartInk)
          Spacer(modifier = Modifier.height(14.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItem(icon = "🛍️", value = newOrders.toString(), label = Localization.tr("New Orders", lang))
            StatItem(icon = "📋", value = totalOrders.toString(), label = Localization.tr("Total Orders", lang))
            StatItem(icon = "₹", value = Localization.formatMoney(totalSales), label = Localization.tr("Total Sales", lang))
            StatItem(icon = "⭐", value = if (shop.rating > 0) shop.rating.toString() else "—", label = Localization.tr("Shop Rating", lang))
          }
        }
      }
    }

    // Live Advertisement Box
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = "📣 ${Localization.tr("Live Shop Advertisement", lang)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
            NearKartTag(
              text = if (shop.offer.isNullOrBlank()) Localization.tr("Empty", lang) else Localization.tr("Published", lang),
              type = if (shop.offer.isNullOrBlank()) "yellow" else "green",
              isSmall = true
            )
          }

          if (!shop.offer.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NearKartBlue)
                .padding(12.dp)
            ) {
              Text(shop.offer, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = offerInput,
            onValueChange = { offerInput = it },
            label = { Text("Offer (shown to customers)") },
            placeholder = { Text("Weekend special: 20% OFF") },
            singleLine = true,
            textStyle = nearKartInputTextStyle(),
            shape = RoundedCornerShape(12.dp),
            colors = nearKartTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = { onPublishOffer(offerInput.trim()) },
            colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.End)
          ) {
            Text(Localization.tr("Publish", lang), fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Low stock alert box
    if (lowStockProducts.isNotEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "⚠️ ${Localization.tr("Stock alert", lang)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
            Spacer(modifier = Modifier.height(8.dp))
            lowStockProducts.take(4).forEach { prod ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 6.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NearKartInk)
                  Text(
                    text = if (prod.onlineQty <= 0) Localization.tr("Out of stock", lang) else "Only ${Localization.formatQty(prod.onlineQty, prod.unit)} left",
                    fontSize = 11.sp,
                    color = NearKartRed
                  )
                }
                Button(
                  onClick = { onBumpStock(prod) },
                  colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Text("+10", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
          }
        }
      }
    }

    // Recent Customer Orders
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = Localization.tr("Recent Customer Orders", lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
            Text(
              text = Localization.tr("View All →", lang),
              color = NearKartBlue,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.clickable { onViewAllOrders() }
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          if (orders.isEmpty()) {
            Text(Localization.tr("No orders yet.", lang), color = NearKartMute, fontSize = 13.sp)
          } else {
            orders.take(3).forEach { order ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onOpenOrder(order) }
                  .padding(vertical = 8.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = if (order.orderNumber != null) "#${order.orderNumber}" else "#NK${order.id.takeLast(4).uppercase()}",
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    NearKartTag(text = order.status, isSmall = true)
                  }
                  Text(text = "${order.customerName} • ${order.orderItems.size} items", fontSize = 12.sp, color = NearKartMute)
                }
                Text(text = Localization.formatMoney(order.total), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = NearKartGreen)
              }
            }
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(30.dp)) }
  }
}

@Composable
private fun StatItem(icon: String, value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = icon, fontSize = 22.sp)
    Text(text = value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NearKartInk, modifier = Modifier.padding(top = 2.dp))
    Text(text = label, fontSize = 10.sp, color = NearKartMute, textAlign = TextAlign.Center)
  }
}

// ----------------- Orders Tab -----------------
@Composable
private fun SellerOrdersTab(
  orders: List<Order>,
  lang: String,
  onRefresh: () -> Unit,
  onSelectOrder: (Order) -> Unit,
  onAcceptOrder: (Order) -> Unit,
  onRejectOrder: (Order) -> Unit
) {
  val allStatuses = listOf("new", "accepted", "preparing", "ready", "on_the_way", "delivered", "cancelled")
  var selectedFilter by remember { mutableStateOf("new") }

  val filtered = orders.filter { it.status == selectedFilter }

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(NearKartBlue)
          .statusBarsPadding()
          .padding(16.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            Text(text = Localization.tr("Seller Orders", lang), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(text = "Real-time incoming orders", color = Color(0xFFCFDCF7), fontSize = 12.sp)
          }
          IconButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh orders", tint = Color.White)
          }
        }
      }
    }

    item {
      LazyRow(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(allStatuses) { st ->
          val count = orders.count { it.status == st }
          val isSelected = selectedFilter == st
          Surface(
            color = if (isSelected) NearKartBlue else Color.White,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NearKartBlue else NearKartLine),
            modifier = Modifier.clickable { selectedFilter = st }
          ) {
            Text(
              text = "${Localization.tr(st.replace("_", " ").replaceFirstChar { it.uppercase() }, lang)} ($count)",
              color = if (isSelected) Color.White else NearKartInk,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
      }
    }

    if (filtered.isEmpty()) {
      item {
        Text(
          text = Localization.tr("No orders in this list.", lang),
          color = NearKartMute,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(40.dp)
        )
      }
    } else {
      items(filtered, key = { it.id }) { order ->
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable { onSelectOrder(order) }
            .testTag("seller_order_card_${order.id}")
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = if (order.orderNumber != null) "#${order.orderNumber}" else "#NK${order.id.takeLast(4).uppercase()}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = NearKartInk
              )
              if (order.status == "new") {
                NearKartTag(text = "⏱ 10 min left", type = "yellow")
              } else {
                NearKartTag(text = order.status, type = if (order.status == "delivered") "green" else "blue")
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = order.customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NearKartInk)
            Text(text = if (order.isPickup) "🏪 Pickup" else "📍 " + order.customerAddress, fontSize = 12.sp, color = NearKartMute)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "${order.orderItems.size} items • ${if (order.isUpi) "UPI" else "COD"}",
                fontSize = 12.sp,
                color = NearKartMute
              )
              Text(text = Localization.formatMoney(order.total), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NearKartGreen)
            }

            if (order.isUpi) {
              Spacer(modifier = Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "UTR: ${order.utrNumber ?: "Pending"}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = NearKartInk
                )
                if (order.paymentStatus == "verified") {
                  Spacer(modifier = Modifier.width(6.dp))
                  NearKartTag(text = "✔ Verified", type = "green", isSmall = true)
                }
              }
            }

            if (order.status == "new") {
              Spacer(modifier = Modifier.height(12.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                  onClick = { onAcceptOrder(order) },
                  colors = ButtonDefaults.buttonColors(containerColor = NearKartGreen),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1.3f)
                ) {
                  Text(Localization.tr("Accept", lang), fontWeight = FontWeight.Bold)
                }
                Button(
                  onClick = { onRejectOrder(order) },
                  colors = ButtonDefaults.buttonColors(containerColor = NearKartRed),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Text(Localization.tr("Reject", lang), fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(30.dp)) }
  }
}

// ----------------- Order Details in Seller Hub -----------------
@Composable
private fun SellerOrderDetailView(
  order: Order,
  lang: String,
  onBack: () -> Unit,
  onUpdateStatus: (String, String?) -> Unit,
  onRequestReject: () -> Unit,
  onVerifyPayment: (Boolean) -> Unit
) {
  val context = LocalContext.current
  var showInvoiceDialog by remember { mutableStateOf(false) }

  val flow = if (order.isPickup) listOf("new", "accepted", "preparing", "ready", "delivered")
  else listOf("new", "accepted", "preparing", "on_the_way", "delivered")

  val currentIdx = flow.indexOf(order.status).let { if (it == -1) 0 else it }
  val nextStep = if (currentIdx < flow.size - 1) flow[currentIdx + 1] else null

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(NearKartBlue)
          .statusBarsPadding()
          .padding(16.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
              Text(
                text = Localization.tr("Order Details", lang),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color.White
              )
              Text(
                text = if (order.orderNumber != null) "#${order.orderNumber}" else order.id,
                color = Color(0xFFCFDCF7),
                fontSize = 12.sp
              )
            }
          }

          NearKartTag(text = order.status, type = if (order.status == "delivered") "green" else "blue")
        }
      }
    }

    // Customer Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(14.dp)
        ) {
          PhotoAvatar(imageUrl = null, fallbackName = order.customerName, size = 44.dp, isRound = true)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(order.customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
            Text(order.customerPhone, fontSize = 12.sp, color = NearKartMute)
          }
          if (order.customerPhone.isNotBlank()) {
            OutlinedButton(
              onClick = {
                try {
                  val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))
                  context.startActivity(intent)
                } catch (_: Exception) {
                  Toast.makeText(context, "Phone dialer unavailable", Toast.LENGTH_SHORT).show()
                }
              },
              shape = RoundedCornerShape(10.dp)
            ) {
              Text(Localization.tr("Call", lang), fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Delivery address
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(Localization.tr("Delivery Information", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NearKartInk)
          Spacer(modifier = Modifier.height(4.dp))
          Text(text = if (order.isPickup) "🏪 Pickup from shop" else "🏠 ${order.customerAddress}", fontSize = 13.sp, color = NearKartInk)
          if (!order.isPickup && order.customerAddress.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = Localization.tr("View on Map", lang),
              color = NearKartBlue,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              modifier = Modifier.clickable {
                try {
                  val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(order.customerAddress)}"))
                  context.startActivity(mapIntent)
                } catch (_: Exception) {
                  Toast.makeText(context, "Map app unavailable", Toast.LENGTH_SHORT).show()
                }
              }
            )
          }
        }
      }
    }

    // UPI Payment Verification Card (if UPI)
    if (order.isUpi) {
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
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("💳 UPI Payment", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
              NearKartTag(
                text = if (order.paymentStatus == "verified") "✔ Verified" else if (order.paymentStatus == "rejected") "Rejected" else "Pending Verification",
                type = if (order.paymentStatus == "verified") "green" else if (order.paymentStatus == "rejected") "red" else "yellow"
              )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("UTR Number", fontSize = 12.sp, color = NearKartMute)
              Text(order.utrNumber ?: "Not provided", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = NearKartInk)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Amount to receive", fontSize = 12.sp, color = NearKartMute)
              Text(Localization.formatMoney(order.total), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NearKartGreen)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Verify this 12-digit UTR in your bank app or UPI transaction history:", fontSize = 11.sp, color = NearKartMute)

            if (order.status != "cancelled") {
              Spacer(modifier = Modifier.height(10.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                  onClick = { onVerifyPayment(true) },
                  colors = ButtonDefaults.buttonColors(containerColor = NearKartGreen),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1.3f)
                ) {
                  Text("Payment verified", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                  onClick = { onVerifyPayment(false) },
                  colors = ButtonDefaults.buttonColors(containerColor = NearKartRed),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Text("Not received", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
              }
            }
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
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Order Items (${order.orderItems.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NearKartInk)
          Spacer(modifier = Modifier.height(8.dp))
          order.orderItems.forEach { item ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
            ) {
              Text("${item.name} • ${Localization.formatQty(item.qty, item.unit)}", fontSize = 13.sp, color = NearKartInk)
              Text(Localization.formatMoney(item.price * item.qty), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NearKartInk)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NearKartLine))
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Total Amount", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = NearKartInk)
            Text(Localization.formatMoney(order.total), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = NearKartGreen)
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "📄 View Bill / Invoice",
            color = NearKartBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clickable { showInvoiceDialog = true }
          )
        }
      }
    }

    // Status Updater
    if (order.status != "cancelled") {
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
            Text(Localization.tr("Update Order Status", lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
            Spacer(modifier = Modifier.height(10.dp))

            val buttons = listOf(
              "accepted" to Localization.tr("Accept Order", lang),
              "preparing" to Localization.tr("Start Preparing", lang),
              "ready" to Localization.tr("Mark as Ready", lang),
              "on_the_way" to Localization.tr("Out for Delivery", lang),
              "delivered" to Localization.tr("Mark as Delivered", lang)
            ).filter { flow.contains(it.first) }

            buttons.forEach { (stepKey, label) ->
              val isStepEnabled = order.status != "delivered" && stepKey == nextStep
              Button(
                onClick = { onUpdateStatus(stepKey, null) },
                enabled = isStepEnabled,
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (stepKey == "delivered") NearKartGreen else NearKartBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
              ) {
                Text(label, fontWeight = FontWeight.Bold)
              }
            }

            if (order.status == "new") {
              OutlinedButton(
                onClick = onRequestReject,
                border = androidx.compose.foundation.BorderStroke(1.dp, NearKartRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
              ) {
                Text(Localization.tr("Reject Order", lang), color = NearKartRed, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(40.dp)) }
  }

  if (showInvoiceDialog) {
    AlertDialog(
      onDismissRequest = { showInvoiceDialog = false },
      title = { Text("Invoice for #${order.orderNumber ?: order.id}", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          order.orderItems.forEach {
            Text("${it.name} - ${Localization.formatQty(it.qty, it.unit)} = ${Localization.formatMoney(it.price * it.qty)}")
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text("Total: ${Localization.formatMoney(order.total)} (${if (order.isUpi) "UPI" else "COD"})", fontWeight = FontWeight.Bold)
        }
      },
      confirmButton = {
        TextButton(onClick = { showInvoiceDialog = false }) { Text("OK") }
      }
    )
  }
}

// ----------------- Products Tab -----------------
@Composable
private fun SellerProductsTab(
  products: List<Product>,
  lang: String,
  onAddProduct: () -> Unit,
  onEditProduct: (Product) -> Unit,
  onBumpStock: (Product) -> Unit,
  onSetActive: (Product, Boolean) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var filterStatus by remember { mutableStateOf("all") } // all, in, low, out, inactive
  var productToDeactivate by remember { mutableStateOf<Product?>(null) }

  val filtered = products.filter { prod ->
    val matchesSearch = searchQuery.isBlank() || Localization.matchText("${prod.name} ${prod.category}", searchQuery)
    val matchesStatus = when (filterStatus) {
      "in" -> prod.active && prod.onlineQty > prod.lowAlert
      "low" -> prod.active && prod.onlineQty > 0 && prod.onlineQty <= prod.lowAlert
      "out" -> prod.active && prod.onlineQty <= 0
      "inactive" -> !prod.active
      else -> true
    }
    matchesSearch && matchesStatus
  }

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(NearKartBlue)
          .statusBarsPadding()
          .padding(16.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            Text(text = Localization.tr("All Products", lang), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(text = "${products.size} products in total", color = Color(0xFFCFDCF7), fontSize = 12.sp)
          }

          Button(
            onClick = onAddProduct,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
          ) {
            Text(text = Localization.tr("+ Add Product", lang), color = NearKartBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
      }
    }

    // Search & Filter
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search products...") },
            singleLine = true,
            textStyle = nearKartInputTextStyle(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NearKartMute) },
            shape = RoundedCornerShape(12.dp),
            colors = nearKartTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(10.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips = listOf(
              "all" to Localization.tr("All", lang),
              "in" to Localization.tr("In stock", lang),
              "low" to "Low stock",
              "out" to Localization.tr("Out of stock", lang),
              "inactive" to "Inactive"
            )
            items(chips) { (key, label) ->
              val isSelected = filterStatus == key
              Surface(
                color = if (isSelected) NearKartBlue else NearKartBg,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NearKartBlue else NearKartLine),
                modifier = Modifier.clickable { filterStatus = key }
              ) {
                Text(
                  text = label,
                  color = if (isSelected) Color.White else NearKartInk,
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }
    }

    // Table Header
    item {
      Surface(
        color = Color(0xFFEEF3FF),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
      ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
          Text("Product", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NearKartMute, modifier = Modifier.weight(2f))
          Text("Stock", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NearKartMute, modifier = Modifier.weight(1.2f))
          Text("Price", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NearKartMute, modifier = Modifier.weight(1f))
          Text("Action", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NearKartMute, modifier = Modifier.weight(1.2f))
        }
      }
    }

    if (filtered.isEmpty()) {
      item {
        Text(Localization.tr("No products found.", lang), color = NearKartMute, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
      }
    } else {
      items(filtered, key = { it.id }) { product ->
        Card(
          colors = CardDefaults.cardColors(containerColor = Color.White),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(2f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NearKartInk, maxLines = 1)
                Text(
                  text = "${product.category}${if (!product.active) " • Inactive" else ""}",
                  fontSize = 10.sp,
                  color = if (product.active) NearKartMute else NearKartRed
                )
              }

              Column(modifier = Modifier.weight(1.2f)) {
                Text(Localization.formatQty(product.onlineQty, product.unit), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NearKartInk)
                Text("Shop: ${Localization.formatQty(product.shopQty, product.unit)}", fontSize = 10.sp, color = NearKartMute)
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(Localization.formatMoney(product.price), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NearKartGreen)
                Text("/${product.unit}", fontSize = 10.sp, color = NearKartMute)
              }

              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.2f)) {
                IconButton(onClick = { onEditProduct(product) }, modifier = Modifier.size(30.dp)) {
                  Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NearKartBlue, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                  onClick = { onBumpStock(product) },
                  colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  modifier = Modifier.height(28.dp)
                ) {
                  Text("+10", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            Row(
              horizontalArrangement = Arrangement.End,
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
              Text(
                text = if (product.active) "Hide from shop" else "Show in shop",
                color = if (product.active) NearKartMute else NearKartGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                  .clickable {
                    if (product.active) {
                      productToDeactivate = product
                    } else {
                      onSetActive(product, true)
                    }
                  }
                  .padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(30.dp)) }
  }

  productToDeactivate?.let { prod ->
    AlertDialog(
      onDismissRequest = { productToDeactivate = null },
      title = { Text("Hide product?", fontWeight = FontWeight.Bold) },
      text = { Text("Hide \"${prod.name}\" from customers? You can activate it again anytime.") },
      confirmButton = {
        TextButton(
          onClick = {
            productToDeactivate = null
            onSetActive(prod, false)
          }
        ) {
          Text("Hide", color = NearKartRed, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { productToDeactivate = null }) {
          Text(Localization.tr("Back", lang))
        }
      }
    )
  }
}

// ----------------- More Tab (Shop Profile & Payment) -----------------
@Composable
private fun SellerMoreTab(
  shop: Shop,
  orders: List<Order>,
  lang: String,
  vibrationEnabled: Boolean,
  onToggleVibration: (Boolean) -> Unit,
  onToggleShopOpen: (Boolean) -> Unit,
  onOpenInventory: () -> Unit,
  onUpdatePayment: (upiId: String?, qrUrl: String?, onResult: (Boolean, String?) -> Unit) -> Unit,
  onOpenLanguage: () -> Unit,
  onLogout: () -> Unit
) {
  val context = LocalContext.current
  var isPaymentSheetOpen by remember { mutableStateOf(false) }
  var showHoursDialog by remember { mutableStateOf(false) }
  var showDeliveryDialog by remember { mutableStateOf(false) }
  var showSalesReportDialog by remember { mutableStateOf(false) }

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .background(NearKartBlue)
          .statusBarsPadding()
          .padding(top = 16.dp, bottom = 44.dp)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          PhotoAvatar(imageUrl = shop.photoUrl, fallbackName = shop.name, size = 80.dp, isRound = true)
          Spacer(modifier = Modifier.height(10.dp))
          Text(text = "${shop.name} ✔", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
          Text(text = "${shop.category} • ${shop.area}", color = Color(0xFFCFDCF7), fontSize = 13.sp)
          Spacer(modifier = Modifier.height(8.dp))
          NearKartTag(
            text = if (shop.isOpen) Localization.tr("● Open", lang) else Localization.tr("● Closed", lang),
            type = if (shop.isOpen) "green" else "red"
          )
        }
      }
    }

    // Payment Account Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          val hasPayment = !shop.upiId.isNullOrBlank() || !shop.qrUrl.isNullOrBlank()
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = Localization.tr("Payment Account", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NearKartInk)
            NearKartTag(
              text = if (!hasPayment) "Empty" else "Configured",
              type = if (!hasPayment) "yellow" else "green"
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text("UPI ID: ${shop.upiId ?: "Not set"}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NearKartInk)
          Text("Customers pay you directly via QR or UPI and send you the 12-digit UTR.", fontSize = 11.sp, color = NearKartMute, modifier = Modifier.padding(top = 2.dp))

          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { isPaymentSheetOpen = true },
            colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(if (!hasPayment) "Add UPI ID & QR Code" else "Edit Payment Account", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Management Rows (every row performs a real action)
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = Localization.tr("Shop Management", lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
          Spacer(modifier = Modifier.height(8.dp))
          MenuRowItem(
            icon = "🕒",
            title = "Shop Status (Open / Closed)",
            subtitle = if (shop.isOpen) "Shop is currently OPEN for orders" else "Shop is currently CLOSED",
            onClick = { showHoursDialog = true }
          )
          MenuRowItem(
            icon = "🚚",
            title = "Delivery and Pickup",
            subtitle = "Home delivery & self-pickup info",
            onClick = { showDeliveryDialog = true }
          )
          MenuRowItem(
            icon = "📦",
            title = "Inventory",
            subtitle = "View & update product stock",
            onClick = onOpenInventory
          )
          MenuRowItem(
            icon = "📈",
            title = "Sales Report",
            subtitle = "${orders.size} total orders • ${Localization.formatMoney(orders.filter { it.status != "cancelled" }.sumOf { it.total })}",
            onClick = { showSalesReportDialog = true }
          )
        }
      }
    }

    // Settings & Logout
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NearKartLine),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = Localization.tr("Support & Account", lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NearKartInk)
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenLanguage() }
              .padding(vertical = 8.dp)
          ) {
            Column {
              Text("🌐 ${Localization.tr("Language", lang)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text("Change app language", fontSize = 11.sp, color = NearKartMute)
            }
            Text("›", fontSize = 20.sp, color = NearKartMute)
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp)
          ) {
            Column {
              Text("🔔 Notification Vibration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text("Vibrate on new customer order", fontSize = 11.sp, color = NearKartMute)
            }
            Switch(
              checked = vibrationEnabled,
              onCheckedChange = onToggleVibration,
              colors = SwitchDefaults.colors(checkedThumbColor = NearKartBlue)
            )
          }

          MenuRowItem(
            icon = "🎧",
            title = Localization.tr("Customer Support", lang),
            subtitle = "Contact partner support",
            onClick = {
              try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@nearkart.local")).apply {
                  putExtra(Intent.EXTRA_SUBJECT, "NearKart Partner Support (${shop.name})")
                }
                context.startActivity(intent)
              } catch (_: Exception) {
                Toast.makeText(context, "Support: support@nearkart.local", Toast.LENGTH_LONG).show()
              }
            }
          )

          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = NearKartRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(Localization.tr("Log Out", lang), fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(40.dp)) }
  }

  if (showHoursDialog) {
    AlertDialog(
      onDismissRequest = { showHoursDialog = false },
      title = { Text("Shop Availability", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Current status: ${if (shop.isOpen) "Open (accepting orders)" else "Closed (orders paused)"}")
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Accept customer orders", fontWeight = FontWeight.SemiBold)
            Switch(checked = shop.isOpen, onCheckedChange = onToggleShopOpen)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showHoursDialog = false }) { Text("Done") }
      }
    )
  }

  if (showDeliveryDialog) {
    AlertDialog(
      onDismissRequest = { showDeliveryDialog = false },
      title = { Text("Delivery and Pickup", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("• Customers can choose either Home Delivery or Shop Pickup at checkout.")
          Text("• Delivery charges are currently free and agreed directly with the customer.")
          Text("• Shop address/area: ${shop.area}")
        }
      },
      confirmButton = {
        TextButton(onClick = { showDeliveryDialog = false }) { Text(Localization.tr("Close", lang)) }
      }
    )
  }

  if (showSalesReportDialog) {
    val activeSales = orders.filter { it.status != "cancelled" }
    val deliveredSales = orders.filter { it.status == "delivered" }
    val upiTotal = activeSales.filter { it.isUpi }.sumOf { it.total }
    val codTotal = activeSales.filter { !it.isUpi }.sumOf { it.total }
    AlertDialog(
      onDismissRequest = { showSalesReportDialog = false },
      title = { Text("Sales Report", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Total Orders: ${orders.size}")
          Text("Delivered Orders: ${deliveredSales.size}")
          Text("Cancelled Orders: ${orders.count { it.status == "cancelled" }}")
          Spacer(modifier = Modifier.height(4.dp))
          Text("Total Sales: ${Localization.formatMoney(activeSales.sumOf { it.total })}", fontWeight = FontWeight.Bold, color = NearKartGreen)
          Text("• UPI Sales: ${Localization.formatMoney(upiTotal)}")
          Text("• COD Sales: ${Localization.formatMoney(codTotal)}")
        }
      },
      confirmButton = {
        TextButton(onClick = { showSalesReportDialog = false }) { Text(Localization.tr("Close", lang)) }
      }
    )
  }

  if (isPaymentSheetOpen) {
    PaymentAccountBottomSheet(
      shop = shop,
      onDismiss = { isPaymentSheetOpen = false },
      onSave = { upi, qr, onResult ->
        onUpdatePayment(upi, qr) { success, errMsg ->
          onResult(success, errMsg)
          if (success) isPaymentSheetOpen = false
        }
      }
    )
  }
}

@Composable
private fun MenuRowItem(
  icon: String,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(vertical = 8.dp)
  ) {
    Text(icon, fontSize = 20.sp)
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NearKartInk)
      Text(subtitle, fontSize = 11.sp, color = NearKartMute)
    }
    Text("›", fontSize = 20.sp, color = NearKartMute)
  }
}

// ----------------- Product Form Bottom Sheet -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
  shop: Shop,
  product: Product?,
  lang: String,
  onDismiss: () -> Unit,
  onSave: (Product, onResult: (Boolean, String?) -> Unit) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var name by remember(product?.id) { mutableStateOf(product?.name ?: "") }
  var category by remember(product?.id) { mutableStateOf(product?.category ?: Localization.PRODUCT_CATEGORIES[0]) }
  var unit by remember(product?.id) { mutableStateOf(product?.unit ?: "kg") }
  var price by remember(product?.id) { mutableStateOf(product?.price?.toString() ?: "") }
  var originalPrice by remember(product?.id) { mutableStateOf(product?.originalPrice?.toString() ?: "") }
  var onlineQty by remember(product?.id) { mutableStateOf(product?.onlineQty?.toString() ?: "10") }
  var shopQty by remember(product?.id) { mutableStateOf(product?.shopQty?.toString() ?: "5") }
  var lowAlert by remember(product?.id) { mutableStateOf(product?.lowAlert?.toString() ?: "5") }
  var imageUrl by remember(product?.id) { mutableStateOf(product?.imageUrl ?: "") }
  var description by remember(product?.id) { mutableStateOf(product?.description ?: "") }
  var active by remember(product?.id) { mutableStateOf(product?.active ?: true) }
  var errorMsg by remember(product?.id) { mutableStateOf<String?>(null) }
  var isSaving by remember(product?.id) { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.White
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(if (product != null) "Edit Product" else "New Product", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = NearKartInk)
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
      }

      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Product Name") },
        singleLine = true,
        textStyle = nearKartInputTextStyle(),
        colors = nearKartTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(10.dp))
      Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NearKartMute)
      Spacer(modifier = Modifier.height(4.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Localization.PRODUCT_CATEGORIES) { cat ->
          val isSelected = category == cat
          Surface(
            color = if (isSelected) NearKartBlue else NearKartBg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.clickable { category = cat }
          ) {
            Text(
              text = cat,
              color = if (isSelected) Color.White else NearKartInk,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text("Unit (how you sell it)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NearKartMute)
      Spacer(modifier = Modifier.height(4.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Localization.UNITS.forEach { u ->
          val isSelected = unit == u
          Surface(
            color = if (isSelected) NearKartBlue else NearKartBg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.clickable { unit = u }
          ) {
            Text(
              text = u,
              color = if (isSelected) Color.White else NearKartInk,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = price,
          onValueChange = { price = it },
          label = { Text("Price (₹)") },
          singleLine = true,
          textStyle = nearKartInputTextStyle(),
          colors = nearKartTextFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = originalPrice,
          onValueChange = { originalPrice = it },
          label = { Text("MRP (optional)") },
          singleLine = true,
          textStyle = nearKartInputTextStyle(),
          colors = nearKartTextFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = onlineQty,
          onValueChange = { onlineQty = it },
          label = { Text("Online Stock") },
          singleLine = true,
          textStyle = nearKartInputTextStyle(),
          colors = nearKartTextFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = shopQty,
          onValueChange = { shopQty = it },
          label = { Text("Counter Stock") },
          singleLine = true,
          textStyle = nearKartInputTextStyle(),
          colors = nearKartTextFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = lowAlert,
          onValueChange = { lowAlert = it },
          label = { Text("Low Stock Alert") },
          singleLine = true,
          textStyle = nearKartInputTextStyle(),
          colors = nearKartTextFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = imageUrl,
          onValueChange = { imageUrl = it },
          label = { Text("Photo link (optional)") },
          singleLine = true,
          textStyle = nearKartInputTextStyle(),
          colors = nearKartTextFieldColors(),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.6f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description (optional)") },
        minLines = 2,
        textStyle = nearKartInputTextStyle(),
        colors = nearKartTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      )

      if (product != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Active (visible to customers)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NearKartInk)
          Switch(
            checked = active,
            onCheckedChange = { active = it },
            colors = SwitchDefaults.colors(checkedThumbColor = NearKartBlue)
          )
        }
      }

      errorMsg?.let { err ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = err, color = NearKartRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }

      Spacer(modifier = Modifier.height(14.dp))
      Button(
        onClick = {
          if (isSaving) return@Button
          val trimmedName = name.trim()
          val pVal = price.trim().toDoubleOrNull()
          val opVal = if (originalPrice.isBlank()) null else originalPrice.trim().toDoubleOrNull()
          val oqVal = onlineQty.trim().toDoubleOrNull()
          val sqVal = shopQty.trim().toDoubleOrNull() ?: 0.0
          val laVal = lowAlert.trim().toDoubleOrNull() ?: 5.0

          if (trimmedName.isBlank()) {
            errorMsg = "Please enter the product name."
            return@Button
          }
          if (pVal == null || pVal <= 0.0) {
            errorMsg = "Please enter a valid price greater than 0."
            return@Button
          }
          if (opVal != null && opVal < pVal) {
            errorMsg = "MRP must be greater than or equal to price."
            return@Button
          }
          if (oqVal == null || oqVal < 0.0 || sqVal < 0.0 || laVal < 0.0) {
            errorMsg = "Stock quantities cannot be negative."
            return@Button
          }

          errorMsg = null
          isSaving = true
          val newProd = (product ?: Product(
            id = "prod_" + System.currentTimeMillis(),
            shopId = shop.id,
            name = trimmedName,
            price = pVal
          )).copy(
            name = trimmedName,
            category = category,
            unit = unit,
            price = pVal,
            originalPrice = opVal,
            onlineQty = oqVal,
            shopQty = sqVal,
            lowAlert = laVal,
            imageUrl = imageUrl.trim().ifBlank { null },
            description = description.trim().ifBlank { null },
            active = active
          )
          onSave(newProd) { success, err ->
            isSaving = false
            if (!success) {
              errorMsg = err ?: "Could not save product. Please try again."
            }
          }
        },
        enabled = !isSaving,
        colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
      ) {
        if (isSaving) {
          CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
          Text(Localization.tr("Save", lang) + " Product", fontWeight = FontWeight.Bold)
        }
      }
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

// ----------------- Payment Account Bottom Sheet -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentAccountBottomSheet(
  shop: Shop,
  onDismiss: () -> Unit,
  onSave: (upiId: String?, qrUrl: String?, onResult: (Boolean, String?) -> Unit) -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var upiInput by remember(shop.upiId) { mutableStateOf(shop.upiId ?: "") }
  var qrInput by remember(shop.qrUrl) { mutableStateOf(shop.qrUrl ?: "") }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val stream = context.contentResolver.openInputStream(uri)
        val original = stream?.use { BitmapFactory.decodeStream(it) }
        if (original != null) {
          val maxDim = 600
          val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
          val scaled = Bitmap.createScaledBitmap(
            original,
            (original.width * scale).toInt().coerceAtLeast(1),
            (original.height * scale).toInt().coerceAtLeast(1),
            true
          )
          val baos = ByteArrayOutputStream()
          scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
          val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
          qrInput = "data:image/jpeg;base64,$b64"
          errorMsg = null
        } else {
          errorMsg = "Could not read selected QR image."
        }
      } catch (_: Exception) {
        errorMsg = "Could not load selected QR image."
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.White
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Payment Account", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = NearKartInk)
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text("Customers who choose UPI will see this QR code and UPI ID, pay you directly and enter the 12-digit UTR.", fontSize = 12.sp, color = NearKartMute)

      Spacer(modifier = Modifier.height(12.dp))
      OutlinedTextField(
        value = upiInput,
        onValueChange = { upiInput = it },
        label = { Text("UPI ID") },
        placeholder = { Text("shopname@upi") },
        singleLine = true,
        textStyle = nearKartInputTextStyle(),
        shape = RoundedCornerShape(12.dp),
        colors = nearKartTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("seller_upi_id_input")
      )

      Spacer(modifier = Modifier.height(10.dp))
      OutlinedButton(
        onClick = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Upload QR Photo from Gallery", color = NearKartBlue, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = qrInput,
        onValueChange = { qrInput = it },
        label = { Text("QR Code Photo link (optional)") },
        placeholder = { Text("https://...") },
        singleLine = true,
        textStyle = nearKartInputTextStyle(),
        shape = RoundedCornerShape(12.dp),
        colors = nearKartTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
      )

      if (qrInput.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          QrCodeImage(qrData = qrInput, contentDescription = "QR Preview", modifier = Modifier.size(140.dp))
        }
      }

      errorMsg?.let { err ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = err, color = NearKartRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }

      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = {
          if (isSaving) return@Button
          val trimmedUpi = upiInput.trim()
          val trimmedQr = qrInput.trim()

          if (trimmedUpi.isBlank() && trimmedQr.isBlank()) {
            errorMsg = "Please enter a UPI ID or upload/enter a QR code."
            return@Button
          }
          if (trimmedUpi.isNotBlank() && !UPI_RE.matches(trimmedUpi)) {
            errorMsg = "Enter a valid UPI ID (e.g. yourname@okicici)."
            return@Button
          }
          if (trimmedQr.isNotBlank() &&
            !trimmedQr.startsWith("https://", ignoreCase = true) &&
            !trimmedQr.startsWith("http://", ignoreCase = true) &&
            !trimmedQr.startsWith("data:image/", ignoreCase = true)
          ) {
            errorMsg = "QR link must start with https://, http://, or be an uploaded image."
            return@Button
          }

          errorMsg = null
          isSaving = true
          onSave(trimmedUpi.ifBlank { null }, trimmedQr.ifBlank { null }) { success, err ->
            isSaving = false
            if (!success) {
              errorMsg = err ?: "Could not save payment details."
            }
          }
        },
        enabled = !isSaving,
        colors = ButtonDefaults.buttonColors(containerColor = NearKartBlue),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_seller_payment_button")
      ) {
        if (isSaving) {
          CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
          Text("Save Payment Details", fontWeight = FontWeight.Bold)
        }
      }
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
