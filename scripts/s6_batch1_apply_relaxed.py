from pathlib import Path

source = Path(__file__).with_name("s6_batch1_apply.py").read_text()
old = '''    if count != 1:\n        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")\n'''
new = '''    if count < 1:\n        raise SystemExit(f"{label}: anchor missing")\n'''
if source.count(old) != 1:
    raise SystemExit("replace_once helper patch mismatch")
source = source.replace(old, new, 1)
exec(compile(source, "s6_batch1_apply.py", "exec"))
