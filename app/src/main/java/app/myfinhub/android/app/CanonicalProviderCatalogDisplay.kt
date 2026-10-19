package app.myfinhub.android.app

import app.myfinhub.android.core.network.FinancialProviderRecord

/**
 * Resolve the owner AAL2 server catalog's **display name** by exact canonical
 * providerId. It is a label only; Web/Android canonical account IDs and
 * verified drawable marks remain unchanged. Unknown/missing IDs fall back
 * without impersonating an unrelated bank or leaking provider asset URLs.
 */
internal fun canonicalCatalogProviderName(
    providerId: String?,
    existingInstitution: String?,
    catalog: List<FinancialProviderRecord>,
): String? {
    val id=providerId?.trim()?.takeIf(String::isNotBlank) ?: return existingInstitution
    return catalog.firstOrNull { it.id == id }?.displayName ?: existingInstitution
}
