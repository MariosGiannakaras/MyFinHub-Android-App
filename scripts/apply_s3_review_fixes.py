from pathlib import Path
import json


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, text: str) -> None:
    Path(path).write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f"Missing replacement anchor in {path}: {old[:140]!r}")
    write(path, text.replace(old, new, 1))


# Fix the only first-run androidTest compilation failure without relying on an
# unavailable Compose test assertion import.
p = "app/src/androidTest/java/app/myfinhub/android/ActivityS3NavigationTest.kt"
text = read(p)
text = text.replace("import androidx.compose.ui.test.assertDoesNotExist\n", "")
if "import org.junit.Assert.assertTrue\n" not in text:
    text = text.replace("import org.junit.Rule\n", "import org.junit.Assert.assertTrue\nimport org.junit.Rule\n")
text = text.replace(
    '        composeRule.onNodeWithText("Πορτοφόλι").assertDoesNotExist()\n',
    '        assertTrue(runCatching { composeRule.onNodeWithText("Πορτοφόλι").fetchSemanticsNode() }.isFailure)\n',
)
if "assertDoesNotExist" in text:
    raise SystemExit("ActivityS3NavigationTest still contains assertDoesNotExist")
write(p, text)


# Close read detail after either online deletion (item disappears) or an offline
# durable delete enqueue (the pending projection restores a tombstone).
p = "app/src/main/java/app/myfinhub/android/feature/activity/ActivityS3Screens.kt"
replace_once(
    p,
    "    LaunchedEffect(item, deleteRequested) {\n"
    "        if (deleteRequested && item == null) onDeleted()\n"
    "    }",
    "    LaunchedEffect(item, deleteRequested) {\n"
    "        if (deleteRequested && (item == null || item.pendingSync)) onDeleted()\n"
    "    }",
)

# The editor must also close after a confirmed offline enqueue. A pending row
# opened from the ledger remains blocked, but a pending projection produced by
# the editor's own save request is allowed to reach the success observer first.
text = read(p)
a = text.index("fun ActivityEditScreen(")
region = text[a:]
original_save_decl = "    var saveRequested by rememberSaveable(item.id) { mutableStateOf(false) }\n"
if region.count(original_save_decl) != 1:
    raise SystemExit("Unexpected ActivityEditScreen saveRequested declaration count")
region = region.replace(original_save_decl, "", 1)
old_pending = "    if (item.pendingSync) {\n"
if old_pending not in region:
    raise SystemExit("Missing ActivityEditScreen pending block")
region = region.replace(
    old_pending,
    original_save_decl + "\n    if (item.pendingSync && !saveRequested) {\n",
    1,
)
region = region.replace(
    "            mutationInFlight -> Unit\n"
    "            dirty -> discardDialogOpen = true",
    "            mutationInFlight || saveRequested -> Unit\n"
    "            dirty -> discardDialogOpen = true",
    1,
)
region = region.replace(
    "                    enabled = dirty && valid && !mutationBlocked && !saveRequested,",
    "                    enabled = dirty && valid && !mutationBlocked && !saveRequested && !item.pendingSync,",
    1,
)
region = region.replace(
    "                onValueChange = { if (!mutationInFlight && !saveRequested) date = it },",
    "                onValueChange = { if (!mutationInFlight && !saveRequested && !item.pendingSync) date = it },",
    1,
)
region = region.replace(
    "                    enabled = !mutationInFlight && !saveRequested,",
    "                    enabled = !mutationInFlight && !saveRequested && !item.pendingSync,",
    1,
)
region = region.replace(
    "                        enabled = !mutationInFlight && !saveRequested,",
    "                        enabled = !mutationInFlight && !saveRequested && !item.pendingSync,",
    1,
)
region = region.replace(
    "                enabled = !mutationInFlight && !saveRequested,",
    "                enabled = !mutationInFlight && !saveRequested && !item.pendingSync,",
    1,
)
text = text[:a] + region
write(p, text)


# Record the reviewed candidate artifact and the concrete first-run findings,
# while keeping S3 in progress until the current-head validation is green.
p = Path("tracking/android-project-state.json")
data = json.loads(p.read_text())
current = data["current_redesign_pass"]
current["branch"] = "android/redesign-s3-activity"
current["pr"] = 115
current["current_slice"] = "S3.1 / S3.2 / S3.3 / S3.4"
current["checkpoint"] = "s3_reviewed_baseline_and_concrete_fix_batch"
current["next_action"] = (
    "Validate PR #115 after the reviewed baseline/semantic fix batch; if Android CI, screenshot regression, "
    "S24-target instrumentation and Project Tracking are green, record final evidence and complete S3."
)
data["active_workstream"]["status"] = "android_redesign_s3_activity_validation"
data["active_workstream"]["summary"] = (
    "S1 and S2 are complete. PR #115 implements the S3 Activity ledger/read-detail/editor batch. "
    "The first hosted run produced 88 render candidates; the 11 intentional Activity/top-level Activity changes "
    "were personally reviewed in light/dark/150% and accepted for checkpointing. The same run exposed one "
    "androidTest-only assertion import error plus offline edit/delete route-close edge cases; these are fixed in one "
    "batch before the final hosted validation. No backend or web/desktop changes."
)
data["active_workstream"]["next"] = [
    "Run one final consolidated PR #115 validation after the reviewed baseline and concrete fixes land.",
    "Complete S3 only after Android CI, Project Tracking, screenshot regression and S24-target instrumentation all pass on the same current head.",
]
s3 = next(task for task in data["redesign_tasks"] if task["id"] == "S3")
for subtask in s3["subtasks"]:
    subtask["status"] = "in_progress"
    if subtask["id"] == "S3.3":
        subtask["evidence"].append(
            "First hosted validation review found two durable-offline navigation edge cases: an accepted offline edit becomes pending before editor exit, and an accepted offline delete is represented by a pending tombstone. The fix lets only the initiating save/delete request observe that pending projection as durable local success and return to origin; pre-existing pending rows remain non-editable/non-deletable."
        )
    if subtask["id"] == "S3.4":
        subtask["evidence"].extend([
            "Android UI Quality run 34710603625 rendered 88 candidates. Exactly 11 intentional Activity/top-level Activity references differed or were new; artifact 10303516408 (sha256 5806d169b8c0147665b8c561707089a5f17837343365a879dd38c0a544089006) was personally reviewed before baseline adoption. Light/dark and 150% ledger/category/detail/editor/filter states showed no clipping or hierarchy defect requiring redesign.",
            "The same first run compiled production and screenshot Kotlin successfully. S24 prebuild stopped only at ActivityS3NavigationTest.kt's unavailable assertDoesNotExist import; the assertion is replaced with a version-compatible semantics lookup before the final rerun.",
        ])
p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
