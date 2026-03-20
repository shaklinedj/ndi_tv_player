import os
from PIL import Image

image_path = r"C:\Users\Dell\.gemini\antigravity\brain\debf03c2-0288-4b74-9fef-1cbf0d1e7eeb\ndi_tv_logo_1773624709154.png"
res_dir = r"C:\Users\Dell\Desktop\ndi\ndi_tv_player\app\src\main\res"

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

try:
    img = Image.open(image_path).convert("RGBA")
    
    for dpi, size in sizes.items():
        folder = os.path.join(res_dir, f"mipmap-{dpi}")
        os.makedirs(folder, exist_ok=True)
        
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save round version (just crop or we just save square as ic_launcher for now)
        # We will save same image as both for simplicity since Android supports square icons or handles rounding in 8+.
        resized.save(os.path.join(folder, "ic_launcher.png"))
        resized.save(os.path.join(folder, "ic_launcher_round.png"))
        
    print("Icons generated successfully!")
except Exception as e:
    print(f"Error: {e}")
