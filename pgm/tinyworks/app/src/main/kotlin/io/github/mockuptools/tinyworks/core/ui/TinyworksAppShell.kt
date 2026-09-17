package io.github.mockuptools.tinyworks.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex

data class TinyworksMenuItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun TinyworksAppShell(
    menuItems: List<TinyworksMenuItem>,
    content: @Composable (Modifier) -> Unit,
) {
    var isMenuOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isMenuOpen) {
        isMenuOpen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TinyworksHeader(
                onOpenMenu = { isMenuOpen = true },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                content(Modifier.fillMaxSize())
            }
        }

        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { isMenuOpen = false },
                )
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.82f)
                        .align(Alignment.CenterEnd)
                        .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Tinyworks",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp),
                        )
                        menuItems.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                selected = item.selected,
                                onClick = {
                                    isMenuOpen = false
                                    item.onClick()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TinyworksHeader(onOpenMenu: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF6750A4),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tinyworks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenMenu) {
                Text(
                    text = "☰",
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                )
            }
        }
    }
}
