from pathlib import Path

runner = Path(__file__)
target = runner.with_name("apply_v140.py")
source = target.read_text(encoding="utf-8")
source = source.replace(
    "updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)",
    "updated, count = re.subn(pattern, lambda match: replacement, text, count=1, flags=re.S)",
)
namespace = {"__file__": str(target), "__name__": "__main__"}
exec(compile(source, str(target), "exec"), namespace)
runner.unlink(missing_ok=True)
