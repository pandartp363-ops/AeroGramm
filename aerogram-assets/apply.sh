#!/usr/bin/env bash
# Раскладывает файлы AeroGram по проекту Telegram. Запускается из корня форка (workflow делает это сам).
set -e
A=aerogram-assets
RES=TMessagesProj/src/main/res
JAVA=TMessagesProj/src/main/java
MAN=TMessagesProj/src/main/AndroidManifest.xml

cp $A/AeroGram.java   $JAVA/org/telegram/messenger/AeroGram.java
cp $A/AeroActivity.java $JAVA/org/telegram/ui/AeroActivity.java
cp $A/AeroAdminActivity.java $JAVA/org/telegram/ui/AeroAdminActivity.java
cp $A/AeroNft.java $JAVA/org/telegram/ui/AeroNft.java

# адрес сервера и бота
if [ -n "$AERO_SERVER" ]; then
  sed -i "s#https://YOUR-SERVER.example.com#${AERO_SERVER%/}#" $JAVA/org/telegram/messenger/AeroGram.java
  HOST=$(echo "$AERO_SERVER" | sed -E 's#https?://##; s#[:/].*##')
else HOST=localhost; fi
[ -n "$AERO_BOT" ] && sed -i "s#@YourAeroBot#${AERO_BOT}#" $JAVA/org/telegram/messenger/AeroGram.java

# иконки
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mkdir -p $RES/mipmap-$d
  cp $A/ic_launcher_$d.png       $RES/mipmap-$d/ic_launcher.png
  cp $A/ic_launcher_round_$d.png $RES/mipmap-$d/ic_launcher_round.png
  cp $A/ic_launcher_fg_$d.png    $RES/mipmap-$d/ic_launcher_foreground.png
done
mkdir -p $RES/mipmap-anydpi-v26 $RES/drawable-nodpi $RES/xml $RES/values
cp $A/aero_logo.png $RES/drawable-nodpi/aero_logo.png
mkdir -p $RES/drawable-nodpi
cp $A/badges/badge_owner.png $RES/drawable-nodpi/badge_owner.png
cp $A/badges/badge_admin.png $RES/drawable-nodpi/badge_admin.png
cp $A/badges/badge_channel.png $RES/drawable-nodpi/badge_channel.png
sed "s#YOUR-HOST#$HOST#" $A/network_security_config.xml > $RES/xml/network_security_config.xml
cat > $RES/values/aero_colors.xml <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources><color name="aero_icon_bg">#F50110</color></resources>
XML
for n in ic_launcher ic_launcher_round; do
cat > $RES/mipmap-anydpi-v26/$n.xml <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/aero_icon_bg"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
XML
done

# манифест: экран AeroActivity + сетевой конфиг
python3 - <<'PY'
p = "TMessagesProj/src/main/AndroidManifest.xml"
s = open(p, encoding="utf-8").read()
for act in ("AeroActivity", "AeroAdminActivity"):
    if 'org.telegram.ui.%s"' % act not in s:
        s = s.replace("</application>", '    <activity android:name="org.telegram.ui.%s" android:theme="@android:style/Theme.Material.NoActionBar" android:exported="false"/>\n    </application>' % act, 1)
if "networkSecurityConfig" not in s:
    s = s.replace("<application", '<application android:networkSecurityConfig="@xml/network_security_config"', 1)
open(p, "w", encoding="utf-8").write(s)

# хук проверки блокировки при старте приложения
p = "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
s = open(p, encoding="utf-8").read()
hook = "org.telegram.messenger.AeroGram.refreshMe(this);"
key = "ApplicationLoader.postInitApplication();"
if hook not in s:
    i = s.find(key)
    if i < 0: print("WARN: не нашёл место для refreshMe() в LaunchActivity, добавь вручную")
    else: s = s[:i+len(key)] + "\n        " + hook + s[i+len(key):]
open(p, "w", encoding="utf-8").write(s)
PY
echo "AeroGram: файлы применены"
