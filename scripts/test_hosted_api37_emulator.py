"""Exercise the hosted boot gate with disposable mock SDK/ADB executables."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "run-hosted-api37-emulator.sh"


def executable(path, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/usr/bin/env bash\nset -eu\n" + body)
    path.chmod(0o755)


class HostedApi37EmulatorTests(unittest.TestCase):
    def test_refuses_non_hosted(self):
        result = subprocess.run(["bash", str(SCRIPT), "true"],
                                env={**os.environ, "GITHUB_ACTIONS": "false"}, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)

    def run_mock(self, api):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sdk = root / "sdk"
            executable(sdk / "cmdline-tools/latest/bin/sdkmanager", "exit 0\n")
            executable(sdk / "cmdline-tools/latest/bin/avdmanager",
                       'mkdir -p "$ANDROID_AVD_HOME/myfinhub-api37.avd"\n'
                       'printf "hw.ramSize=2048\\ndisk.dataPartition.size=2048M\\n" > "$ANDROID_AVD_HOME/myfinhub-api37.avd/config.ini"\n')
            executable(sdk / "emulator/emulator", 'exec /bin/sleep 120\n')
            executable(root / "bin/sleep", "exit 0\n")
            executable(sdk / "platform-tools/adb", '''
shift 2
case "$*" in
  "shell getprop sys.boot_completed") echo 1 ;;
  "shell getprop ro.build.version.sdk") echo "$FAKE_API" ;;
  "shell pidof system_server") echo 42 ;;
  "shell service check package")
    count=$(cat "$FAKE_ROOT/count" 2>/dev/null || echo 0)
    count=$((count+1))
    echo "$count" > "$FAKE_ROOT/count"
    if (( count < 3 )); then echo "Service package: not found"; else echo "Service package: found"; fi ;;
  "shell service check "*) echo "Service ${4}: found" ;;
  "shell df -k /data") echo "mock guest storage" ;;
  "shell input keyevent 82")
    # Two false observations + five consecutive healthy ones.
    test "$(cat "$FAKE_ROOT/count")" -ge 7
    touch "$FAKE_ROOT/ready" ;;
  "shell settings put "*) test -f "$FAKE_ROOT/ready" ;;
  "emu kill") exit 0 ;;
  *) exit 1 ;;
esac
''')
            env = {**os.environ, "GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted",
                   "ANDROID_SDK_ROOT": str(sdk), "RUNNER_TEMP": str(root), "FAKE_ROOT": str(root),
                   "FAKE_API": str(api), "PATH": str(root / "bin") + os.pathsep + os.environ["PATH"]}
            result = subprocess.run(["bash", str(SCRIPT), "bash", "-c", 'test -f "$FAKE_ROOT/ready"; touch "$FAKE_ROOT/ran"'],
                                    env=env, capture_output=True, text=True, timeout=30)
            return result, (root / "ran").exists()

    def test_waits_for_services_before_test_execution(self):
        result, ran = self.run_mock(37)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(ran)

    def test_wrong_runtime_api_never_runs_tests(self):
        result, ran = self.run_mock(35)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(ran)


if __name__ == "__main__":
    unittest.main()
