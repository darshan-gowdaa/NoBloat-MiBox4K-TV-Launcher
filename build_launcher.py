import os
import sys
import subprocess
import shutil
import zipfile

SDK_DIR = r"C:\Users\Administrator\AppData\Local\Android\Sdk"
BUILD_TOOLS = os.path.join(SDK_DIR, "build-tools", "34.0.0")
AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8_JAR = os.path.join(BUILD_TOOLS, "lib", "d8.jar")
APKSIGNER_JAR = os.path.join(BUILD_TOOLS, "lib", "apksigner.jar")
JDK17_DIR = r"C:\Users\Administrator\tools\jdk17"
JAVA_EXE = os.path.join(JDK17_DIR, "bin", "java.exe")
JAVAC = os.path.join(JDK17_DIR, "bin", "javac.exe")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
ANDROID_JAR = os.path.join(SDK_DIR, "platforms", "android-28", "android.jar")
KEYSTORE = r"C:\Users\Administrator\debug.keystore"

LAUNCHER_DIR = os.path.dirname(os.path.abspath(__file__))
APP_DIR = os.path.join(LAUNCHER_DIR, "app")
SRC_DIR = os.path.join(APP_DIR, "src", "main")
RES_DIR = os.path.join(SRC_DIR, "res")
ASSETS_DIR = os.path.join(SRC_DIR, "assets")
MANIFEST = os.path.join(SRC_DIR, "AndroidManifest.xml")
JAVA_DIR = os.path.join(SRC_DIR, "java", "com", "appletv", "launcher")

BUILD_DIR = os.path.join(LAUNCHER_DIR, "build_out")
RES_ZIP = os.path.join(BUILD_DIR, "res.zip")
GEN_DIR = os.path.join(BUILD_DIR, "gen")
CLASSES_DIR = os.path.join(BUILD_DIR, "classes")
DEX_DIR = os.path.join(BUILD_DIR, "dex")
UNALIGNED_APK = os.path.join(BUILD_DIR, "unaligned.apk")
ALIGNED_APK = os.path.join(BUILD_DIR, "aligned.apk")
FINAL_APK = os.path.join(LAUNCHER_DIR, "appletv-launcher.apk")

def run(cmd, desc):
    print(f"==> {desc}")
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"FAILED: {desc}")
        print("stdout:", res.stdout)
        print("stderr:", res.stderr)
        sys.exit(1)
    return res

def main():
    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    os.makedirs(GEN_DIR, exist_ok=True)
    os.makedirs(CLASSES_DIR, exist_ok=True)
    os.makedirs(DEX_DIR, exist_ok=True)

    # 1. Compile resources
    run([AAPT2, "compile", "--dir", RES_DIR, "-o", RES_ZIP], "Compiling resources with aapt2")

    # 2. Link resources and generate R.java
    link_cmd = [
        AAPT2, "link",
        "-I", ANDROID_JAR,
        "--manifest", MANIFEST,
        "--min-sdk-version", "26",
        "--target-sdk-version", "28",
        "--version-code", "1",
        "--version-name", "1.0",
        "--no-xml-namespaces",
        "--java", GEN_DIR,
        "-o", UNALIGNED_APK,
        "--auto-add-overlay"
    ]
    if os.path.exists(ASSETS_DIR):
        link_cmd.extend(["-A", ASSETS_DIR])
    link_cmd.append(RES_ZIP)
    run(link_cmd, "Linking resources with aapt2")

    # 3. Find java files
    java_files = [os.path.join(GEN_DIR, "com", "appletv", "launcher", "R.java")]
    for root, dirs, files in os.walk(JAVA_DIR):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))

    # 4. Compile Java
    javac_cmd = [
        JAVAC,
        "-encoding", "UTF-8",
        "-source", "1.8",
        "-target", "1.8",
        "-bootclasspath", ANDROID_JAR,
        "-cp", ANDROID_JAR,
        "-d", CLASSES_DIR
    ] + java_files
    run(javac_cmd, "Compiling Java with javac")

    # 5. Compile DEX with d8
    class_files = []
    for root, dirs, files in os.walk(CLASSES_DIR):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))

    d8_cmd = [JAVA_EXE, "-cp", D8_JAR, "com.android.tools.r8.D8", "--release", "--min-api", "26", "--output", DEX_DIR, "--lib", ANDROID_JAR] + class_files
    run(d8_cmd, "Converting classes to DEX with d8")

    # 6. Add classes.dex to APK
    dex_file = os.path.join(DEX_DIR, "classes.dex")
    with zipfile.ZipFile(UNALIGNED_APK, "a", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.write(dex_file, "classes.dex")
    print("==> Added classes.dex to APK")

    # 7. Zipalign
    if os.path.exists(ALIGNED_APK):
        os.remove(ALIGNED_APK)
    run([ZIPALIGN, "-f", "-p", "4", UNALIGNED_APK, ALIGNED_APK], "Aligning APK with zipalign")

    # 8. Sign APK
    if os.path.exists(FINAL_APK):
        os.remove(FINAL_APK)
    shutil.copyfile(ALIGNED_APK, FINAL_APK)
    run([
        JAVA_EXE, "-jar", APKSIGNER_JAR, "sign",
        "--ks", KEYSTORE,
        "--ks-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey",
        "--key-pass", "pass:android",
        FINAL_APK
    ], "Signing APK with apksigner")

    print(f"SUCCESS! APK built and signed at: {FINAL_APK}")
    print(f"APK size: {os.path.getsize(FINAL_APK)} bytes")

if __name__ == "__main__":
    main()
