package com.shohjahon.treble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

/** "Joylar" tabi: o'yinlar o'tkazilgan va o'tkaziladigan maydonlar. */
@Composable
fun VenuesTab(onShowVenueGames: (String) -> Unit) {
    val context = LocalContext.current
    val venues = GameRepository.venues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            text = "Joylar",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Text(
            text = "O'yinlar o'tkaziladigan maydonlar",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (venues.isEmpty()) {
                item {
                    EmptyState(
                        iconPath = IconPaths.Pin,
                        title = "Hozircha joylar yo'q",
                        subtitle = "O'yinlar e'lon qilingach, maydonlar shu yerda paydo bo'ladi."
                    )
                }
            } else {
                items(venues, key = { it.name }) { venue ->
                    SurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        GameImage(
                            path = venue.images.firstOrNull(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = venue.name,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (venue.address.isNotBlank()) {
                                Text(
                                    text = venue.address,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            StatusPill(
                                text = if (venue.activeGames > 0) "${venue.activeGames} ta faol o'yin" else "Faol o'yin yo'q",
                                background = if (venue.activeGames > 0) StatusOpenBg else StatusFullBg,
                                foreground = if (venue.activeGames > 0) StatusOpenFg else StatusFullFg
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DialogButton(
                                    text = "Xaritada",
                                    onClick = { openMapFor(context, venue.name, venue.address, venue.mapLink) },
                                    modifier = Modifier.weight(1f)
                                )
                                DialogButton(
                                    text = "O'yinlar",
                                    onClick = { onShowVenueGames(venue.name) },
                                    modifier = Modifier.weight(1f),
                                    primary = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
