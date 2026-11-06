package sy.medpro.watani.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sy.medpro.watani.data.SupportFavorites
import sy.medpro.watani.data.SupportMessages
import sy.medpro.watani.ui.drawer.DrawerScreenScaffold
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.RoseOutline
import sy.medpro.watani.ui.theme.ThmanyahFontFamily

private const val FILTER_ALL = "الكل"
private const val FILTER_FAVORITES = "المفضلة"

/** شاشة «رسائل دعم وطمأنينة»: بنك الدعم كاملًا مع تصفية بالتصنيف أو المفضلة. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SupportFavorites.init(context) }
    val favorites by SupportFavorites.favorites.collectAsState()
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }

    val filterOptions = listOf(FILTER_ALL) + SupportMessages.categories.map { it.title } + FILTER_FAVORITES
    val visibleMessages = remember(selectedFilter, favorites) {
        when {
            selectedFilter == FILTER_ALL -> SupportMessages.all
            selectedFilter == FILTER_FAVORITES -> SupportMessages.all.filter { favorites.contains(it.id) }
            else -> {
                val categoryId = SupportMessages.categories.firstOrNull { it.title == selectedFilter }?.id
                SupportMessages.all.filter { it.categoryId == categoryId }
            }
        }
    }

    DrawerScreenScaffold(title = "رسائل دعم وطمأنينة", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filterOptions) { option ->
                        FilterChip(
                            selected = selectedFilter == option,
                            onClick = { selectedFilter = option },
                            label = { Text(option, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaroonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
            if (visibleMessages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "لا توجد رسائل مفضلة بعد — اضغط القلب على أي رسالة لحفظها هنا.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                items(visibleMessages, key = { it.id }) { message ->
                    val isFavorite = favorites.contains(message.id)
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, RoseOutline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaroonPrimary.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = SupportMessages.categoryTitle(message.categoryId),
                                        color = MaroonPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                IconButton(onClick = { SupportFavorites.toggle(context, message.id) }) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                        tint = if (isFavorite) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message.text,
                                fontFamily = ThmanyahFontFamily,
                                fontSize = 18.sp,
                                lineHeight = 27.sp
                            )
                        }
                    }
                }
            }
        }
        // تذييل التنويه الثابت: خارج قائمة الرسائل، مرئي دائمًا أسفل
        // الشاشة أثناء التصفّح (لا يُكتشف إلا بنهاية التمرير).
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = SupportMessages.DISCLAIMER,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        }
    }
}
