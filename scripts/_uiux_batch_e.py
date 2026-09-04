from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text()
    if text.count(old) != 1:
        raise AssertionError(f"{label}: expected 1 match, got {text.count(old)}")
    p.write_text(text.replace(old, new, 1))

# 1) Canonical create-card mutation: metadata only, never PAN/expiry/CVV.
p = Path('app/src/main/java/app/myfinhub/android/core/data/CanonicalCardMutations.kt')
t = p.read_text()
marker = '''/**
 * Deactivates one canonical card by stable ID while retaining its historical record.
'''
create = r'''/** Canonical card creation containing safe display/accounting metadata only. */
data class CreateCanonicalCard(
    val cardId: String,
    val bankId: String,
    val nickname: String,
    val kind: String,
    val network: String,
    val formFactor: String,
    val last4: String?,
    val creditLimit: Double?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Προσθήκη κάρτας"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val normalizedId = cardId.trim()
        val normalizedBankId = bankId.trim()
        val normalizedNickname = nickname.trim()
        val normalizedKind = kind.trim().lowercase()
        val normalizedNetwork = network.trim().lowercase()
        val normalizedFormFactor = formFactor.trim().lowercase()
        val normalizedLast4 = last4?.trim()?.takeIf(String::isNotBlank)

        require(normalizedId.isNotBlank()) { "Απαιτείται αναγνωριστικό κάρτας." }
        require(normalizedBankId.isNotBlank() && normalizedBankId.length <= 100) { "Συμπλήρωσε έγκυρο εκδότη κάρτας." }
        require(normalizedNickname.isNotBlank() && normalizedNickname.length <= 500) { "Συμπλήρωσε όνομα κάρτας." }
        require(normalizedKind in setOf("debit", "prepaid", "credit")) { "Ο τύπος κάρτας δεν είναι έγκυρος." }
        require(normalizedNetwork in setOf("visa", "mastercard", "other")) { "Το δίκτυο κάρτας δεν είναι έγκυρο." }
        require(normalizedFormFactor in setOf("physical", "virtual")) { "Η μορφή κάρτας δεν είναι έγκυρη." }
        require(normalizedLast4 == null || Regex("^\\d{4}$").matches(normalizedLast4)) { "Τα τελευταία 4 ψηφία πρέπει να είναι ακριβώς τέσσερα ψηφία." }
        if (creditLimit != null) {
            require(normalizedKind == "credit") { "Πιστωτικό όριο επιτρέπεται μόνο σε πιστωτική κάρτα." }
            require(creditLimit.isFinite() && creditLimit > 0.0) { "Το πιστωτικό όριο πρέπει να είναι μεγαλύτερο από μηδέν." }
        }

        val cards = document.state.array("cards")
        val existing = cards.mapNotNull { it as? JsonObject }.firstOrNull { it.string("id") == normalizedId }
        if (existing != null) {
            val same = existing.string("bankId") == normalizedBankId &&
                existing.string("nickname") == normalizedNickname &&
                existing.string("kind") == normalizedKind &&
                existing.string("network") == normalizedNetwork &&
                existing.string("formFactor").orEmpty().ifBlank { "physical" } == normalizedFormFactor &&
                existing.string("last4") == normalizedLast4 &&
                existing.bool("active") != false
            require(same) { "Υπάρχει ήδη διαφορετική κάρτα με το ίδιο αναγνωριστικό." }
            return document
        }

        val card = JsonObject(buildMap {
            put("id", JsonPrimitive(normalizedId))
            put("bankId", JsonPrimitive(normalizedBankId))
            put("nickname", JsonPrimitive(normalizedNickname))
            put("kind", JsonPrimitive(normalizedKind))
            put("network", JsonPrimitive(normalizedNetwork))
            put("formFactor", JsonPrimitive(normalizedFormFactor))
            normalizedLast4?.let { put("last4", JsonPrimitive(it)) }
            creditLimit?.let { put("creditLimit", JsonPrimitive(it)) }
            put("active", JsonPrimitive(true))
            put("createdAt", JsonPrimitive(nowIso))
            put("updatedAt", JsonPrimitive(nowIso))
        })
        val updatedState = document.state.updated("cards", JsonArray(cards + card))
        return CanonicalFinanceDocument(
            document.raw
                .updated("state", updatedState)
                .updated("updatedAt", JsonPrimitive(nowIso)),
        )
    }
}

'''
if t.count(marker) != 1:
    raise AssertionError('create card mutation insertion guard')
p.write_text(t.replace(marker, create + marker, 1))

# 2) Durable pending semantics for create card.
p = Path('app/src/main/java/app/myfinhub/android/core/data/PendingCanonicalMutation.kt')
t = p.read_text()
t = t.replace('''    UPSERT_OVERALL_BUDGET,\n    DEACTIVATE_CARD,''', '''    UPSERT_OVERALL_BUDGET,\n    CREATE_CARD,\n    DEACTIVATE_CARD,''')
t = t.replace('''            PendingMutationKind.UPSERT_OVERALL_BUDGET -> UpsertOverallBudget(''', '''            PendingMutationKind.CREATE_CARD -> CreateCanonicalCard(
            cardId = payload.string("cardId").orEmpty(),
            bankId = payload.string("bankId").orEmpty(),
            nickname = payload.string("nickname").orEmpty(),
            kind = payload.string("cardKind").orEmpty(),
            network = payload.string("network").orEmpty(),
            formFactor = payload.string("formFactor").orEmpty(),
            last4 = payload.string("last4"),
            creditLimit = payload.number("creditLimit"),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.UPSERT_OVERALL_BUDGET -> UpsertOverallBudget(''')
t = t.replace('''        PendingMutationKind.DEACTIVATE_CARD -> {\n            val cardId = payload.string("cardId").orEmpty()''', '''        PendingMutationKind.CREATE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()
            val card = document.state.array("cards")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("id") == cardId }
            cardId.isNotBlank() && card != null &&
                card.string("bankId") == payload.string("bankId") &&
                card.string("nickname") == payload.string("nickname") &&
                card.string("kind") == payload.string("cardKind") &&
                card.string("network") == payload.string("network") &&
                card.bool("active") != false
        }
        PendingMutationKind.DEACTIVATE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()''')
needle = '''            is DeactivateCanonicalCard -> PendingCanonicalMutationIntent(\n                intentId = intentId,'''
insert = '''            is CreateCanonicalCard -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.CREATE_CARD,
                payload = JsonObject(buildMap {
                    put("cardId", JsonPrimitive(mutation.cardId))
                    put("bankId", JsonPrimitive(mutation.bankId))
                    put("nickname", JsonPrimitive(mutation.nickname))
                    put("cardKind", JsonPrimitive(mutation.kind))
                    put("network", JsonPrimitive(mutation.network))
                    put("formFactor", JsonPrimitive(mutation.formFactor))
                    mutation.last4?.let { put("last4", JsonPrimitive(it)) }
                    mutation.creditLimit?.let { put("creditLimit", JsonPrimitive(it)) }
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                }),
                syncState = syncState,
            )
            is DeactivateCanonicalCard -> PendingCanonicalMutationIntent(
                intentId = intentId,'''
if t.count(needle) != 1:
    raise AssertionError('pending create serialization guard')
t = t.replace(needle, insert, 1)
# Safe local create -> deactivate cancellation, but never remove ambiguous CREATE_CARD.
needle = '''    if (next.kind == PendingMutationKind.DEACTIVATE_CARD) {\n        val cardId = next.affectedCardId'''
insert = '''    if (next.kind == PendingMutationKind.DEACTIVATE_CARD) {
        val cardId = next.affectedCardId
        val neverSentCreate = current.lastOrNull {
            it.kind == PendingMutationKind.CREATE_CARD &&
                it.payload.string("cardId") == cardId &&
                it.syncState == PendingMutationSyncState.NEVER_SENT
        }
        if (neverSentCreate != null) {
            return current.filterNot { it.intentId == neverSentCreate.intentId }
        }'''
if t.count(needle) != 1:
    raise AssertionError('pending create deactivate compaction guard')
t = t.replace(needle, insert, 1)
p.write_text(t)

# 3) Safe UI request + create screen.
Path('app/src/main/java/app/myfinhub/android/feature/money/CanonicalCardCreateScreen.kt').write_text(r'''package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
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

@Composable
fun CanonicalCardCreateScreen(
    cards: List<MoneyCard>,
    onCreate: (CardCreateRequest) -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var bankId by remember { mutableStateOf("") }
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
                title = "Προσθήκη κάρτας",
                subtitle = "Μόνο ασφαλή στοιχεία κάρτας",
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
                    "Δεν αποθηκεύονται εδώ πλήρης αριθμός κάρτας, ημερομηνία λήξης ή CVV.",
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
            OutlinedTextField(
                value = bankId,
                onValueChange = { bankId = it; validation = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Τράπεζα / εκδότης") },
                singleLine = true,
                enabled = submittedId == null,
            )
            Text("Τύπος", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                listOf("debit" to "Χρεωστική", "prepaid" to "Προπληρωμένη", "credit" to "Πιστωτική").forEach { (value, label) ->
                    FilterChip(selected = kind == value, onClick = { kind = value; validation = null }, label = { Text(label) }, enabled = submittedId == null)
                }
            }
            Text("Δίκτυο", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                listOf("visa" to "Visa", "mastercard" to "Mastercard", "other" to "Άλλο").forEach { (value, label) ->
                    FilterChip(selected = network == value, onClick = { network = value }, label = { Text(label) }, enabled = submittedId == null)
                }
            }
            Text("Μορφή", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                listOf("physical" to "Φυσική", "virtual" to "Virtual").forEach { (value, label) ->
                    FilterChip(selected = formFactor == value, onClick = { formFactor = value }, label = { Text(label) }, enabled = submittedId == null)
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
                label = if (submittedId == null) "Αποθήκευση κάρτας" else "Αποθήκευση στη συσκευή…",
                onClick = {
                    val normalizedNickname = nickname.trim()
                    val normalizedBank = bankId.trim()
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

# 4) Route + Money entry point.
replace_once(
    'app/src/main/java/app/myfinhub/android/app/AppRoute.kt',
    '    @Serializable data class CardDetail(val cardId: String) : AppRoute\n',
    '    @Serializable data object CardCreate : AppRoute\n    @Serializable data class CardDetail(val cardId: String) : AppRoute\n',
    'card create route',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt',
    '    onOpenCard: (String) -> Unit,\n    onOpenAccount: (String) -> Unit = {},\n',
    '    onOpenCard: (String) -> Unit,\n    onAddCard: () -> Unit = {},\n    onOpenAccount: (String) -> Unit = {},\n',
    'money add callback',
)
replace_once(
    'app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt',
    '''            item {\n                MyFinHubSectionHeading(\n                    title = "Κάρτες",\n                    subtitle = "Ασφαλής πρόσβαση μόνο στα πραγματικά στοιχεία",\n                    icon = MyFinHubIcons.Card,\n                    tone = FinanceTone.Transfer,\n                )\n            }\n''',
    '''            item {\n                MyFinHubSectionHeading(\n                    title = "Κάρτες",\n                    subtitle = "Ασφαλής πρόσβαση μόνο στα πραγματικά στοιχεία",\n                    icon = MyFinHubIcons.Card,\n                    tone = FinanceTone.Transfer,\n                )\n                TextButton(onClick = onAddCard) { Text("Προσθήκη κάρτας") }\n            }\n''',
    'money add action',
)

# 5) ViewModel creation callback.
p = Path('app/src/main/java/app/myfinhub/android/app/FinanceProductViewModel.kt')
t = p.read_text()
t = t.replace('import app.myfinhub.android.core.data.DeactivateCanonicalCard\n', 'import app.myfinhub.android.core.data.CreateCanonicalCard\nimport app.myfinhub.android.core.data.DeactivateCanonicalCard\n')
t = t.replace('import app.myfinhub.android.feature.home.reduceHomeState\n', 'import app.myfinhub.android.feature.home.reduceHomeState\nimport app.myfinhub.android.feature.money.CardCreateRequest\n')
needle = '''    fun deleteCard(cardId: String) {\n'''
method = '''    fun createCard(request: CardCreateRequest) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        if (ready.projection.document.canonicalCards().any { it.id == request.cardId }) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η κάρτα δεν μπόρεσε να προστεθεί με ασφάλεια.",
                    details = "Ενέργεια: Προσθήκη κάρτας\\nΚατηγορία: DUPLICATE_CARD_ID",
                    diagnosticCode = "MFH-APP-DUPLICATE-CARD-ID",
                ),
            )
            return
        }
        applyMutation(
            CreateCanonicalCard(
                cardId = request.cardId,
                bankId = request.bankId,
                nickname = request.nickname,
                kind = request.kind,
                network = request.network,
                formFactor = request.formFactor,
                last4 = request.last4,
                creditLimit = request.creditLimit,
                nowIso = Instant.now().toString(),
            ),
        )
    }

    fun deleteCard(cardId: String) {
'''
if t.count(needle) != 1:
    raise AssertionError('viewmodel create card insertion guard')
t = t.replace(needle, method, 1)
t = t.replace('''                PendingMutationKind.UPSERT_OVERALL_BUDGET -> "Αλλαγή budget"\n                PendingMutationKind.DEACTIVATE_CARD -> "Διαγραφή κάρτας"''', '''                PendingMutationKind.UPSERT_OVERALL_BUDGET -> "Αλλαγή budget"\n                PendingMutationKind.CREATE_CARD -> "Προσθήκη κάρτας"\n                PendingMutationKind.DEACTIVATE_CARD -> "Διαγραφή κάρτας"''')
p.write_text(t)

# 6) App navigation and callback plumbing.
p = Path('app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt')
t = p.read_text()
t = t.replace('import app.myfinhub.android.feature.money.CanonicalCardDetailScreen\n', 'import app.myfinhub.android.feature.money.CanonicalCardCreateScreen\nimport app.myfinhub.android.feature.money.CanonicalCardDetailScreen\nimport app.myfinhub.android.feature.money.CardCreateRequest\n')
t = t.replace('''    onDeleteCard: (String) -> Unit = {},\n    planState: PlanUiState = PlanUiState(),''', '''    onDeleteCard: (String) -> Unit = {},\n    onCreateCard: (CardCreateRequest) -> Unit = {},\n    planState: PlanUiState = PlanUiState(),''')
t = t.replace('''                            onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },\n                            onOpenAccount = { accountId -> moneyBackStack.pushIfNew(AppRoute.AccountDetail(accountId)) },''', '''                            onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },\n                            onAddCard = { moneyBackStack.pushIfNew(AppRoute.CardCreate) },\n                            onOpenAccount = { accountId -> moneyBackStack.pushIfNew(AppRoute.AccountDetail(accountId)) },''', 1)
marker = '                entry<AppRoute.CardDetail> { route ->\n'
route = '''                entry<AppRoute.CardCreate> {
                    CanonicalCardCreateScreen(
                        cards = moneyState.cards,
                        onCreate = onCreateCard,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
'''
if t.count(marker) != 1:
    raise AssertionError('card create route wiring guard')
t = t.replace(marker, route + marker, 1)
p.write_text(t)

# 7) Root passes real canonical mutation callback.
p = Path('app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt')
t = p.read_text()
t = t.replace('import app.myfinhub.android.feature.money.CardSecretUiState\n', 'import app.myfinhub.android.feature.money.CardCreateRequest\nimport app.myfinhub.android.feature.money.CardSecretUiState\n')
t = t.replace('''                            onDeleteCard = financeViewModel::deleteCard,\n                            diagnostics = diagnostics,''', '''                            onDeleteCard = financeViewModel::deleteCard,\n                            onCreateCard = financeViewModel::createCard,\n                            diagnostics = diagnostics,''')
t = t.replace('''    onDeleteCard: (String) -> Unit,\n    diagnostics: AppDiagnosticsSnapshot,''', '''    onDeleteCard: (String) -> Unit,\n    onCreateCard: (CardCreateRequest) -> Unit,\n    diagnostics: AppDiagnosticsSnapshot,''')
t = t.replace('''                    onDeleteCard = onDeleteCard,\n                    planState = projection.planState,''', '''                    onDeleteCard = onDeleteCard,\n                    onCreateCard = onCreateCard,\n                    planState = projection.planState,''')
p.write_text(t)

# 8) Pending UI visibly represents create and delete card work.
p = Path('app/src/main/java/app/myfinhub/android/app/PendingUiProjection.kt')
t = p.read_text()
t = t.replace('''    val cardMessage = serverDocument?.let { pendingCardDeletionMessage(it, pending, today) }''', '''    val cardMessage = serverDocument?.let {
        pendingCardChangeMessage(it, projection.moneyState.cards, pending, today)
    }''')
t = t.replace('''private fun pendingCardDeletionMessage(\n    serverDocument: CanonicalFinanceDocument,\n    pending: List<PendingCanonicalMutationIntent>,\n    today: LocalDate,\n): String? {''', '''private fun pendingCardChangeMessage(
    serverDocument: CanonicalFinanceDocument,
    optimisticCards: List<app.myfinhub.android.feature.money.MoneyCard>,
    pending: List<PendingCanonicalMutationIntent>,
    today: LocalDate,
): String? {
    val createLines = pending.filter { it.kind == PendingMutationKind.CREATE_CARD }.mapNotNull { intent ->
        val id = intent.payload.string("cardId") ?: return@mapNotNull null
        val card = optimisticCards.firstOrNull { it.id == id } ?: return@mapNotNull null
        "${card.nickname} · Εκκρεμεί προσθήκη · ${intent.syncState.pendingStatusLabel()}"
    }
''')
# Rename prior return composition to combine both kinds.
t = t.replace('''    if (lines.isEmpty()) return null\n\n    return "Εκκρεμείς διαγραφές καρτών:\\n${lines.joinToString("\\n")}"\n}''', '''    val allLines = createLines + lines
    if (allLines.isEmpty()) return null

    return "Εκκρεμείς αλλαγές καρτών:\\n${allLines.joinToString("\\n")}"
}''')
p.write_text(t)

# 9) Tests: creation contract + pending durability/causal semantics.
p = Path('app/src/test/java/app/myfinhub/android/core/data/CanonicalCardMutationsTest.kt')
t = p.read_text()
insert = r'''
    @Test
    fun createCard_addsOnlySafeCanonicalMetadataAndIsIdempotent() {
        val mutation = CreateCanonicalCard(
            cardId = "card-new",
            bankId = "issuer",
            nickname = "Καθημερινή",
            kind = "credit",
            network = "visa",
            formFactor = "physical",
            last4 = "1234",
            creditLimit = 1500.0,
            nowIso = "2026-09-04T20:00:00Z",
        )
        val once = mutation.apply(canonicalFixture())
        val twice = mutation.apply(once)
        val card = twice.state.array("cards").mapNotNull { it as? JsonObject }.single { it.string("id") == "card-new" }

        assertEquals("issuer", card.string("bankId"))
        assertEquals("Καθημερινή", card.string("nickname"))
        assertEquals("credit", card.string("kind"))
        assertEquals("visa", card.string("network"))
        assertEquals("1234", card.string("last4"))
        assertTrue(card.bool("active") == true)
        assertFalse(card.keys.any { key ->
            key.lowercase() in setOf("pan", "cardnumber", "expiry", "expirydate", "cvv", "cvc", "securitycode")
        })
        assertEquals(1, twice.state.array("cards").count { (it as? JsonObject)?.string("id") == "card-new" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun createCard_rejectsMalformedLast4() {
        CreateCanonicalCard("new", "issuer", "Name", "debit", "visa", "physical", "12x4", null, "2026-09-04T20:00:00Z")
            .apply(canonicalFixture())
    }
'''
idx = t.rfind('\n}')
if idx < 0: raise AssertionError('card test class guard')
t = t[:idx] + insert + t[idx:]
p.write_text(t)

p = Path('app/src/test/java/app/myfinhub/android/core/data/OfflinePendingMutationSemanticsTest.kt')
t = p.read_text()
t = t.replace('''            UpsertOverallBudget("2026-09", 420.50, 73, "budget-new", NOW),\n            DeactivateCanonicalCard("card-1", NOW),''', '''            UpsertOverallBudget("2026-09", 420.50, 73, "budget-new", NOW),\n            CreateCanonicalCard("card-new", "issuer", "New", "debit", "visa", "physical", "1234", null, NOW),\n            DeactivateCanonicalCard("card-1", NOW),''')
insert = r'''
    @Test
    fun neverSentCardCreateThenDeactivate_compactsToNothing() {
        val create = PendingCanonicalMutationIntent.fromMutation(
            CreateCanonicalCard("card-local", "issuer", "Local", "debit", "visa", "physical", null, null, NOW),
            "create-card",
        )
        val deactivate = PendingCanonicalMutationIntent.fromMutation(
            DeactivateCanonicalCard("card-local", NOW),
            "delete-card",
        )
        val afterCreate = compactPendingMutationIntents(emptyList(), create)
        val afterDelete = compactPendingMutationIntents(afterCreate, deactivate)
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun ambiguousCardCreate_isNeverCompactedByLaterDeactivate() {
        val create = PendingCanonicalMutationIntent.fromMutation(
            CreateCanonicalCard("card-ambiguous", "issuer", "Ambiguous", "debit", "visa", "physical", null, null, NOW),
            "create-card",
            PendingMutationSyncState.NEEDS_REVIEW,
        )
        val deactivate = PendingCanonicalMutationIntent.fromMutation(
            DeactivateCanonicalCard("card-ambiguous", NOW),
            "delete-card",
        )
        val queue = compactPendingMutationIntents(listOf(create), deactivate)
        assertEquals(listOf("create-card", "delete-card"), queue.map { it.intentId })
    }

'''
needle = '    @Test\n    fun deleteIsSatisfiedWhenTransactionIsAlreadyAbsent() {'
if t.count(needle) != 1: raise AssertionError('offline test insertion guard')
t = t.replace(needle, insert + needle, 1)
p.write_text(t)

print('batch E transformations applied')
