import re
import sys

files = [
    "reference/jadx/sources/com/harish/wordlookup/ui/MainActivityKt.java",
    "reference/jadx/sources/com/harish/wordlookup/ui/SetupScreenKt.java",
    "reference/jadx/sources/com/harish/wordlookup/ui/LookupCardKt.java",
    "reference/jadx/sources/com/harish/wordlookup/ui/RegisterScreenKt.java",
    "reference/jadx/sources/com/harish/wordlookup/ui/RegisterScreenKt$RegisterScreen$2.java",
    "reference/jadx/sources/com/harish/wordlookup/ui/Permissions.java",
]

lit = re.compile(r'"((?:[^"\\]|\\.)*)"')
identifier = re.compile(r"^[A-Za-z0-9_$/;.()\[\]<>-]+$")

for f in files:
    text = open(f, encoding="utf-8").read()
    for m in lit.finditer(text):
        s = m.group(1)
        if len(s) < 3:
            continue
        if identifier.match(s):
            continue
        print(f.split("/")[-1], "::", repr(s))
