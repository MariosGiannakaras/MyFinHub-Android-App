package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.core.ui.PrivacySafeNoticeRecord
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NoticeHistoryScreen(
    entries: List<PrivacySafeNoticeRecord>,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Ιστορικό ειδοποιήσεων",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.xs,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            item("notice-privacy") {
                Text(
                    "Μόνο ασφαλή συμβάντα και κωδικοί υποστήριξης, χωρίς ποσά ή στοιχεία κάρτας.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isEmpty()) {
                item("notice-empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        Text("Δεν υπάρχουν ειδοποιήσεις", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Τα συμβάντα της εφαρμογής θα εμφανιστούν εδώ όταν προκύψουν.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(entries) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                            .padding(vertical = MyFinHubSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MyFinHubIconBadge(
                            icon = MyFinHubIcons.Attention,
                            tone = FinanceTone.Neutral,
                            contentDescription = null,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
                        ) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                diagnosticCodeDescription(entry.diagnosticCode)
                                    ?: "Καταγράφηκε συμβάν εφαρμογής για υποστήριξη.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatNoticeTime(entry.occurredAtEpochMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SelectionContainer {
                                Text(
                                    "Κωδικός υποστήριξης · ${entry.diagnosticCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (index < entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun formatNoticeTime(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("el-GR")))
}.getOrDefault("Άγνωστος χρόνος")
