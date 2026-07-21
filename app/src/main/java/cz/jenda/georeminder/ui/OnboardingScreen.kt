package cz.jenda.georeminder.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.ui.components.PrimaryButton
import cz.jenda.georeminder.ui.components.iosClickable
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val text: String,
)

/**
 * Uvítací průvodce při prvním spuštění – 3 stránky. Texty podle DESIGN_SPEC §5.1;
 * druhá stránka je přepsaná pro Android (oprávnění „Povolit vždy").
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val colors = GeoTheme.colors
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Filled.PinDrop,
            title = "Vítej v GeoReminderu",
            text = "Připomene ti úkoly přesně ve chvíli, kdy jsi na správném místě – nebo ve správný čas. „Koupit mléko, až budu u obchodu.“",
        ),
        OnboardingPage(
            icon = Icons.Filled.NearMe,
            title = "Poloha „Povolit vždy“",
            text = "Aby připomínka přišla i se zavřenou appkou, potřebuje Android svolit polohu „Povolit vždy“. Hlídání dělá úsporně systém – baterii to prakticky nezatěžuje.",
        ),
        OnboardingPage(
            icon = Icons.Filled.NotificationsActive,
            title = "Notifikace",
            text = "Bez notifikací ti appka nemá jak dát vědět. V dalším kroku je prosím povol – stejně jako přístup k poloze.",
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = page.title,
                    style = GeoType.title2Bold,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = page.text,
                    style = GeoType.body,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(40.dp))
            }
        }

        // Tečkový indikátor stránek v jemné kapsli
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(Color(0x14787880))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                colors.label.copy(
                                    alpha = if (index == pagerState.currentPage) 0.75f else 0.25f
                                )
                            ),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(
                text = if (isLast) "Povolit a začít" else "Pokračovat",
            ) {
                if (isLast) {
                    onFinish()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            }
            Text(
                text = "Přeskočit",
                style = GeoType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier
                    .iosClickable(onClick = onFinish)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}
