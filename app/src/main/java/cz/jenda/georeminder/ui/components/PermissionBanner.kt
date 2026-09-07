package cz.jenda.georeminder.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.jenda.georeminder.ui.theme.GeoTheme
import cz.jenda.georeminder.ui.theme.GeoType

/**
 * Oranžový banner zobrazený, když uživatel odmítl oprávnění.
 * Tlačítko vede přímo do systémového nastavení appky.
 */
@Composable
fun PermissionBanner(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "Nastavení",
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GeoTheme.colors.orange)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bannerTextColor = Color(0xFF1C1C1E)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = bannerTextColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = message,
            style = GeoType.footnote,
            color = bannerTextColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = actionLabel,
            style = GeoType.footnoteBold,
            color = bannerTextColor,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, bannerTextColor, CircleShape)
                .iosClickable {
                    if (onAction != null) {
                        onAction()
                    } else {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
