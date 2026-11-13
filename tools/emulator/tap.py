"""화면에서 글자나 설명(content-desc)이 들어 있는 요소를 찾아 가운데를 누른다. 못 찾으면 종료 코드 1."""
import re
import subprocess
import sys
import time

needle = sys.argv[1]
pattern = re.compile(r'<node [^>]*?(?:text|content-desc)="[^"]*' + re.escape(needle) + r'[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')

for _ in range(6):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
    matches = [tuple(map(int, m.groups())) for m in pattern.finditer(xml)]
    if matches:
        # 겹친 노드가 여럿이면 가장 작은(안쪽) 것을 누른다
        x0, y0, x1, y1 = min(matches, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
        subprocess.run(["adb", "shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2)])
        sys.exit(0)
    time.sleep(1.5)
sys.exit(1)
