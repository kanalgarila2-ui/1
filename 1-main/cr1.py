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


# ===== ANDROID SDK (проверка + доустановка пакетов) =====
def setup_android_sdk():
    print("\n" + "="*70)
    print("  📱 Переменные")
    print("="*70)

    # Установка переменных
    os.environ["ANDROID_HOME"] = ANDROID_SDK_ROOT
    os.environ["ANDROID_SDK_ROOT"] = ANDROID_SDK_ROOT
    platform_tools = os.path.join(ANDROID_SDK_ROOT, "platform-tools")
    if os.path.exists(platform_tools):
        os.environ["PATH"] = platform_tools + os.pathsep + os.environ.get("PATH", "")

# ===== GRADLE =====
def install_gradle():
    print("\n" + "="*70)
    print(f"  🐘 GRADLE {GRADLE_VERSION}")
    print("="*70)
    
    gradle_home = os.path.join(os.environ.get("USERPROFILE", "C:\\"), ".gradle_wrapper")
    gradle_dir = os.path.join(gradle_home, f"gradle-{GRADLE_VERSION}")
    
    gradle_bin = os.path.join(gradle_dir, "bin")
    os.environ["PATH"] = gradle_bin + os.pathsep + os.environ.get("PATH", "")
    os.environ["GRADLE_HOME"] = gradle_dir
    
    print(f"  ✅ Использую Gradle из: {gradle_dir}")
    return gradle_dir


# ===== СБОРКА =====
def build_module(name, module_path):
    print("\n" + "="*70)
    print(f"  🔨 СБОРКА {name} APK (debug)")
    print("="*70)
    print(f"  Папка сборки: {module_path}")
    print("  Это может занять 5-15 минут...")
    start = time.time()
    
    # Запускаем gradle в папке module_path (которая должна быть client/)
    run("gradle clean assembleDebug --no-daemon --stacktrace", cwd=module_path)
    
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
        setup_android_sdk()
        install_gradle()
        
        build_module("CLIENT", CLIENT)
        
        apks = copy_apks()
        
        total_elapsed = time.time() - total_start
        
        print("\n" + "="*70)
        print("  🎉 СБОРКА ЗАВЕРШЕНА!")
        print("="*70)
        print(f"  Время: {total_elapsed/60:.1f} мин")
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