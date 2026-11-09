"""Copy only approved real Compose golden PNGs into stable public screenshot names.

The official renderer owns app/src/screenshotTestDebug/reference. This mirror is
byte-for-byte, not redrawn, manually Photoshopped, or backed by physical S24
screenshots. All fixture content is synthetic. No capture of owner finance data.
"""
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PNG_MAGIC = bytes.fromhex("89504e470d0a1a0a")
MIRRORS = {
    "screenshots/activity/activity-compact.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/activity/ProductionActivityScreenshotTestKt/ProductionActivityPendingLightScreenshot_production_activity_pending_light_ee7d53cf_0.png",
    "screenshots/activity/activity-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/activity/ProductionActivityScreenshotTestKt/ProductionActivityPendingLargeFontScreenshot_production_activity_pending_large_font_83e0a837_0.png",
    "screenshots/auth/locked-pin-fallback.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/auth/AuthShellScreenshotTestKt/AuthLockedPinFallbackCompactLightScreenshot_auth_locked_pin_fallback_compact_light_35ee1851_0.png",
    "screenshots/auth/login.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/auth/AuthShellScreenshotTestKt/AuthLoginCompactLightScreenshot_auth_login_compact_light_3e5c8e84_0.png",
    "screenshots/auth/pin-enrollment-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/auth/AuthShellScreenshotTestKt/AuthPinEnrollmentCompactLargeFontScreenshot_auth_pin_enrollment_compact_large_font_3464a141_0.png",
    "screenshots/home/home-attention-detail.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/home/ProductionHomeScreenshotTestKt/HomeAttentionDetailLightScreenshot_home_attention_detail_light_892f9ad3_0.png",
    "screenshots/home/home-compact.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/home/ProductionHomeScreenshotTestKt/ProductionHomeCompactLightScreenshot_production_home_compact_light_73154b5c_0.png",
    "screenshots/home/home-dark.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/home/ProductionHomeScreenshotTestKt/ProductionHomeCompactDarkScreenshot_production_home_compact_dark_564e04bd_0.png",
    "screenshots/home/home-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/home/ProductionHomeScreenshotTestKt/ProductionHomeCompactLargeFontScreenshot_production_home_compact_large_font_99387350_0.png",
    "screenshots/money/credit-card-stack-compact.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/money/CreditCardStackScreenshotTestKt/CreditCardStackCompactLightScreenshot_card_stack_compact_light_04e49ab8_0.png",
    "screenshots/money/credit-card-stack-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/money/CreditCardStackScreenshotTestKt/CreditCardStackCompactLargeFontScreenshot_card_stack_compact_150_percent_font_c9b36481_0.png",
    "screenshots/money/money-loans.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/money/CanonicalMoneyScreenshotTestKt/CanonicalLoansCompactLightScreenshot_canonical_loans_compact_light_48b79ab1_0.png",
    "screenshots/money/money-overview-dark.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/money/CanonicalMoneyScreenshotTestKt/CanonicalMoneyCompactDarkScreenshot_canonical_money_compact_dark_8ace8741_0.png",
    "screenshots/money/money-overview.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/money/CanonicalMoneyScreenshotTestKt/CanonicalMoneyCompactLightScreenshot_canonical_money_compact_light_2b156103_0.png",
    "screenshots/money/money-savings-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/money/CanonicalMoneyScreenshotTestKt/CanonicalSavingsLargeFontScreenshot_canonical_savings_large_font_38536db9_0.png",
    "screenshots/plan/plan-budgets-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/plan/S7PlanScreenshotTestKt/S7BudgetLarge_s7_budget_large_acbd1ba0_0.png",
    "screenshots/plan/plan-overview-dark.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/plan/S7PlanScreenshotTestKt/S7PlanDark_s7_plan_dark_8ab7588e_0.png",
    "screenshots/plan/plan-overview-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/plan/S7PlanScreenshotTestKt/S7PlanLarge_s7_plan_large_18fa4087_0.png",
    "screenshots/plan/plan-overview.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/plan/S7PlanScreenshotTestKt/S7PlanLight_s7_plan_light_0b794e8d_0.png",
    "screenshots/quick-entry/quick-entry-compact.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/quickentry/ProductionQuickEntryScreenshotTestKt/ProductionQuickEntryCompactLightScreenshot_production_quick_entry_compact_light_5e5d5329_0.png",
    "screenshots/quick-entry/quick-entry-dark.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/quickentry/ProductionQuickEntryScreenshotTestKt/ProductionQuickEntryCompactDarkScreenshot_production_quick_entry_compact_dark_e7f0ce82_0.png",
    "screenshots/quick-entry/quick-entry-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/quickentry/ProductionQuickEntryScreenshotTestKt/ProductionQuickEntryCompactLargeFontScreenshot_production_quick_entry_compact_large_font_f2f26a3e_0.png",
    "screenshots/quick-entry/quick-entry-split.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/quickentry/ProductionQuickEntryScreenshotTestKt/ProductionQuickEntrySplitLightScreenshot_production_quick_entry_split_light_e9879f9d_0.png",
    "screenshots/utilities/settings-compact.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/utilities/ProductionSettingsScreenshotTestKt/ProductionSettingsCompactLightScreenshot_production_settings_compact_light_e836902a_0.png",
    "screenshots/utilities/settings-dark.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/utilities/ProductionSettingsScreenshotTestKt/ProductionSettingsCompactDarkScreenshot_production_settings_compact_dark_0d316ba0_0.png",
    "screenshots/utilities/settings-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/utilities/ProductionSettingsScreenshotTestKt/ProductionSettingsCompactLargeFontScreenshot_production_settings_compact_large_font_aa5bc333_0.png",
    "screenshots/reports/reports-compact.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/insights/InsightsScreenshotTestKt/InsightsCompactLightScreenshot_insights_compact_light_0d06167b_0.png",
    "screenshots/reports/reports-dark.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/insights/InsightsScreenshotTestKt/InsightsCompactDarkScreenshot_insights_compact_dark_79a1f2a1_0.png",
    "screenshots/reports/reports-large-font.png": "app/src/screenshotTestDebug/reference/app/myfinhub/android/feature/insights/InsightsScreenshotTestKt/InsightsCompactLargeFontScreenshot_insights_compact_large_font_92696cce_0.png",
}


def verify_mirrors() -> list[str]:
    failures: list[str] = []
    for public, golden in MIRRORS.items():
        source = ROOT / golden
        target = ROOT / public
        if not source.is_file():
            failures.append(f"{public}: missing official golden {golden}")
            continue
        raw = source.read_bytes()
        if not raw.startswith(PNG_MAGIC):
            failures.append(f"{public}: invalid official PNG data")
        elif not target.is_file():
            failures.append(f"{public}: missing mirror; run --sync")
        elif target.read_bytes() != raw:
            failures.append(f"{public}: stale mirror; run --sync")
    return failures


def sync_mirrors() -> None:
    for public, golden in MIRRORS.items():
        source, target = ROOT / golden, ROOT / public
        data = source.read_bytes()
        if not data.startswith(PNG_MAGIC):
            raise ValueError(f"Invalid official golden: {golden}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sync", action="store_true", help="update public copies from official Compose goldens")
    args = parser.parse_args()
    if args.sync:
        sync_mirrors()
    errors = verify_mirrors()
    if errors:
        parser.exit(1, "\\n".join(errors) + "\\n")
    print(f"Verified {len(MIRRORS)} byte-identical Compose screenshot mirrors")
