import os
import subprocess
import sys
import tempfile
import zipfile
import shutil
import urllib.request
import datetime
import time

ROOT = r"C:\Users\123\Desktop\1"
CLIENT = os.path.join(ROOT, "client")
SERVER = os.path.join(ROOT, "server")
JAVA_HOME = r"C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
JAVA_EXE = os.path.join(JAVA_HOME, "bin", "java.exe")
GRADLE_VERSION = "8.4"
GRADLE_URL = f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip"
ANDROID_SDK_ROOT = r"C:\Android\Sdk"

def progress_hook(block_num, block_size, total_size):
    downloaded = block_num * block_size
    if total_size > 0:
        percent = min(100, int(downloaded * 100 / total_size))
        bar_len = 30
        filled = int(bar_len * percent / 100)
        bar = "█" * filled + "░" * (bar_len - filled)
        size_mb = total_size / (1024 * 1024)
        down_mb = downloaded / (1024 * 1024)
        sys.stdout.write(f"\r  [{bar}] {percent:3d}% | {down_mb:.1f}/{size_mb:.1f} MB  ")
        sys.stdout.flush()

def download_with_progress(url, dest, desc="Downloading"):
    print(f"  {desc}...")
    print(f"  URL: {url}")
    try:
        urllib.request.urlretrieve(url, dest, reporthook=progress_hook)
        print()
        return True
    except Exception as e:
        print(f"\n  ERROR: {e}")
        return False

def run(cmd, cwd=None, check=True):
    full_env = os.environ.copy()
    full_env["JAVA_HOME"] = JAVA_HOME
    full_env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.pathsep + full_env.get("PATH", "")
    full_env["ANDROID_HOME"] = ANDROID_SDK_ROOT
    full_env["ANDROID_SDK_ROOT"] = ANDROID_SDK_ROOT
    
    print(f"\n>>> {cmd}")
    result = subprocess.run(cmd, shell=True, cwd=cwd, env=full_env)
    if check and result.returncode != 0:
        raise Exception(f"Command failed: {cmd}")
    return result

# ===== БЭКАП =====
def backup():
    print("\n" + "="*70)
    print("  📦 BACKUP — архивирую проект")
    print("="*70)
    backup_dir = os.path.join(ROOT, "backups")
    os.makedirs(backup_dir, exist_ok=True)
    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    zip_name = os.path.join(backup_dir, f"backup_{timestamp}.zip")
    
    total_files = 0
    with zipfile.ZipFile(zip_name, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(ROOT):
            dirs[:] = [d for d in dirs if d not in ['.git', '.gradle', 'build', 'backups', '__pycache__', 'node_modules', 'output']]
            for file in files:
                if file.endswith(('.apk', '.aab', '.jks', '.keystore', '.zip')):
                    continue
                filepath = os.path.join(root, file)
                arcname = os.path.relpath(filepath, ROOT)
                zf.write(filepath, arcname)
                total_files += 1
                if total_files % 100 == 0:
                    sys.stdout.write(f"\r  Файлов: {total_files}...")
                    sys.stdout.flush()
    
    size_mb = os.path.getsize(zip_name) / (1024 * 1024)
    print(f"\n  ✅ Backup готов! {size_mb:.1f} MB, {total_files} файлов")
    return zip_name

# ===== ANDROID SDK (проверка + доустановка пакетов) =====
def setup_android_sdk():
    print("\n" + "="*70)
    print("  📱 ANDROID SDK")
    print("="*70)
    
    # Ищем sdkmanager
    sdkmanager = None
    for root, dirs, files in os.walk(ANDROID_SDK_ROOT):
        if "sdkmanager.bat" in files:
            sdkmanager = os.path.join(root, "sdkmanager.bat")
            break
    
    if not sdkmanager:
        print("  ❌ Android SDK не найден!")
        print(f"  Запусти сначала install_adb.py")
        raise Exception("Android SDK not found!")
    
    print(f"  sdkmanager: {sdkmanager}")
    
    # Принимаем лицензии на всякий случай
    print("\n  Принимаю лицензии...")
    subprocess.run(f'echo y | "{sdkmanager}" --sdk_root="{ANDROID_SDK_ROOT}" --licenses', shell=True, capture_output=True)
    
    # Устанавливаем платформы если нет
    packages = [
        ("platform-tools", "Platform Tools"),
        ("platforms;android-34", "SDK Platform 34"),
        ("build-tools;34.0.0", "Build Tools 34.0.0"),
    ]
    
    for pkg, desc in packages:
        print(f"  Устанавливаю {desc}...")
        result = subprocess.run(
            f'"{sdkmanager}" --sdk_root="{ANDROID_SDK_ROOT}" {pkg}',
            shell=True, capture_output=True, text=True
        )
        if result.returncode == 0:
            print(f"  ✅ {desc}")
        else:
            if "already installed" in result.stdout.lower() or "nothing to install" in result.stdout.lower():
                print(f"  ✅ {desc} (уже установлен)")
            else:
                print(f"  ⚠️ {desc}: {result.stdout[-100:]}")
    
    # Установка переменных
    os.environ["ANDROID_HOME"] = ANDROID_SDK_ROOT
    os.environ["ANDROID_SDK_ROOT"] = ANDROID_SDK_ROOT
    platform_tools = os.path.join(ANDROID_SDK_ROOT, "platform-tools")
    if os.path.exists(platform_tools):
        os.environ["PATH"] = platform_tools + os.pathsep + os.environ.get("PATH", "")
    
    # Проверка ADB
    adb = os.path.join(platform_tools, "adb.exe")
    if os.path.exists(adb):
        result = subprocess.run(f'"{adb}" --version', shell=True, capture_output=True, text=True)
        print(f"\n  ✅ ADB: {result.stdout.strip().split(chr(10))[0]}")
    else:
        print(f"\n  ⚠️ ADB не найден")

# ===== LOCAL.PROPERTIES =====
def create_local_properties():
    print("\n" + "="*70)
    print("  📝 LOCAL.PROPERTIES")
    print("="*70)
    sdk_path_unix = ANDROID_SDK_ROOT.replace("\\", "/")
    for module, name in [(CLIENT, "client"), (SERVER, "server")]:
        props_file = os.path.join(module, "local.properties")
        with open(props_file, 'w') as f:
            f.write(f"sdk.dir={sdk_path_unix}\n")
        print(f"  ✅ {name}/local.properties")

# ===== GRADLE =====
def install_gradle():
    print("\n" + "="*70)
    print(f"  🐘 GRADLE {GRADLE_VERSION}")
    print("="*70)
    gradle_home = os.path.join(os.environ.get("USERPROFILE", "C:\\"), ".gradle_wrapper")
    gradle_dir = os.path.join(gradle_home, f"gradle-{GRADLE_VERSION}")
    
    if not os.path.exists(gradle_dir):
        zip_path = os.path.join(tempfile.gettempdir(), f"gradle-{GRADLE_VERSION}.zip")
        if not download_with_progress(GRADLE_URL, zip_path, f"Скачиваю Gradle {GRADLE_VERSION}"):
            print("  ⚠️ Не удалось скачать, буду использовать gradlew")
            return None
        os.makedirs(gradle_home, exist_ok=True)
        shutil.unpack_archive(zip_path, gradle_home)
        os.remove(zip_path)
        print(f"  ✅ Установлен: {gradle_dir}")
    else:
        print(f"  ✅ Уже установлен: {gradle_dir}")
    
    gradle_bin = os.path.join(gradle_dir, "bin")
    os.environ["PATH"] = gradle_bin + os.pathsep + os.environ.get("PATH", "")
    os.environ["GRADLE_HOME"] = gradle_dir
    return gradle_dir

# ===== ИКОНКИ =====
def generate_icons():
    print("\n" + "="*70)
    print("  🎨 ИКОНКИ")
    print("="*70)
    make_icons = os.path.join(ROOT, "make_icons.py")
    if not os.path.exists(make_icons):
        print("  ⚠️ make_icons.py не найден")
        return
    run("python make_icons.py client 139 92 246", cwd=ROOT)
    run("python make_icons.py server 0 184 148", cwd=ROOT)
    print("  ✅ Иконки сгенерированы")

# ===== СБОРКА =====
def build_module(name, module_path):
    print("\n" + "="*70)
    print(f"  🔨 СБОРКА {name} APK (debug)")
    print("="*70)
    print("  Это может занять 5-15 минут...")
    start = time.time()
    gradlew = os.path.join(module_path, "gradlew.bat")
    if os.path.exists(gradlew):
        run(f'"{gradlew}" assembleDebug --no-daemon --stacktrace', cwd=module_path)
    else:
        run("gradle assembleDebug --no-daemon --stacktrace", cwd=module_path)
    elapsed = time.time() - start
    print(f"  ✅ {name} собран за {elapsed:.0f} сек ({elapsed/60:.1f} мин)")

# ===== КОПИРОВАНИЕ =====
def copy_apks():
    print("\n" + "="*70)
    print("  📋 КОПИРОВАНИЕ APK")
    print("="*70)
    out_dir = os.path.join(ROOT, "output")
    os.makedirs(out_dir, exist_ok=True)
    
    apks = {
        "VerySchool-Client-debug.apk": os.path.join(CLIENT, "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
        "VerySchool-Server-debug.apk": os.path.join(SERVER, "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
    }
    
    copied = []
    for name, src in apks.items():
        if os.path.exists(src):
            dst = os.path.join(out_dir, name)
            shutil.copy2(src, dst)
            size_mb = os.path.getsize(dst) / (1024 * 1024)
            print(f"  ✅ {name} ({size_mb:.1f} MB)")
            copied.append(dst)
        else:
            print(f"  ❌ {name} — не собран")
    return copied

# ===== MAIN =====
if __name__ == "__main__":
    print("="*70)
    print("  🚀 VERY SCHOOL BUILD SCRIPT")
    print("="*70)
    print(f"  Root: {ROOT}")
    print(f"  Java: {JAVA_HOME}")
    print(f"  SDK:  {ANDROID_SDK_ROOT}")
    print("="*70)
    
    total_start = time.time()
    
    try:
        backup_zip = backup()
        setup_android_sdk()
        create_local_properties()
        install_gradle()
        generate_icons()
        
        build_module("CLIENT", CLIENT)
        build_module("SERVER", SERVER)
        
        apks = copy_apks()
        
        total_elapsed = time.time() - total_start
        
        print("\n" + "="*70)
        print("  🎉 СБОРКА ЗАВЕРШЕНА!")
        print("="*70)
        print(f"  Время: {total_elapsed/60:.1f} мин")
        print(f"  Backup: {backup_zip}")
        print(f"  APK ({len(apks)} шт):")
        for apk in apks:
            print(f"    → {apk}")
        print(f"\n  Папка с APK: {os.path.join(ROOT, 'output')}")
        print("="*70)
        
    except Exception as e:
        total_elapsed = time.time() - total_start
        print("\n" + "="*70)
        print(f"  ❌ СБОРКА ПРОВАЛИЛАСЬ ({total_elapsed/60:.1f} мин)")
        print("="*70)
        print(f"  Ошибка: {e}")
        print(f"\n  Backup сохранён в: backups/")
        print("="*70)
        sys.exit(1)