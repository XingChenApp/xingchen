import os

BASE = "/tmp/webhtv-src"

def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

p = os.path.join(BASE, "app/build.gradle")
c = read(p)
c = c.replace('applicationId "com.fongmi.android.tv"', 'applicationId "com.XingChen.tv"')
c = c.replace('versionCode 560', 'versionCode 2')
c = c.replace('versionName "5.6.0"', 'versionName "1.0.1"')
write(p, c)
print("build.gradle patched")

for res_dir in ["app/src/main/res/values/strings.xml", "app/src/main/res/values-zh-rCN/strings.xml"]:
    p = os.path.join(BASE, res_dir)
    if os.path.exists(p):
        c = read(p)
        c = c.replace('<string name="app_name">TV</string>', '<string name="app_name">星辰</string>')
        c = c.replace('<string name="app_name">影视</string>', '<string name="app_name">星辰</string>')
        c = c.replace('<string name="app_name">WebHomeTV</string>', '<string name="app_name">星辰</string>')
        write(p, c)
        print("app_name patched: " + res_dir)

print("Minimal clean patch done")
