#!/usr/bin/env python3
"""Run the opt-in Android bearer proof only against a fresh hosted CI loopback stack."""
from __future__ import annotations

import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

# Exact Web PR #668 candidate; do not treat the isolated proof as deployment.
WEB_SHA = "d25cf3d3774ab084c8256571f323747af10c048f"
CLI = ["npx", "--yes", "supabase@2.119.0"]
SUPABASE = "http://127.0.0.1:54321"
WEB = "http://127.0.0.1:4317"
TEST = "app.myfinhub.android.core.network.SharedAppLockRealStackTest"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())


def local_status(raw: str) -> tuple[str, str]:
    d = json.loads(raw)
    fields: dict[str, set[str]] = {}

    def visit(value):
        if isinstance(value, dict):
            for name, item in value.items():
                if isinstance(item, str):
                    normalized = "".join(c for c in name.upper() if c.isalnum())
                    fields.setdefault(normalized, set()).add(item)
                else:
                    visit(item)

    def unique(*names):
        values = set().union(*(fields.get(name, set()) for name in names))
        if len(values) > 1:
            raise RuntimeError("Ambiguous disposable local status")
        return next(iter(values), None)

    visit(d)
    if unique("APIURL") != SUPABASE:
        raise RuntimeError("Refusing any noncanonical loopback Supabase endpoint")
    publishable = unique("PUBLISHABLEKEY") or unique("ANONKEY")
    service = unique("SERVICEROLEKEY")
    if not isinstance(publishable, str) or len(publishable) < 20:
        raise RuntimeError("Missing disposable publishable key")
    if not isinstance(service, str) or len(service) < 20 or publishable == service:
        raise RuntimeError("Missing distinct disposable service role")
    return publishable, service


def fixture_request(path: str, service: str, data: dict) -> dict:
    if path not in ("/auth/v1/admin/users", "/rest/v1/rheomiq_owner"):
        raise RuntimeError("Unexpected fixture provisioning route")
    req = urllib.request.Request(
        SUPABASE + path, data=json.dumps(data).encode(), method="POST",
        headers={
            "Authorization": "Bearer " + service, "apikey": service,
            "Content-Type": "application/json", "Prefer": "return=representation",
        },
    )
    try:
        with OPENER.open(req, timeout=15) as res:
            body = res.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"Isolated fixture provisioning failed: HTTP {exc.code}") from None


def require_test_executed(report: Path) -> None:
    root = ET.parse(report).getroot()
    cases = root.findall("testcase")
    if len(cases) != 1 or any(c.find(tag) is not None for c in cases for tag in ("skipped", "failure", "error")):
        raise RuntimeError("The isolated native test did not execute successfully")
    if cases[0].get("name") != "nativeBearerLockAndRecoveryAgainstRealLocalStack":
        raise RuntimeError("Unexpected isolated test case")


def safe_transport_diagnostics(report: Path) -> list[str]:
    try:
        root = ET.parse(report).getroot()
        # Gradle stores test stdout in this element. Only our fixed categories can leave it.
        return re.findall(r"(?m)^ISOLATED_HTTP_TRANSPORT=(TIMEOUT|EOF|PROTOCOL|SOCKET|OTHER_IO)$",
                          root.findtext("system-out", ""))[-5:]
    except (OSError, ET.ParseError):
        return []


def safe_test_failure(report: Path) -> str | None:
    """Emit only approved stage labels, typed failure, HTTP code and source lines.

    Never emit raw JUnit exception text, upstream bodies, stdout or fixture values.
    """
    try:
        cases = ET.parse(report).getroot().findall("testcase")
        if len(cases) != 1 or cases[0].get("name") != "nativeBearerLockAndRecoveryAgainstRealLocalStack":
            return None
        fail = cases[0].find("failure")
        if fail is None:
            return None
        msg = fail.get("message", "")
        match = re.fullmatch(
            r"(?:java\.lang\.IllegalStateException: )?([A-Za-z0-9 -]{1,80}): "
            r"(auth failed|lock request failed|finance request failed) "
            r"\(([A-Z_]+|null)(?:, HTTP ([1-5][0-9]{2}|null))?(?:, ([A-Z_]+|null))?\)", msg)
        # All stage strings passed to these helpers are literals in the checked-in test.
        test_source = (Path(__file__).resolve().parent.parent /
                       "app/src/test/java/app/myfinhub/android/core/network/SharedAppLockRealStackTest.kt").read_text()
        labels = set(re.findall(r'(?:authSuccess|lockSuccess|apiSuccess)\("([A-Za-z0-9 -]{1,80})"', test_source))
        lines = re.findall(r"SharedAppLockRealStackTest\.kt:([0-9]{1,4})", fail.text or "")[:5]
        where = ", ".join(lines) or "unknown"
        if match and match.group(1) in labels:
            stage, kind, reason, status, _ = match.groups()
            return f"Isolated native test stage: {stage}; {kind}: {reason}; HTTP {status or 'n/a'}; source lines {where}"
        return f"Isolated native test failed; source lines {where}"
    except (OSError, ET.ParseError):
        return None


def main(web: Path) -> None:
    # This is deliberately not a developer-machine command: the hosted workflow owns
    # startup/discard of its fresh containers. Refuse preconfigured external targets.
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise RuntimeError("Use the fresh GitHub-hosted isolated workflow")
    for name in ("SUPABASE_URL", "DATABASE_URL", "PGHOST", "MYFINHUB_API_BASE_URL"):
        if os.environ.get(name):
            raise RuntimeError(f"Refusing preconfigured target: {name}")
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=web, text=True).strip() != WEB_SHA:
        raise RuntimeError("Web fixture checkout does not match the reviewed immutable SHA")
    android = Path(__file__).resolve().parent.parent
    publishable, service = local_status(subprocess.check_output(
        [*CLI, "status", "-o", "json"], cwd=web, text=True, stderr=subprocess.DEVNULL,
    ))
    suffix = secrets.token_hex(6)
    owner_email = f"android-owner-{suffix}@example.test"
    outsider_email = f"android-outsider-{suffix}@example.test"
    password = secrets.token_urlsafe(24) + "9!"
    # Never print the fixture credentials, database key, server output or tokens.
    owner = fixture_request("/auth/v1/admin/users", service,
                            {"email": owner_email, "password": password, "email_confirm": True})
    fixture_request("/auth/v1/admin/users", service,
                    {"email": outsider_email, "password": password, "email_confirm": True})
    if not isinstance(owner.get("id"), str):
        raise RuntimeError("Disposable owner creation did not return an identity")
    fixture_request("/rest/v1/rheomiq_owner", service, {"singleton": True, "user_id": owner["id"]})

    with tempfile.TemporaryDirectory(prefix="android-shared-pin-") as tmp:
        fixture = Path(tmp) / "finance.json"
        fixture.write_bytes(subprocess.check_output([
            str(web / "node_modules/.bin/tsx"), "-e",
            "import { realStackFinanceData } from './scripts/real-stack-fixture.ts'; process.stdout.write(JSON.stringify(realStackFinanceData()))",
        ], cwd=web, stderr=subprocess.DEVNULL))
        # Explicit environment; no incidental production DB or Vercel credentials.
        server_env = {k: os.environ[k] for k in ("PATH", "HOME", "LANG", "TMPDIR") if k in os.environ}
        server_env.update({
            "RHEOMIQ_PORT": "4317", "RHEOMIQ_HOST": "127.0.0.1", "SUPABASE_URL": SUPABASE,
            "SUPABASE_PUBLISHABLE_KEY": publishable, "CARD_VAULT_KEY": secrets.token_hex(32),
            "CARD_VAULT_KEY_VERSION": "1", "MYFINHUB_APP_VERSION": "android-isolated-qa",
        })
        child = subprocess.Popen([str(web / "node_modules/.bin/tsx"), "server/index.ts", "--serve-dist"],
                                 cwd=web, env=server_env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            for _ in range(120):
                if child.poll() is not None:
                    raise RuntimeError("Isolated Web server exited before health check")
                try:
                    with OPENER.open(WEB + "/api/health", timeout=2) as reply:
                        if reply.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                time.sleep(0.5)
            else:
                raise RuntimeError("Isolated Web server not ready")
            test_env = os.environ.copy()
            test_env.update({
                "MYFINHUB_RUN_ISOLATED_LOCK_TEST": "1",
                "MYFINHUB_ISOLATED_PUBLISHABLE_KEY": publishable,
                "MYFINHUB_ISOLATED_OWNER_EMAIL": owner_email,
                "MYFINHUB_ISOLATED_OUTSIDER_EMAIL": outsider_email,
                "MYFINHUB_ISOLATED_PASSWORD": password,
                "MYFINHUB_ISOLATED_FIXTURE": str(fixture),
            })
            # Do not leak an ambient local/production service key to the JVM.
            for var in ("SUPABASE_SERVICE_ROLE_KEY", "SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEY", "CARD_VAULT_KEY"):
                test_env.pop(var, None)
            report = android / "app/build/test-results/testDebugUnitTest" / f"TEST-{TEST}.xml"
            report.unlink(missing_ok=True)
            try:
                subprocess.run(["./gradlew", ":app:testDebugUnitTest", "--tests", TEST, "--rerun",
                                "--no-build-cache", "--no-configuration-cache", "--no-daemon"],
                               cwd=android, env=test_env, check=True, timeout=900)
            except subprocess.CalledProcessError:
                if child.poll() is not None:
                    print("Isolated Web process exited before native test completion.", file=sys.stderr)
                diagnostic = safe_test_failure(report)
                if diagnostic:
                    print(diagnostic, file=sys.stderr)
                kinds = safe_transport_diagnostics(report)
                if kinds:
                    print("Isolated transport categories: " + ", ".join(kinds), file=sys.stderr)
                raise
            require_test_executed(report)
            kinds = safe_transport_diagnostics(report)
            if kinds:
                print("Isolated transport categories: " + ", ".join(kinds))
            print("PASS: executed native shared-PIN, device and account loopback proof against Web PR #668 candidate")
        finally:
            child.terminate()
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise RuntimeError("Expected the pinned Web checkout directory")
        main(Path(sys.argv[1]).resolve())
    except (RuntimeError, subprocess.SubprocessError, OSError, ValueError, ET.ParseError) as exc:
        # Never print subprocess command environments or upstream bodies.
        # RuntimeError messages are created above and contain only fixed stage labels.
        print(f"Isolated proof failed: {exc if isinstance(exc, RuntimeError) else type(exc).__name__}", file=sys.stderr)
        sys.exit(1)
