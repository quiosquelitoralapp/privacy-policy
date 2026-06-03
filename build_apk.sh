#!/bin/bash
set -e

PROJECT=/home/user/privacy-policy
BUILD=$PROJECT/build_out
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
AAPT2=/usr/lib/android-sdk/build-tools/29.0.3/aapt2
DX=/usr/lib/android-sdk/build-tools/29.0.3/dx
ZIPALIGN=/usr/lib/android-sdk/build-tools/29.0.3/zipalign
APKSIGNER_JAR=/usr/lib/android-sdk/build-tools/29.0.3/apksigner.jar
SRC=$PROJECT/app/src/main/java/com/quiosquelitoral/gunboundguide
RES=$PROJECT/app/src/main/res
MANIFEST=$PROJECT/app/src/main/AndroidManifest.xml
KEYSTORE=$PROJECT/debug.keystore
PKG=com/quiosquelitoral/gunboundguide

echo "=== Limpando build anterior ==="
rm -rf $BUILD
mkdir -p $BUILD/{compiled_res,gen,gen_classes,kotlin_out}

echo "=== 1. Compilando recursos (aapt2 compile) ==="
$AAPT2 compile --dir $RES -o $BUILD/compiled_res/

echo "=== 2. Linkando recursos e gerando R.java (aapt2 link) ==="
$AAPT2 link \
    -o $BUILD/resources.apk \
    -I $ANDROID_JAR \
    --manifest $MANIFEST \
    --java $BUILD/gen \
    --auto-add-overlay \
    $BUILD/compiled_res/*.flat

echo "=== 3. Compilando R.java ==="
mkdir -p $BUILD/gen_classes
javac -source 1.8 -target 1.8 \
    -classpath $ANDROID_JAR \
    -d $BUILD/gen_classes \
    $BUILD/gen/$PKG/R.java

jar cf $BUILD/R.jar -C $BUILD/gen_classes .

echo "=== 4. Compilando Kotlin com R.jar no classpath ==="
KOTLIN_FILES=$(find $SRC -name "*.kt" | tr '\n' ' ')
kotlinc \
    -classpath "$ANDROID_JAR:$BUILD/R.jar" \
    -d $BUILD/classes.jar \
    $KOTLIN_FILES

echo "=== 5. Convertendo para DEX ==="
$DX --dex --output=$BUILD/classes.dex $BUILD/classes.jar $BUILD/R.jar

echo "=== 6. Montando APK final ==="
cp $BUILD/resources.apk $BUILD/app-unsigned.apk
cd $BUILD && zip -j app-unsigned.apk classes.dex

echo "=== 7. Alinhando APK ==="
$ZIPALIGN -v 4 $BUILD/app-unsigned.apk $BUILD/app-aligned.apk

echo "=== 8. Gerando keystore de debug ==="
keytool -genkeypair -v \
    -keystore $KEYSTORE \
    -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" 2>&1 | grep -E "Generated|Stored|Error" || true

echo "=== 9. Assinando APK ==="
java -jar $APKSIGNER_JAR sign \
    --ks $KEYSTORE \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out $BUILD/GunboundGuide-debug.apk \
    $BUILD/app-aligned.apk

echo ""
echo "==============================="
echo "  APK pronto!"
ls -lh $BUILD/GunboundGuide-debug.apk
echo "==============================="
