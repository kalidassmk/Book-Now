import os
import subprocess
import sys

def setup_new_env():
    print("🚀 Setting up fresh environment with Python 3.13...")
    
    # 1. Create fresh venv
    try:
        subprocess.run(["python3", "-m", "venv", "venv313"], check=True)
        print("✅ Created venv313")
    except Exception as e:
        print(f"❌ Failed to create venv: {e}")
        return

    # 2. Upgrade pip and install libraries
    pip_path = "./venv313/bin/pip"
    libs = [
        "google-generativeai",
        "redis",
        "aiohttp",
        "beautifulsoup4",
        "textblob",
        "fake-useragent",
        "openai",
        "anthropic",
        "google-genai"
    ]
    
    print(f"📦 Installing libraries: {', '.join(libs)}...")
    try:
        subprocess.run([pip_path, "install", "--upgrade", "pip"], check=True)
        subprocess.run([pip_path, "install"] + libs, check=True)
        print("✅ Libraries installed successfully.")
    except Exception as e:
        print(f"❌ Failed to install libraries: {e}")
        return

    print("\n🎉 SETUP COMPLETE!")
    print("To run your news analyzer with Python 3.13, use:")
    print("./venv313/bin/python3 unified_ai_analyzer.py")

if __name__ == "__main__":
    setup_new_env()
