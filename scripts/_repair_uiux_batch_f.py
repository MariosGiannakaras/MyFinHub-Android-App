from pathlib import Path

p = Path('scripts/_uiux_batch_f.py')
t = p.read_text()
old = '''replace_once(\n    'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt',\n    ''' + "'''" + '''                            diagnostics = diagnostics,\\n                            onLogout = onLogout,'''+ "'''" + ''',\n    ''' + "'''" + '''                            diagnostics = diagnostics,\\n                            noticeHistoryCount = noticeHistory.size,\\n                            onOpenNoticeHistory = { homeBackStack.pushIfNew(AppRoute.NoticeHistory) },\\n                            onLogout = onLogout,'''+ "'''" + ''',\n    'production settings history wiring',\n)'''
new = '''replace_once(\n    'app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt',\n    ''' + "'''" + '''                        ProductionSettingsScreen(\\n                            state = frontendUtilitiesState,\\n                            onAction = onFrontendUtilitiesAction,\\n                            onBack = { homeBackStack.removeLastOrNull() },\\n                            diagnostics = diagnostics,\\n                            onLogout = onLogout,\\n                        )'''+ "'''" + ''',\n    ''' + "'''" + '''                        ProductionSettingsScreen(\\n                            state = frontendUtilitiesState,\\n                            onAction = onFrontendUtilitiesAction,\\n                            onBack = { homeBackStack.removeLastOrNull() },\\n                            diagnostics = diagnostics,\\n                            noticeHistoryCount = noticeHistory.size,\\n                            onOpenNoticeHistory = { homeBackStack.pushIfNew(AppRoute.NoticeHistory) },\\n                            onLogout = onLogout,\\n                        )'''+ "'''" + ''',\n    'production settings history wiring',\n)'''
if t.count(old) != 1:
    raise AssertionError(f'expected one batch F guard to repair, got {t.count(old)}')
t = t.replace(old, new, 1)

block_start = t.index('# 5) Destructive create->delete remains visible')
block_end = t.index('# 6) Focused tests:', block_start)
new_block = r'''# 5) Destructive create->delete remains visible and create-only remains annotated.
p = Path('app/src/main/java/app/myfinhub/android/app/PendingUiProjection.kt')
t = p.read_text()
start = t.index('private fun pendingCardChangeMessage(')
end = t.index('\nprivate fun PendingMutationSyncState.pendingStatusLabel()', start)
replacement = r"""private fun pendingCardChangeMessage(
    serverDocument: CanonicalFinanceDocument,
    @Suppress("UNUSED_PARAMETER") optimisticCards: List<app.myfinhub.android.feature.money.MoneyCard>,
    pending: List<PendingCanonicalMutationIntent>,
    today: LocalDate,
): String? {
    var replayDocument = serverDocument
    val linesByCardId = linkedMapOf<String, String>()

    for (intent in pending) {
        val before = replayDocument
        val next = runCatching { intent.asMutation().apply(before) }.getOrNull() ?: break
        when (intent.kind) {
            PendingMutationKind.CREATE_CARD -> {
                val cardId = intent.payload.string("cardId").orEmpty()
                val card = runCatching {
                    projectCanonicalProduct(next, today).moneyState.cards.firstOrNull { it.id == cardId }
                }.getOrNull()
                if (card != null) {
                    val last4 = card.last4.takeIf(String::isNotBlank)?.let { " ••••$it" }.orEmpty()
                    linesByCardId[cardId] = "${card.nickname}$last4 · Εκκρεμεί προσθήκη · ${intent.syncState.pendingStatusLabel()}"
                }
            }
            PendingMutationKind.DEACTIVATE_CARD -> {
                val cardId = intent.affectedCardId.orEmpty()
                val card = runCatching {
                    projectCanonicalProduct(before, today).moneyState.cards.firstOrNull { it.id == cardId }
                }.getOrNull()
                if (card != null) {
                    val last4 = card.last4.takeIf(String::isNotBlank)?.let { " ••••$it" }.orEmpty()
                    linesByCardId[cardId] = "${card.nickname}$last4 · Εκκρεμεί διαγραφή · ${intent.syncState.pendingStatusLabel()}"
                }
            }
            else -> Unit
        }
        replayDocument = next
    }

    if (linesByCardId.isEmpty()) return null
    return "Εκκρεμείς αλλαγές καρτών:\n${linesByCardId.values.joinToString("\n")}"
}
"""
p.write_text(t[:start] + replacement + t[end:])

'''
t = t[:block_start] + new_block + t[block_end:]
p.write_text(t)
print('batch F guards aligned with retained production settings and card-change helper')
