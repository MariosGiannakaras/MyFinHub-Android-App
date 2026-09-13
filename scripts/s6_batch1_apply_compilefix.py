from pathlib import Path

source = Path(__file__).with_name("s6_batch1_apply_relaxed.py").read_text()
exec(compile(source, "s6_batch1_apply_relaxed.py", "exec"))

path = Path("app/src/androidTest/java/app/myfinhub/android/feature/money/S6CardSurfaceTest.kt")
text = path.read_text()
text = text.replace("import androidx.compose.ui.test.onNode\n", "")
path.write_text(text)
