package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.DeactivateCanonicalCard
import app.myfinhub.android.core.data.DeleteCanonicalActivity
import app.myfinhub.android.core.data.PendingCanonicalMutationIntent
import app.myfinhub.android.core.data.PendingMutationSyncState
import app.myfinhub.android.core.data.UpsertOverallBudget
import app.myfinhub.android.core.data.canonicalFixture
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingUiProjectionTest {
    private val today = LocalDate.of(2026, 8, 23)
    private val now = "2026-08-23T12:00:00Z"

    @Test
    fun pendingDelete_restoresVisibleTombstoneAfterOptimisticRemoval() {
        val server = canonicalFixture()
        val intent = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("tx-exp", now),
            intentId = "intent-delete",
        )

        val result = projectWithPending(server, listOf(intent))
        val tombstone = result.activityState.items.first { it.id == "tx-exp" }

        assertTrue(tombstone.pendingSync)
        assertTrue(tombstone.subtitle.contains("Εκκρεμεί διαγραφή"))
    }

    @Test
    fun ambiguousDelete_usesConfirmationLanguageInsteadOfSafeLocalUndoLanguage() {
        val server = canonicalFixture()
        val intent = PendingCanonicalMutationIntent.fromMutation(
            DeleteCanonicalActivity("tx-exp", now),
            intentId = "intent-delete-review",
            syncState = PendingMutationSyncState.NEEDS_REVIEW,
        )

        val result = projectWithPending(server, listOf(intent))
        val tombstone = result.activityState.items.first { it.id == "tx-exp" }

        assertTrue(tombstone.pendingSync)
        assertTrue(tombstone.subtitle.contains("Αναμονή επιβεβαίωσης διαγραφής από τον server"))
        assertFalse(tombstone.subtitle.contains("Ακύρωση"))
    }

    @Test
    fun pendingCardDeactivation_isRemovedFromInteractiveStackButRemainsVisibleAsTombstoneMessage() {
        val server = cardFixture()
        val intent = PendingCanonicalMutationIntent.fromMutation(
            DeactivateCanonicalCard("card-credit", now),
            intentId = "intent-card-delete",
        )

        val result = projectWithPending(server, listOf(intent))
        val message = result.moneyState.frontendMessage

        assertFalse(result.moneyState.cards.any { it.id == "card-credit" })
        assertNotNull(message)
        assertTrue(message.orEmpty().contains("Πιστωτική"))
        assertTrue(message.orEmpty().contains("Εκκρεμεί διαγραφή"))
        assertTrue(message.orEmpty().contains("Προς συγχρονισμό"))
    }

    @Test
    fun pendingBudget_exposesNeedsReviewInlineState() {
        val server = canonicalFixture()
        val intent = PendingCanonicalMutationIntent.fromMutation(
            UpsertOverallBudget(
                month = "2026-08",
                amount = 900.0,
                alertThreshold = 75,
                budgetId = "budget-pending",
                nowIso = now,
            ),
            intentId = "intent-budget",
            syncState = PendingMutationSyncState.NEEDS_REVIEW,
        )

        val result = projectWithPending(server, listOf(intent))

        assertEquals(
            "Αλλαγή budget · Αναμονή επιβεβαίωσης από τον server",
            result.planState.message,
        )
    }

    @Test
    fun confirmedBudget_clearsStalePendingInlineState() {
        val server = canonicalFixture()
        val base = projectCanonicalProduct(server, today)
        val stale = base.copy(
            planState = base.planState.copy(message = "Αλλαγή budget · Προς συγχρονισμό"),
        )

        val refreshed = projectCanonicalProduct(server, today, previous = stale)
        val result = projectPendingUi(
            projection = refreshed,
            serverDocument = server,
            pending = emptyList(),
            today = today,
        )

        assertNull(result.planState.message)
    }

    @Test
    fun emptyQueue_preservesNonPendingPlanMessage() {
        val server = canonicalFixture()
        val base = projectCanonicalProduct(server, today)
        val projection = base.copy(planState = base.planState.copy(message = "Έλεγξε το μηνιαίο όριο."))

        val result = projectPendingUi(
            projection = projection,
            serverDocument = server,
            pending = emptyList(),
            today = today,
        )

        assertEquals("Έλεγξε το μηνιαίο όριο.", result.planState.message)
    }

    private fun projectWithPending(
        server: CanonicalFinanceDocument,
        pending: List<PendingCanonicalMutationIntent>,
    ): CanonicalProductProjection {
        val optimistic = pending.fold(server) { document, intent -> intent.asMutation().apply(document) }
        return projectPendingUi(
            projection = projectCanonicalProduct(optimistic, today),
            serverDocument = server,
            pending = pending,
            today = today,
        )
    }
}

private fun cardFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "app":"MyFinHub",
          "schemaVersion":3,
          "updatedAt":"2026-08-23T00:00:00Z",
          "seed":{
            "accounts":[],
            "snapshots":[],
            "transactions":[],
            "recurring":[],
            "loans":[],
            "lending":[]
          },
          "state":{
            "deleted":[],
            "overrides":{},
            "customTransactions":[],
            "settings":{},
            "events":[],
            "scheduled":[],
            "cards":[
              {
                "id":"card-credit",
                "nickname":"Πιστωτική",
                "kind":"credit",
                "network":"visa",
                "last4":"1881",
                "active":true,
                "creditLimit":2000.0
              }
            ],
            "budgets":[]
          }
        }
        """.trimIndent(),
    ).jsonObject,
)
