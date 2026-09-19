#!/usr/bin/env python3
# Build jarcraft.zip bundle (jar + exe + bilingual readme) into download/
import zipfile, os, time

DL = "/home/z/my-project/download"
JAR = os.path.join(DL, "jarcraft-alpha1.0.0.jar")
EXE = os.path.join(DL, "legacy-jarcraft-launcher.exe")
ZIP = os.path.join(DL, "jarcraft.zip")

README = """jarcraft alpha 1.0.0
====================

[EN] How to run
1. Install Java 8 or newer (Oracle Java / Adoptium Temurin recommended).
2. Keep "jarcraft-alpha1.0.0.jar" and "legacy-jarcraft-launcher.exe"
   in the SAME folder.
3. Double-click "legacy-jarcraft-launcher.exe", pick memory, press Play.
   Or run directly:  java -jar jarcraft-alpha1.0.0.jar

[JP] 実行方法
1. Java 8 以降をインストールしてください（Oracle Java / Adoptium 推奨）。
2. 「jarcraft-alpha1.0.0.jar」と「legacy-jarcraft-launcher.exe」を
   同じフォルダに置いてください。
3. 「legacy-jarcraft-launcher.exe」をダブルクリックし、メモリを選んで
   Play を押してください。直接実行の場合:  java -jar jarcraft-alpha1.0.0.jar

Controls / 操作
  Mouse        : look            マウス: 視点
  WASD         : move            移動
  Space        : jump            ジャンプ
  Shift        : sneak           しゃがみ
  Left click   : break block     ブロック破壊
  Right click  : place block     ブロック設置
  1-9          : select block    ブロック選択
  F3           : debug info      デバッグ表示
  Esc          : release mouse   マウス解放

If Windows SmartScreen shows a warning for the launcher, click
"More info" -> "Run anyway" (unsigned binary).
スマートスクリーンの警告が出た場合は「詳細情報」→「実行」を選択してください。
"""

# fresh timestamps so any file watcher re-picks the files up
now = time.time()
for f in (JAR, EXE):
    os.utime(f, (now, now))

with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as z:
    z.write(JAR, "jarcraft/jarcraft-alpha1.0.0.jar")
    z.write(EXE, "jarcraft/legacy-jarcraft-launcher.exe")
    z.writestr("jarcraft/readme.txt", README)

print("created:", ZIP, os.path.getsize(ZIP), "bytes")
with zipfile.ZipFile(ZIP) as z:
    bad = z.testzip()
    print("zip integrity:", "OK" if bad is None else f"BAD: {bad}")
    for i in z.infolist():
        print(" -", i.filename, i.file_size)
