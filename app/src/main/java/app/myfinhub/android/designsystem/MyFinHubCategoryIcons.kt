package app.myfinhub.android.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/**
 * Stable Android-only presentation mapping for canonical category labels.
 *
 * Category taxonomy remains server/desktop owned. Android does not expose icon administration;
 * familiar category labels receive a curated local glyph and unknown labels safely fall back to
 * the transaction-kind icon supplied by the caller.
 */
fun myFinHubCategoryIcon(category: String?, fallback: ImageVector): ImageVector {
    val key = category.orEmpty().trim().lowercase(Locale.ROOT)
    if (key.isBlank()) return fallback

    return when {
        key.containsAny("τρόφι", "σούπερ", "supermarket", "grocery", "grocer") -> MyFinHubIcons.Shopping
        key.containsAny("φαγη", "εστια", "έξοδο", "καφέ", "cafe", "coffee", "restaurant", "dining") -> MyFinHubIcons.Dining
        key.containsAny("μισθ", "salary", "payroll", "εργασ") -> MyFinHubIcons.Work
        key.containsAny("αποταμι", "saving") -> MyFinHubIcons.Savings
        key.containsAny("κάρτ", "card", "credit") -> MyFinHubIcons.Card
        key.containsAny("μετακίνη", "μεταφορ", "βενζ", "καύσι", "transport", "fuel", "car") -> MyFinHubIcons.Transport
        key.containsAny("λογαριασ", "ρεύμα", "νερό", "τηλέφων", "internet", "utility", "utilities", "bill") -> MyFinHubIcons.Bills
        key.containsAny("υγε", "φαρμακ", "γιατρ", "health", "medical", "pharmacy") -> MyFinHubIcons.Health
        key.containsAny("διασκ", "ψυχαγωγ", "σινεμά", "cinema", "entertainment", "streaming") -> MyFinHubIcons.Entertainment
        key.containsAny("ενοίκ", "σπίτ", "στεγ", "rent", "home", "housing") -> MyFinHubIcons.Home
        else -> fallback
    }
}

private fun String.containsAny(vararg needles: String): Boolean = needles.any(::contains)
