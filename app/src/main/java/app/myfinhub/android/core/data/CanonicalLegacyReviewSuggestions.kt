package app.myfinhub.android.core.data

import java.time.Instant
import java.util.Locale

/**
 * Web domain.ts reviewSuggestions, as a conservative read-only queue.
 * The original legacy ID is a referent only; never produce reviewDecision
 * writes, mutate legacy overrides or present a classification as confirmed.
 */
data class CanonicalLegacyReviewSuggestion(
    val transactionId: String,
    val dateIso: String,
    val semanticKind: String,
    val confidence: String,
    val reason: String,
)

private val webLegacyMoneyMention = Regex("""(?:^|[^\d])(\d{1,4}(?:[.,]\d{1,2})?)\s*€""")
private val webLegacyMixedLine = Regex("""\n\s*\+|\n.+:""")
private val webLegacyCorrection = Regex("""ΔΙ[ΟΌ]ΡΘΩΣ""")
private val legacyReviewPriority = mapOf("high" to 0, "medium" to 1, "low" to 2)

fun CanonicalFinanceDocument.canonicalLegacyReviewSuggestions(
    nowIso: String = Instant.now().toString(),
): List<CanonicalLegacyReviewSuggestion> {
    val decisions = state.obj("reviewDecisions")
    val locale = Locale.forLanguageTag("el-GR")
    val candidates = effectiveLegacyTransactions().mapNotNull { tx ->
        val decision = decisions[tx.id] as? kotlinx.serialization.json.JsonObject
        val status = decision?.string("status")
        if (status == "confirmed" || status == "kept") return@mapNotNull null
        if (status == "snoozed" &&
            decision?.string("snoozedUntil")?.let { it > nowIso } == true
        ) return@mapNotNull null
        val note = tx.note.trim()
        val upper = note.uppercase(locale)
        val mixed = webLegacyMoneyMention.findAll(note).count() >= 2 ||
            webLegacyMixedLine.containsMatchIn(note)
        fun row(
            semanticKind: String,
            confidence: String,
            reason: String,
        ) = CanonicalLegacyReviewSuggestion(
            tx.id, tx.date, semanticKind, confidence, reason,
        )
        when {
            "PAY&SAVE" in upper -> row(
                "saving_cash_offset", "high",
                "Το Pay&Save είναι αποταμίευση/εσωτερική κίνηση, όχι νέο εισόδημα ή έξοδο.",
            )
            "ΑΠΟΤΑΜΙΕΥΣ" in upper -> row(
                if (mixed) "split_required" else "saving_cash_offset",
                if (mixed) "medium" else "high",
                if (mixed) "Το σχόλιο περιέχει αποταμίευση μαζί με άλλο οικονομικό γεγονός και χρειάζεται split."
                else "Η κίνηση περιγράφεται ρητά ως αποταμίευση.",
            )
            "ΑΝΑΛΗΨΗ ΑΠΟ" in upper -> row(
                if (mixed) "split_required" else "withdrawal",
                if (mixed) "medium" else "high",
                if (mixed) "Ανάληψη και πραγματικά έξοδα εμφανίζονται στην ίδια εγγραφή."
                else "Η ανάληψη είναι μεταφορά τραπεζικού υπολοίπου σε μετρητά, όχι expense.",
            )
            "ΠΛΗΡΩΜΗ ΠΙΣΤΩΤΙΚΗΣ" in upper -> row(
                "card_payment", "medium",
                "Η εξόφληση πιστωτικής συνήθως είναι liability transfer. Απαιτεί έλεγχο επειδή το legacy ιστορικό ίσως περιέχει τις αγορές μέσα στο ίδιο σχόλιο.",
            )
            webLegacyCorrection.containsMatchIn(upper) -> row(
                if (mixed) "split_required" else "reconciliation",
                if (mixed) "medium" else "high",
                if (mixed) "Η διόρθωση είναι αναμεμειγμένη με πραγματική αγορά."
                else "Η καθαρή διόρθωση υπολοίπου πρέπει να εξαιρεθεί από spending.",
            )
            "ΕΠΙΣΤΡΟΦ" in upper -> row(
                if (mixed) "split_required" else "refund", "medium",
                if (mixed) "Η επιστροφή είναι μαζί με άλλο έσοδο/έξοδο."
                else "Η επιστροφή αγοράς πρέπει να μειώνει spending αντί να εμφανίζεται ως κανονικό income.",
            )
            "IRIS" in upper -> row(
                "iris_context", "low",
                "Το IRIS μπορεί να είναι πληρωμή, επιστροφή, δανεικό ή ανταλλαγή μετρητών και χρειάζεται ανθρώπινη επιβεβαίωση.",
            )
            else -> null
        }
    }
    return candidates.sortedWith(compareBy {
        legacyReviewPriority[it.confidence] ?: 3
    })
}
