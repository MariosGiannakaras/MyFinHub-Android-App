from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))

money = "app/src/main/java/app/myfinhub/android/feature/money/CanonicalMoneyScreens.kt"
replace_once(
    money,
    "import androidx.compose.ui.text.font.FontWeight\n",
    "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.dp\n",
)

for path in [
    "app/src/main/java/app/myfinhub/android/feature/money/Money2026Screens.kt",
    "app/src/main/java/app/myfinhub/android/feature/money/MoneyScreen.kt",
]:
    replace_once(
        path,
        "        is CardSecretUiState.Loading -> secretState.takeIf { it.cardId == card?.id }\n        is CardSecretUiState.Revealed ->",
        "        is CardSecretUiState.Loading -> secretState.takeIf { it.cardId == card?.id }\n        is CardSecretUiState.Saving -> secretState.takeIf { it.cardId == card?.id }\n        is CardSecretUiState.Revealed ->",
    )
    replace_once(
        path,
        "                    is CardSecretUiState.Loading -> {\n                        CircularProgressIndicator()\n                        Text(\"Ανάκτηση ασφαλών στοιχείων…\")\n                    }\n",
        "                    is CardSecretUiState.Loading -> {\n                        CircularProgressIndicator()\n                        Text(\"Ανάκτηση ασφαλών στοιχείων…\")\n                    }\n\n                    is CardSecretUiState.Saving -> {\n                        CircularProgressIndicator()\n                        Text(\"Αποθήκευση ασφαλών στοιχείων…\")\n                    }\n",
    )

print("rc6 compile fix applied")
