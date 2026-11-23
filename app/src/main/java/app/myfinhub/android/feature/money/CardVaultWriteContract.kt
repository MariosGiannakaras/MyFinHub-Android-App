package app.myfinhub.android.feature.money

import app.myfinhub.android.core.network.CardSecretNewWriteContract
import app.myfinhub.android.core.network.CardSecretUpdate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Feature adapter for the shared Web v1.7 new-write Card Vault contract.
 * The single implementation lives in core.network; never apply these
 * write rules to encrypted legacy reads.
 */
internal object CardVaultWriteContract {
    fun normalizePan(raw: String): String? = CardSecretNewWriteContract.normalizePan(raw)

    fun normalizeCvv(raw: String): String? = CardSecretNewWriteContract.normalizeCvv(raw)

    fun normalizeExpiry(
        raw: String,
        currentMonth: YearMonth = YearMonth.now(ZoneOffset.UTC),
    ): String? = CardSecretNewWriteContract.normalizeExpiry(raw, currentMonth)

    fun isValidNewWrite(
        update: CardSecretUpdate,
        currentMonth: YearMonth = YearMonth.now(ZoneOffset.UTC),
    ): Boolean = CardSecretNewWriteContract.isValidNewWrite(update, currentMonth)
}
