package app.myfinhub.android.core.data

import java.time.LocalDate

/** Web forecastScenarios.ts account-level results, never ledger balances. */
data class CanonicalForecastScenarioAccountRiskRow(
    val accountId: String,
    val baselineProjected: Double,
    val scenarioProjected: Double,
    val scenarioDifference: Double,
    val minimum: Double,
    val firstLowDate: LocalDate?,
    val firstNegativeDate: LocalDate?,
)

/**
 * Preserve the canonical Web account ordering and compare only the same
 * account in the same 30/60/90-day horizon. Use exact cents rather than
 * floating-point subtraction; no new forecast or persisted finance event.
 */
fun CanonicalForecastScenario.canonicalScenarioAccountRiskRows():
    List<CanonicalForecastScenarioAccountRiskRow> =
    accounts.map { scenarioAccount ->
        val baselineAccount = baseline.accounts.firstOrNull {
            it.accountId == scenarioAccount.accountId
        } ?: throw IllegalArgumentException(
            "Ο λογαριασμός δεν υπάρχει στην αρχική πρόβλεψη.",
        )
        val projectedCents = moneyToCents(scenarioAccount.projected)
        val beforeCents = moneyToCents(baselineAccount.projected)
        CanonicalForecastScenarioAccountRiskRow(
            accountId = scenarioAccount.accountId,
            baselineProjected = baselineAccount.projected,
            scenarioProjected = scenarioAccount.projected,
            scenarioDifference = centsToMoney(Math.subtractExact(
                projectedCents, beforeCents,
            )),
            minimum = scenarioAccount.minimum,
            firstLowDate = scenarioAccount.firstLowDate,
            firstNegativeDate = scenarioAccount.firstNegativeDate,
        )
    }
