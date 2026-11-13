"""화면에서 글자나 설명(content-desc)이 들어 있는 요소를 찾아 가운데를 누른다. 못 찾으면 종료 코드 1.

--check 를 주면 누르지 않고 화면에 있는지만 확인한다. --gone 은 반대로 화면에서 사라졌는지 확인한다.
접근성 글자에 줄바꿈 금지 문자(U+2060)가 섞여 있으면 화면 낭독기도 같은 글자를 받는다.
그런 경우에는 찾은 것으로 보되, --a11y-log 파일에 그 글자를 남긴다.
"""
import argparse
import re
import subprocess
import sys
import time

WORD_JOINER = "⁠"

parser = argparse.ArgumentParser()
parser.add_argument("needle")
parser.add_argument("--check", action="store_true")
parser.add_argument("--gone", action="store_true")
parser.add_argument("--a11y-log")
args = parser.parse_args()

pattern = re.compile(
    r'<node [^>]*?(?:text|content-desc)="[^"]*' + re.escape(args.needle)
    + r'[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
)


def find(xml):
    return [tuple(map(int, m.groups())) for m in pattern.finditer(xml)]


for _ in range(6):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
    if "<hierarchy" not in xml:
        # 화면 구조를 못 읽었으면 (애니메이션 중 등) 잠시 뒤 다시 읽는다
        time.sleep(1.5)
        continue
    matches = find(xml)
    if not matches:
        matches = find(xml.replace(WORD_JOINER, ""))
        if matches and args.a11y_log:
            with open(args.a11y_log, "a", encoding="utf-8") as f:
                f.write(f"줄바꿈 금지 문자가 접근성 글자에 남아 있음: {args.needle}\n")
    if args.gone:
        if not matches:
            sys.exit(0)
    elif matches:
        if not args.check:
            # 겹친 노드가 여럿이면 가장 작은(안쪽) 것을 누른다
            x0, y0, x1, y1 = min(matches, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
            subprocess.run(["adb", "shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2)])
        sys.exit(0)
    time.sleep(1.5)
sys.exit(1)
