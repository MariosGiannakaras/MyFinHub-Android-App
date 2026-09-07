#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))


def regex_once(path: str, pattern: str, replacement: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one regex match in {path}, found {count}: {pattern[:120]!r}")
    write(path, updated)


# Approved provider identity mirrors the desktop bankBrands registry. No remote assets are used.
write("app/src/main/java/app/myfinhub/android/core/ui/FinancialProvider.kt", r'''package app.myfinhub.android.core.ui

import java.util.Locale

enum class FinancialProvider(
    val key: String,
    val institutionLabel: String,
    val cardLabel: String,
) {
    PIRAEUS("piraeus", "Τράπεζα Πειραιώς", "Piraeus"),
    REVOLUT("revolut", "Revolut", "Revolut"),
    ALPHA("alpha", "Alpha Bank", "ALPHA BANK"),
    PAYZY("payzy", "payzy by COSMOTE", "payzy"),
    VIVA("viva", "Viva.com", "Viva.com"),
}

fun financialProvider(id: String?, name: String?): FinancialProvider? {
    val text = "${id.orEmpty()} ${name.orEmpty()}".lowercase(Locale.ROOT)
    return when {
        "piraeus" in text || "πειραι" in text -> FinancialProvider.PIRAEUS
        "revolut" in text -> FinancialProvider.REVOLUT
        "alpha" in text -> FinancialProvider.ALPHA
        "payzy" in text -> FinancialProvider.PAYZY
        "viva" in text -> FinancialProvider.VIVA
        else -> null
    }
}

fun financialAccountDisplayName(accountId: String, canonicalName: String, kind: String): String {
    if (kind == "cash" || accountId == "cash") return canonicalName.ifBlank { "Μετρητά" }
    val provider = financialProvider(accountId, canonicalName)
    val trimmed = canonicalName.trim()
    val withoutInstitution = when (provider) {
        FinancialProvider.PIRAEUS -> trimmed
            .replace(Regex("^(Πειραιώς|Piraeus)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        FinancialProvider.REVOLUT -> trimmed.takeUnless { it.equals("Revolut", ignoreCase = true) }.orEmpty()
        FinancialProvider.ALPHA -> trimmed.takeUnless { it.equals("Alpha Bank", ignoreCase = true) }.orEmpty()
        FinancialProvider.PAYZY -> trimmed.takeUnless { it.contains("payzy", ignoreCase = true) }.orEmpty()
        FinancialProvider.VIVA -> trimmed.takeUnless { it.contains("viva", ignoreCase = true) }.orEmpty()
        null -> trimmed
    }
    if (withoutInstitution.isNotBlank()) return withoutInstitution
    return when (kind) {
        "savings" -> "Αποταμιευτικός"
        "bank" -> "Λογαριασμός"
        else -> trimmed.ifBlank { "Λογαριασμός" }
    }
}
''')

write("app/src/main/java/app/myfinhub/android/designsystem/MyFinHubProviderMark.kt", r'''package app.myfinhub.android.designsystem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.myfinhub.android.R
import app.myfinhub.android.core.ui.FinancialProvider

@Composable
fun MyFinHubProviderMark(
    provider: FinancialProvider,
    modifier: Modifier = Modifier,
    contentDescription: String? = provider.institutionLabel,
) {
    val drawable = when (provider) {
        FinancialProvider.PIRAEUS -> R.drawable.mfh_bank_piraeus_mark
        FinancialProvider.REVOLUT -> R.drawable.mfh_bank_revolut_mark
        FinancialProvider.ALPHA -> R.drawable.mfh_bank_alpha_mark
        FinancialProvider.PAYZY -> R.drawable.mfh_payzy_reference_logo
        FinancialProvider.VIVA -> R.drawable.mfh_viva_reference_logo
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
''')

write("app/src/main/res/drawable/mfh_bank_piraeus_mark.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">
    <path android:fillColor="#FF17345F" android:pathData="M4.5,27 L10.8,5 L14.9,5 L8.7,27 Z" />
    <path android:fillColor="#FF17345F" android:pathData="M11.8,27 L18.1,5 L22.2,5 L16,27 Z" />
    <path android:fillColor="#FF17345F" android:pathData="M19.1,27 L25.4,5 L29.5,5 L23.3,27 Z" />
</vector>
''')

write("app/src/main/res/drawable/mfh_bank_revolut_mark.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">
    <path android:fillColor="#FF050505" android:pathData="M8,1 L24,1 C27.866,1 31,4.134 31,8 L31,24 C31,27.866 27.866,31 24,31 L8,31 C4.134,31 1,27.866 1,24 L1,8 C1,4.134 4.134,1 8,1 Z" />
    <path android:fillColor="#FFFFFFFF" android:pathData="M9,7 L17.2,7 C21.9,7 24.8,9.3 24.8,13.1 C24.8,15.8 23.3,17.8 20.6,18.7 L25,25 L19.8,25 L16.1,19.3 L14,19.3 L14,25 L9,25 Z M14,11.1 L14,15.4 L17.2,15.4 C18.9,15.4 19.9,14.6 19.9,13.2 C19.9,11.9 18.9,11.1 17.2,11.1 Z" />
</vector>
''')

write("app/src/main/res/drawable/mfh_bank_alpha_mark.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">
    <path android:fillColor="#FF164AA6" android:pathData="M16,1 C24.284,1 31,7.716 31,16 C31,24.284 24.284,31 16,31 C7.716,31 1,24.284 1,16 C1,7.716 7.716,1 16,1 Z" />
    <path android:fillColor="#FFFFFFFF" android:pathData="M7.8,23.8 L14.5,8.2 L17.5,8.2 L24.2,23.8 L20.1,23.8 L18.7,20.2 L13.2,20.2 L11.8,23.8 Z M14.6,16.8 L17.4,16.8 L16,13.3 Z" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6" android:strokeLineCap="round" android:pathData="M10.4,25.4 C12.5,26.4 14.3,26.9 16,26.9 C18,26.9 19.8,26.4 21.6,25.4" />
</vector>
''')

# Home model: keep the legacy role for compatibility but add a dedicated institution field.
replace_once(
    "app/src/main/java/app/myfinhub/android/feature/home/HomeUiState.kt",
    "    val balanceTrend: List<Double> = listOf(balance, balance),\n)",
    "    val balanceTrend: List<Double> = listOf(balance, balance),\n    val institution: String? = null,\n)",
)

# Money models: provider-aware account identity and canonical per-card movements.
replace_once(
    "app/src/main/java/app/myfinhub/android/feature/money/MoneyUiState.kt",
    "data class MoneyAccount(\n    val id: String,\n    val name: String,\n    val balance: Double,\n    val kind: String,\n)\n\ndata class MoneyCard(",
    "data class MoneyAccount(\n    val id: String,\n    val name: String,\n    val balance: Double,\n    val kind: String,\n    val institution: String? = null,\n)\n\nenum class MoneyCardActivityKind { PURCHASE, PAYMENT }\n\ndata class MoneyCardActivity(\n    val id: String,\n    val dateLabel: String,\n    val title: String,\n    val amount: Double,\n    val kind: MoneyCardActivityKind,\n)\n\ndata class MoneyCard(",
)
replace_once(
    "app/src/main/java/app/myfinhub/android/feature/money/MoneyUiState.kt",
    "    val bankId: String = \"\",\n)\n",
    "    val bankId: String = \"\",\n    val canonicalKind: String = \"\",\n    val activity: List<MoneyCardActivity> = emptyList(),\n)\n",
)

# Canonical projection: never surface shorthand as an account label and project linked card events.
projection = "app/src/main/java/app/myfinhub/android/app/CanonicalProductProjection.kt"
replace_once(
    projection,
    "import app.myfinhub.android.core.data.string\n",
    "import app.myfinhub.android.core.data.string\nimport app.myfinhub.android.core.ui.financialAccountDisplayName\nimport app.myfinhub.android.core.ui.financialProvider\n",
)
replace_once(
    projection,
    "import app.myfinhub.android.feature.money.MoneyCard\n",
    "import app.myfinhub.android.feature.money.MoneyCard\nimport app.myfinhub.android.feature.money.MoneyCardActivity\nimport app.myfinhub.android.feature.money.MoneyCardActivityKind\n",
)
replace_once(
    projection,
    "import kotlin.math.roundToInt\n",
    "import kotlin.math.abs\nimport kotlin.math.roundToInt\n",
)
replace_once(
    projection,
    "    val rawHomeAccounts = accounts.filter { it.kind != \"credit\" }.map { account ->\n        HomeAccount(\n            id = account.id,\n            name = account.name,\n            role = account.shortName ?: accountKindLabel(account.kind),\n            balance = balances[account.id] ?: 0.0,",
    "    val rawHomeAccounts = accounts.filter { it.kind != \"credit\" }.map { account ->\n        val provider = financialProvider(account.id, account.name)\n        HomeAccount(\n            id = account.id,\n            name = financialAccountDisplayName(account.id, account.name, account.kind),\n            role = accountKindLabel(account.kind),\n            institution = provider?.institutionLabel,\n            balance = balances[account.id] ?: 0.0,",
)
replace_once(
    projection,
    "        accounts = accounts.filter { it.kind != \"credit\" }.map { account ->\n            MoneyAccount(\n                id = account.id,\n                name = account.name,\n                balance = balances[account.id] ?: 0.0,\n                kind = accountKindLabel(account.kind),\n            )\n        },",
    "        accounts = accounts.filter { it.kind != \"credit\" }.map { account ->\n            val provider = financialProvider(account.id, account.name)\n            MoneyAccount(\n                id = account.id,\n                name = financialAccountDisplayName(account.id, account.name, account.kind),\n                balance = balances[account.id] ?: 0.0,\n                kind = accountKindLabel(account.kind),\n                institution = provider?.institutionLabel,\n            )\n        },",
)
replace_once(
    projection,
    "        cards = activeCards.map { card ->\n            val eventOutstanding = document.cardOutstanding(card.id, asOf)\n            MoneyCard(",
    "        cards = activeCards.map { card ->\n            val eventOutstanding = document.cardOutstanding(card.id, asOf)\n            val cardActivity = events\n                .filter { event -> event.cardId == card.id && event.kind in setOf(\"card_purchase\", \"card_payment\") }\n                .sortedWith(compareByDescending<CanonicalEvent> { it.date }.thenByDescending { eventChronology[it.id].orEmpty() }.thenByDescending { it.id })\n                .map { event ->\n                    val payment = event.kind == \"card_payment\"\n                    MoneyCardActivity(\n                        id = event.id,\n                        dateLabel = formatDate(event.date),\n                        title = event.note.ifBlank { if (payment) \"Πληρωμή κάρτας\" else event.category ?: \"Αγορά\" },\n                        amount = if (payment) abs(event.amount) else -abs(event.amount),\n                        kind = if (payment) MoneyCardActivityKind.PAYMENT else MoneyCardActivityKind.PURCHASE,\n                    )\n                }\n            MoneyCard(",
)
replace_once(
    projection,
    "                bankId = bankIdsByCard[card.id].orEmpty(),\n            )",
    "                bankId = bankIdsByCard[card.id].orEmpty(),\n                canonicalKind = card.kind,\n                activity = cardActivity,\n            )",
)

# Home account cards: account name first, institution second, approved provider mark instead of a generic badge.
home = "app/src/main/java/app/myfinhub/android/feature/home/ProductionHomeScreen.kt"
replace_once(home, "import androidx.compose.foundation.layout.height\n", "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.size\n")
replace_once(home, "import app.myfinhub.android.designsystem.FinanceTone\n", "import app.myfinhub.android.core.ui.financialProvider\nimport app.myfinhub.android.designsystem.FinanceTone\n")
replace_once(home, "import app.myfinhub.android.designsystem.MyFinHubIconBadge\n", "import app.myfinhub.android.designsystem.MyFinHubIconBadge\nimport app.myfinhub.android.designsystem.MyFinHubProviderMark\n")
replace_once(
    home,
    "                MyFinHubIconBadge(\n                    icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,\n                    tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,\n                    contentDescription = null,\n                )\n                Column(modifier = Modifier.weight(1f)) {\n                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n                    Text(account.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                }",
    "                val provider = financialProvider(account.id, account.institution ?: account.name)\n                if (provider != null) {\n                    MyFinHubProviderMark(provider, modifier = Modifier.size(36.dp), contentDescription = provider.institutionLabel)\n                } else {\n                    MyFinHubIconBadge(\n                        icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,\n                        tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,\n                        contentDescription = null,\n                    )\n                }\n                Column(modifier = Modifier.weight(1f)) {\n                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n                    account.institution?.takeIf(String::isNotBlank)?.let { institution ->\n                        Text(institution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }\n                }",
)
replace_once(
    home,
    "                    MyFinHubIconBadge(\n                        icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,\n                        tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,\n                        contentDescription = null,\n                    )\n                    Column(modifier = Modifier.weight(1f)) {\n                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)\n                        Text(account.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }",
    "                    val provider = financialProvider(account.id, account.institution ?: account.name)\n                    if (provider != null) {\n                        MyFinHubProviderMark(provider, modifier = Modifier.size(32.dp), contentDescription = provider.institutionLabel)\n                    } else {\n                        MyFinHubIconBadge(\n                            icon = if (savings) MyFinHubIcons.Savings else MyFinHubIcons.Account,\n                            tone = if (savings) FinanceTone.Savings else FinanceTone.Neutral,\n                            contentDescription = null,\n                        )\n                    }\n                    Column(modifier = Modifier.weight(1f)) {\n                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)\n                        account.institution?.takeIf(String::isNotBlank)?.let { institution ->\n                            Text(institution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        }\n                    }",
)

# Money/account surfaces use the same provider identity. Make card creation a clear primary action.
money_screens = "app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt"
replace_once(money_screens, "import androidx.compose.foundation.layout.padding\n", "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.size\n")
replace_once(money_screens, "import androidx.compose.material3.HorizontalDivider\n", "import androidx.compose.material3.FilledTonalButton\nimport androidx.compose.material3.HorizontalDivider\n")
replace_once(money_screens, "import app.myfinhub.android.core.security.SecureWindowProtection\n", "import app.myfinhub.android.core.security.SecureWindowProtection\nimport app.myfinhub.android.core.ui.financialProvider\n")
replace_once(money_screens, "import app.myfinhub.android.designsystem.MyFinHubIconBadge\n", "import app.myfinhub.android.designsystem.MyFinHubIconBadge\nimport app.myfinhub.android.designsystem.MyFinHubProviderMark\n")
replace_once(
    money_screens,
    "                                    MyFinHubIconBadge(\n                                        icon = MyFinHubIcons.Account,\n                                        tone = if (account.kind.contains(\"Αποταμί\", ignoreCase = true)) {\n                                            FinanceTone.Savings\n                                        } else {\n                                            FinanceTone.Neutral\n                                        },\n                                        contentDescription = null,\n                                    )\n                                    Column(modifier = Modifier.weight(1f)) {\n                                        Text(account.name, style = MaterialTheme.typography.titleMedium)\n                                        Text(\n                                            account.kind,\n                                            style = MaterialTheme.typography.bodySmall,\n                                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                                        )\n                                    }",
    "                                    val provider = financialProvider(account.id, account.institution ?: account.name)\n                                    if (provider != null) {\n                                        MyFinHubProviderMark(provider, modifier = Modifier.size(34.dp), contentDescription = provider.institutionLabel)\n                                    } else {\n                                        MyFinHubIconBadge(\n                                            icon = MyFinHubIcons.Account,\n                                            tone = if (account.kind.contains(\"Αποταμί\", ignoreCase = true)) FinanceTone.Savings else FinanceTone.Neutral,\n                                            contentDescription = null,\n                                        )\n                                    }\n                                    Column(modifier = Modifier.weight(1f)) {\n                                        Text(account.name, style = MaterialTheme.typography.titleMedium)\n                                        Text(\n                                            account.institution ?: account.kind,\n                                            style = MaterialTheme.typography.bodySmall,\n                                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                                        )\n                                    }",
)
replace_once(
    money_screens,
    "                    TextButton(onClick = onAddCard) { Text(\"Προσθήκη\") }",
    "                    FilledTonalButton(onClick = onAddCard) { Text(\"Νέα κάρτα\") }",
)
replace_once(
    money_screens,
    "                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {\n                        EmptyFinanceText(\"Δεν υπάρχουν ενεργές κάρτες.\")\n                    }",
    "                    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {\n                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {\n                            EmptyFinanceText(\"Δεν υπάρχουν ενεργές κάρτες.\")\n                            FilledTonalButton(onClick = onAddCard) { Text(\"Δημιουργία πρώτης κάρτας\") }\n                        }\n                    }",
)

account_detail = "app/src/main/java/app/myfinhub/android/feature/money/CanonicalAccountDetailScreen.kt"
replace_once(account_detail, "                subtitle = account?.kind ?: \"Λεπτομέρειες λογαριασμού\",", "                subtitle = account?.institution ?: account?.kind ?: \"Λεπτομέρειες λογαριασμού\",")

# Card creation: known institution choices first, custom issuer still supported.
write("app/src/main/java/app/myfinhub/android/feature/money/CanonicalCardCreateScreen.kt", r'''package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.FinancialProvider
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.util.UUID

data class CardCreateRequest(
    val cardId: String,
    val bankId: String,
    val nickname: String,
    val kind: String,
    val network: String,
    val formFactor: String,
    val last4: String?,
    val creditLimit: Double?,
)

private val cardProviders = listOf(
    FinancialProvider.PIRAEUS,
    FinancialProvider.ALPHA,
    FinancialProvider.REVOLUT,
    FinancialProvider.PAYZY,
    FinancialProvider.VIVA,
)

@Composable
fun CanonicalCardCreateScreen(
    cards: List<MoneyCard>,
    onCreate: (CardCreateRequest) -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<FinancialProvider?>(FinancialProvider.PIRAEUS) }
    var customBank by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("debit") }
    var network by remember { mutableStateOf("visa") }
    var formFactor by remember { mutableStateOf("physical") }
    var last4 by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var submittedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cards, submittedId) {
        val id = submittedId ?: return@LaunchedEffect
        if (cards.any { it.id == id }) onBack()
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κάρτα",
                subtitle = "Χρεωστική, προπληρωμένη ή πιστωτική",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Η κάρτα συγχρονίζεται με το οικονομικό σου αρχείο. PAN και λήξη παραμένουν στο ασφαλές server vault και το CVV μόνο στη συσκευή.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it; validation = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Όνομα κάρτας") },
                singleLine = true,
                enabled = submittedId == null,
            )

            Text("Τράπεζα / εκδότης", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                cardProviders.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { selectedProvider = provider; validation = null },
                        label = { Text(provider.institutionLabel) },
                        leadingIcon = {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(20.dp), contentDescription = null)
                        },
                        enabled = submittedId == null,
                    )
                }
                FilterChip(
                    selected = selectedProvider == null,
                    onClick = { selectedProvider = null; validation = null },
                    label = { Text("Άλλος εκδότης") },
                    enabled = submittedId == null,
                )
            }
            if (selectedProvider == null) {
                OutlinedTextField(
                    value = customBank,
                    onValueChange = { customBank = it; validation = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Όνομα εκδότη") },
                    singleLine = true,
                    enabled = submittedId == null,
                )
            }

            Text("Τύπος", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                listOf("debit" to "Χρεωστική", "prepaid" to "Προπληρωμένη", "credit" to "Πιστωτική").forEach { (value, label) ->
                    FilterChip(
                        selected = kind == value,
                        onClick = { kind = value; validation = null },
                        label = { Text(label) },
                        enabled = submittedId == null,
                    )
                }
            }
            Text("Δίκτυο", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                listOf("visa" to "Visa", "mastercard" to "Mastercard", "other" to "Άλλο").forEach { (value, label) ->
                    FilterChip(
                        selected = network == value,
                        onClick = { network = value },
                        label = { Text(label) },
                        enabled = submittedId == null,
                    )
                }
            }
            Text("Μορφή", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                listOf("physical" to "Φυσική", "virtual" to "Virtual").forEach { (value, label) ->
                    FilterChip(
                        selected = formFactor == value,
                        onClick = { formFactor = value },
                        label = { Text(label) },
                        enabled = submittedId == null,
                    )
                }
            }
            OutlinedTextField(
                value = last4,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) last4 = it; validation = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Τελευταία 4 ψηφία (προαιρετικό)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = submittedId == null,
            )
            if (kind == "credit") {
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { creditLimit = it; validation = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Πιστωτικό όριο (προαιρετικό)") },
                    suffix = { Text("€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = submittedId == null,
                )
            }
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MyFinHubPrimaryAction(
                label = if (submittedId == null) "Δημιουργία κάρτας" else "Αποθήκευση…",
                onClick = {
                    val normalizedNickname = nickname.trim()
                    val normalizedBank = selectedProvider?.key ?: customBank.trim()
                    val normalizedLast4 = last4.trim().takeIf(String::isNotBlank)
                    val limit = creditLimit.trim().replace(',', '.').takeIf(String::isNotBlank)?.toDoubleOrNull()
                    validation = when {
                        normalizedNickname.isBlank() -> "Συμπλήρωσε όνομα κάρτας."
                        normalizedBank.isBlank() -> "Συμπλήρωσε τράπεζα ή εκδότη."
                        normalizedLast4 != null && normalizedLast4.length != 4 -> "Τα τελευταία ψηφία πρέπει να είναι ακριβώς τέσσερα."
                        kind == "credit" && creditLimit.isNotBlank() && (limit == null || limit <= 0.0) -> "Το πιστωτικό όριο πρέπει να είναι μεγαλύτερο από μηδέν."
                        else -> null
                    }
                    if (validation == null) {
                        val id = "card-android-${UUID.randomUUID()}"
                        submittedId = id
                        onCreate(
                            CardCreateRequest(
                                cardId = id,
                                bankId = normalizedBank,
                                nickname = normalizedNickname,
                                kind = kind,
                                network = network,
                                formFactor = formFactor,
                                last4 = normalizedLast4,
                                creditLimit = if (kind == "credit") limit else null,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = submittedId == null,
                icon = null,
            )
        }
    }
}
''')

# Card detail now owns credit-card movements in addition to secure card data.
write("app/src/main/java/app/myfinhub/android/feature/money/CanonicalCardDetailScreen.kt", r'''package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.security.SecureWindowProtection
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onReveal: () -> Unit = {},
    onHideSecrets: () -> Unit = {},
    onSaveCvv: (CharArray) -> Unit = { value -> value.fill('\u0000') },
    onDeleteCvv: () -> Unit = {},
    onBack: () -> Unit,
) {
    val relevantState = when (secretState) {
        is CardSecretUiState.Hidden -> secretState.takeIf { it.cardId == null || it.cardId == card?.id }
        is CardSecretUiState.Loading -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Revealed -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Failure -> secretState.takeIf { it.cardId == card?.id }
        CardSecretUiState.AuthRejected -> secretState
    } ?: CardSecretUiState.Hidden(card?.id)

    SecureWindowProtection(active = relevantState is CardSecretUiState.Revealed)
    var cvvDraft by remember(card?.id) { mutableStateOf("") }
    val provider = card?.let { financialProvider(it.bankId, it.nickname) }
    val isCredit = card?.let { it.canonicalKind == "credit" || it.kind.contains("Πιστωτική", ignoreCase = true) } == true

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = card?.nickname ?: "Κάρτα",
                subtitle = provider?.institutionLabel ?: card?.kind ?: "Δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubDesignMetrics.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (card == null) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Η κάρτα δεν είναι διαθέσιμη.")
                }
                return@Column
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (provider != null) {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(36.dp), contentDescription = provider.institutionLabel)
                        } else {
                            MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Transfer, null)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            provider?.institutionLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("•••• ${card.last4}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(card.kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isCredit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MyFinHubAmountText(formatCardEuro(card.currentBalance), FinanceTone.Expense, style = MaterialTheme.typography.titleLarge)
                            }
                            card.limit?.takeIf { it > 0.0 }?.let { limit ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Όριο", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatCardEuro(limit), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }

            if (isCredit) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Κινήσεις πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Αγορές και πληρωμές που είναι πραγματικά συνδεδεμένες με αυτή την κάρτα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (card.activity.isEmpty()) {
                            Text("Δεν υπάρχουν ακόμη συνδεδεμένες κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            card.activity.take(20).forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MyFinHubIconBadge(
                                        icon = if (item.kind == MoneyCardActivityKind.PAYMENT) MyFinHubIcons.Income else MyFinHubIcons.Card,
                                        tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                                        contentDescription = null,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                        Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    MyFinHubAmountText(
                                        text = formatSignedCardEuro(item.amount),
                                        tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                                    )
                                }
                                if (index != card.activity.take(20).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "PAN/λήξη αποκαλύπτονται μόνο από το owner+AAL2 server vault. Το CVV παραμένει αποκλειστικά σε κρυπτογραφημένο vault αυτής της συσκευής.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (relevantState) {
                        is CardSecretUiState.Hidden -> {
                            MyFinHubPrimaryAction(
                                label = "Αποκάλυψη ασφαλών στοιχείων",
                                onClick = onReveal,
                                modifier = Modifier.fillMaxWidth(),
                                icon = null,
                            )
                            Text(
                                "Η οθόνη και το recent-app thumbnail προστατεύονται μόνο όσο εμφανίζονται τα πραγματικά στοιχεία.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is CardSecretUiState.Loading -> {
                            CircularProgressIndicator()
                            Text("Ανάκτηση ασφαλών στοιχείων…")
                        }
                        is CardSecretUiState.Failure -> {
                            Text(relevantState.message, color = MaterialTheme.colorScheme.error)
                            if (relevantState.retryable) {
                                MyFinHubPrimaryAction(
                                    label = "Δοκιμή ξανά",
                                    onClick = onReveal,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = null,
                                )
                            }
                        }
                        CardSecretUiState.AuthRejected -> {
                            Text("Η ασφαλής συνεδρία δεν είναι πλέον έγκυρη. Θα χρειαστεί νέα σύνδεση.", color = MaterialTheme.colorScheme.error)
                        }
                        is CardSecretUiState.Revealed -> {
                            SecretValue("PAN", relevantState.pan ?: "Δεν έχει αποθηκευτεί")
                            SecretValue("Λήξη", relevantState.expiry ?: "Δεν έχει αποθηκευτεί")
                            SecretValue("CVV", relevantState.cvv ?: "Δεν έχει αποθηκευτεί στη συσκευή")
                            TextButton(onClick = onHideSecrets) { Text("Απόκρυψη στοιχείων") }
                            MyFinHubOutlinedField(
                                value = cvvDraft,
                                onValueChange = { input -> cvvDraft = input.filter { it in '0'..'9' }.take(4) },
                                label = "Νέο CVV για αυτή τη συσκευή",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            MyFinHubPrimaryAction(
                                label = if (relevantState.cvvSaving) "Αποθήκευση…" else "Αποθήκευση CVV στη συσκευή",
                                onClick = {
                                    val chars = cvvDraft.toCharArray()
                                    cvvDraft = ""
                                    onSaveCvv(chars)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !relevantState.cvvSaving && cvvDraft.length in 3..4,
                                icon = null,
                            )
                            if (relevantState.cvv != null) {
                                MyFinHubDestructiveTextAction(
                                    label = "Διαγραφή τοπικού CVV",
                                    onClick = onDeleteCvv,
                                    enabled = !relevantState.cvvSaving,
                                )
                            }
                            relevantState.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(MyFinHubDesignMetrics.secretValueLabelWidth), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatCardEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedCardEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatCardEuro(value)
''')

# Card stack: stable interactive pager bullets, delayed canonical delete so the existing shred animation is visible,
# and approved provider marks on card faces.
stack = "app/src/main/java/app/myfinhub/android/feature/money/ReferenceCreditCardStack.kt"
replace_once(stack, "import app.myfinhub.android.R\n", "import app.myfinhub.android.R\nimport app.myfinhub.android.core.ui.FinancialProvider\nimport app.myfinhub.android.core.ui.financialProvider\n")
replace_once(stack, "import app.myfinhub.android.R\nimport app.myfinhub.android.core.ui.FinancialProvider\nimport app.myfinhub.android.core.ui.financialProvider\n", "import app.myfinhub.android.R\nimport app.myfinhub.android.core.ui.FinancialProvider\nimport app.myfinhub.android.core.ui.financialProvider\nimport app.myfinhub.android.designsystem.MyFinHubProviderMark\n")
replace_once(
    stack,
    "    fun commitDelete(cardId: String) {\n        if (deletingId != null) return\n        deleteArmedId = null\n        deleteProgress = 0f\n        onHideSecrets()\n        statusMessage = \"Η διαγραφή της κάρτας ξεκίνησε\"\n        onDeleteCard(cardId)\n        deletingId = cardId\n        scope.launch {\n            if (!reducedMotion) delay(620)\n            deletingId = null\n        }\n    }",
    "    fun commitDelete(cardId: String) {\n        if (deletingId != null) return\n        deleteArmedId = null\n        deleteProgress = 0f\n        onHideSecrets()\n        statusMessage = \"Η διαγραφή της κάρτας ξεκίνησε\"\n        deletingId = cardId\n        scope.launch {\n            // Keep the canonical card in the projected list until the approved shred/collapse\n            // transition has actually been visible. Reduced-motion users skip decoration.\n            if (!reducedMotion) delay(620)\n            onDeleteCard(cardId)\n            if (!reducedMotion) delay(80)\n            if (deletingId == cardId) deletingId = null\n        }\n    }",
)
replace_once(
    stack,
    "                    ReferenceBrandMark(visual)",
    "                    ReferenceBrandMark(visual, card)",
)
regex_once(
    stack,
    r"@Composable\nprivate fun ReferenceBrandMark\(visual: ReferenceCardVisual\) \{.*?\n\}\n\n@Composable\nprivate fun ReferenceSecretLine",
    r'''@Composable
private fun ReferenceBrandMark(visual: ReferenceCardVisual, card: MoneyCard) {
    val provider = financialProvider(card.bankId, visual.label)
    if (provider == null) {
        Text(
            visual.label,
            color = visual.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MyFinHubProviderMark(
            provider = provider,
            modifier = if (provider == FinancialProvider.PAYZY || provider == FinancialProvider.VIVA) {
                Modifier.height(26.dp).widthIn(max = 92.dp)
            } else {
                Modifier.size(27.dp)
            },
            contentDescription = provider.institutionLabel,
        )
        if (provider != FinancialProvider.PAYZY && provider != FinancialProvider.VIVA) {
            Text(
                provider.cardLabel,
                color = visual.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReferenceSecretLine''',
)
# Insert direct-selection helper before delete commit.
replace_once(
    stack,
    "    fun commitDelete(cardId: String) {",
    "    fun selectCard(cardId: String) {\n        val index = order.indexOf(cardId)\n        if (index <= 0 || deletingId != null || deleteArmedId != null) return\n        onHideSecrets()\n        scope.launch {\n            if (!reducedMotion) {\n                settleOffset.snapTo(0f)\n                settleOffset.animateTo(restackDistance * .55f, tween(140))\n            }\n            order = order.drop(index) + order.take(index)\n            settleOffset.snapTo(0f)\n        }\n    }\n\n    fun commitDelete(cardId: String) {",
)
regex_once(
    stack,
    r"        Row\(\n            horizontalArrangement = Arrangement\.spacedBy\(7\.dp\),\n            verticalAlignment = Alignment\.CenterVertically,\n            modifier = Modifier\.testTag\(\"credit_card_stack_dots\"\),\n        \) \{\n            orderedCards\.forEachIndexed \{ index, card ->\n                if \(card\.id != deletingId\) \{\n                    Box\(\n                        Modifier\n                            \.width\(if \(index == 0\) 22\.dp else 6\.dp\)\n                            \.height\(6\.dp\)\n                            \.clip\(RoundedCornerShape\(999\.dp\)\)\n                            \.background\(if \(index == 0\) Color\(0xFF4777D6\) else Color\(0xFFBDC9DB\)\)\n                            \.testTag\(\"credit_card_dot_\$\{card\.id\}\"\),\n                    \)\n                \}\n            \}\n        \}",
    r'''        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("credit_card_stack_dots"),
        ) {
            ids.forEachIndexed { index, cardId ->
                val card = cardById[cardId] ?: return@forEachIndexed
                if (card.id != deletingId) {
                    val active = card.id == activeId
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                enabled = !active && deletingId == null && deleteArmedId == null,
                                role = Role.Button,
                                onClick = { selectCard(card.id) },
                            )
                            .semantics {
                                contentDescription = "Κάρτα ${index + 1} από ${ids.size}: ${card.nickname}${if (active) ", ενεργή" else ""}"
                            }
                            .testTag("credit_card_dot_${card.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(if (active) 22.dp else 7.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) Color(0xFF4777D6) else Color(0xFFBDC9DB)),
                        )
                    }
                }
            }
        }''',
)

# Insights: three coherent visual stories instead of a stack of metric cards.
write("app/src/main/java/app/myfinhub/android/feature/insights/InsightsScreen.kt", r'''package app.myfinhub.android.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.financeToneColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onOpenSupportingActivity: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val trend = state.monthlyTrend.takeLast(4)
    val latest = trend.lastOrNull()
    val previous = trend.dropLast(1).lastOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Εικόνα",
                subtitle = "Σύγκριση, πορεία και σύνθεση εξόδων",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("insights_list"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = MyFinHubSpacing.xs,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                ComparisonStoryCard(
                    latest = latest,
                    previous = previous,
                    savingsRate = state.savingsRate,
                    largeFont = largeFont,
                )
            }
            item {
                MonthlyFlowChartCard(
                    points = trend,
                    averageMonthlySpend = state.averageMonthlySpend,
                )
            }
            item {
                CategoryCompositionCard(
                    categories = state.categories,
                    onOpenSupportingActivity = onOpenSupportingActivity,
                )
            }
        }
    }
}

@Composable
private fun ComparisonStoryCard(
    latest: TrendPoint?,
    previous: TrendPoint?,
    savingsRate: Int,
    largeFont: Boolean,
) {
    val expenseChange = previous?.expense?.takeIf { abs(it) > .005 }?.let { base ->
        ((latest?.expense.orZero() - base) / abs(base)) * 100.0
    }
    val latestNet = latest?.let { it.income - it.expense } ?: 0.0
    val previousNet = previous?.let { it.income - it.expense }
    val netChange = previousNet?.let { latestNet - it }
    val headline = when {
        latest == null -> "Δεν υπάρχει ακόμη αρκετό ιστορικό"
        expenseChange == null -> "${latest.label}: πρώτη βάση σύγκρισης"
        expenseChange < -0.5 -> "Τα έξοδα μειώθηκαν ${abs(expenseChange).roundToInt()}%"
        expenseChange > 0.5 -> "Τα έξοδα αυξήθηκαν ${expenseChange.roundToInt()}%"
        else -> "Τα έξοδα έμειναν σχεδόν σταθερά"
    }
    val supporting = if (latest != null && previous != null) {
        "${latest.label} σε σχέση με ${previous.label}"
    } else {
        "Η σύγκριση θα γίνει καθαρότερη όσο προστίθεται ιστορικό."
    }

    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Η αλλαγή που μετράει", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    ComparisonFact("Έξοδα τελευταίου μήνα", latest?.expense?.let(::formatEuro) ?: "—")
                    ComparisonFact("Μεταβολή καθαρής ροής", netChange?.let(::formatSignedEuro) ?: "—")
                    ComparisonFact("Ρυθμός αποταμίευσης", "$savingsRate%")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    ComparisonFact("Έξοδα", latest?.expense?.let(::formatEuro) ?: "—", Modifier.weight(1f))
                    ComparisonFact("Διαφορά ροής", netChange?.let(::formatSignedEuro) ?: "—", Modifier.weight(1f))
                    ComparisonFact("Αποταμίευση", "$savingsRate%", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ComparisonFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonthlyFlowChartCard(points: List<TrendPoint>, averageMonthlySpend: Double) {
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Πορεία 4 μηνών", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Έσοδα και έξοδα στην ίδια κλίμακα — όχι τέσσερις διαφορετικές κάρτες.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (points.isEmpty()) {
                Text("Χρειάζονται περισσότερες κινήσεις για να εμφανιστεί πορεία.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowLegend()
                FlowBars(points)
                Text(
                    "Μέσο μηνιαίο έξοδο ${formatEuro(averageMonthlySpend)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FlowLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        LegendMark("Έσοδα", financeToneColors(FinanceTone.Income).accent)
        LegendMark("Έξοδα", financeToneColors(FinanceTone.Expense).accent)
    }
}

@Composable
private fun LegendMark(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(7.dp).clip(RoundedCornerShape(999.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlowBars(points: List<TrendPoint>) {
    val maxValue = points.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > .005 } ?: 1.0
    val incomeColor = financeToneColors(FinanceTone.Income).accent
    val expenseColor = financeToneColors(FinanceTone.Expense).accent
    val description = points.joinToString(". ") { point ->
        "${point.label}: έσοδα ${formatEuro(point.income)}, έξοδα ${formatEuro(point.expense)}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            val incomeHeight = (96f * (point.income / maxValue).toFloat()).coerceAtLeast(4f).dp
            val expenseHeight = (96f * (point.expense / maxValue).toFloat()).coerceAtLeast(4f).dp
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                Row(
                    modifier = Modifier.height(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.width(12.dp).height(incomeHeight).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(incomeColor))
                    Box(Modifier.width(12.dp).height(expenseHeight).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(expenseColor))
                }
                Text(point.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
                MyFinHubAmountText(
                    text = formatSignedEuro(point.income - point.expense),
                    tone = if (point.income >= point.expense) FinanceTone.Income else FinanceTone.Expense,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CategoryCompositionCard(
    categories: List<InsightCategory>,
    onOpenSupportingActivity: () -> Unit,
) {
    val top = categories.firstOrNull()
    MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            Text("Πού πηγαίνουν τα έξοδα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (top == null) {
                Text("Δεν υπάρχουν ακόμη κατηγοριοποιημένα έξοδα για αυτόν τον μήνα.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(MyFinHubSpacing.sm)) {
                        Text("Μεγαλύτερη κατηγορία", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(top.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${formatEuro(top.amount)} · ${(top.share * 100).roundToInt()}% των κατηγοριοποιημένων εξόδων")
                    }
                }
                categories.take(5).forEach { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            MyFinHubAmountText(formatEuro(category.amount), FinanceTone.Expense)
                        }
                        LinearProgressIndicator(
                            progress = { category.share.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = financeToneColors(FinanceTone.Expense).accent,
                            trackColor = financeToneColors(FinanceTone.Expense).container,
                        )
                    }
                }
                TextButton(onClick = onOpenSupportingActivity) { Text("Δες τις κινήσεις") }
            }
        }
    }
}

private fun Double?.orZero(): Double = this ?: 0.0

private fun formatEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedEuro(value: Double): String =
    (if (value > .005) "+" else "") + formatEuro(value)
''')

# Instrumentation coverage for stable, interactive pager indicators.
test = "app/src/androidTest/java/app/myfinhub/android/feature/money/CreditCardStackTest.kt"
insert_before = "    @Test\n    fun deleteCancel_restoresNormalCardState() {"
new_test = r'''    @Test
    fun paginationDot_selectsCardByStableId() {
        var activeCardId: String? = null
        val cards = testCards(3)

        composeRule.setContent {
            MyFinHubTheme {
                CreditCardStack(
                    cards = cards,
                    secretState = CardSecretUiState.Hidden(),
                    onActiveCardChanged = { activeCardId = it },
                    onRevealSecrets = {},
                    onHideSecrets = {},
                    onOpenCard = {},
                    onDeleteCard = {},
                )
            }
        }

        composeRule.waitUntil { activeCardId == "card-a" }
        composeRule.onNodeWithTag("credit_card_dot_card-c").performClick()
        composeRule.waitUntil(timeoutMillis = TimeUnit.SECONDS.toMillis(5)) { activeCardId == "card-c" }
        composeRule.onNodeWithContentDescription("Κάρτα 3 από 3: Bonus Visa Gold, ενεργή").assertIsDisplayed()
    }

'''
replace_once(test, insert_before, new_test + insert_before)

print("Owner-feedback production patch applied.")
