import glob
import os
import zipfile
import subprocess

search_dir = r"C:\Users\Abhishek soni\.gradle\caches\modules-2\files-2.1\androidx.navigation3\navigation3-ui-android"
aar_files = glob.glob(os.path.join(search_dir, "**", "*.aar"), recursive=True)

if not aar_files:
    print("No aar found")
    exit(1)

aar_path = aar_files[0]
temp_dir = r"C:\Users\Abhishek soni\.gradle\caches\temp_nav3"
os.makedirs(temp_dir, exist_ok=True)

with zipfile.ZipFile(aar_path, 'r') as zip_ref:
    zip_ref.extract('classes.jar', temp_dir)

classes_jar = os.path.join(temp_dir, 'classes.jar')
result = subprocess.run(["javap", "-classpath", classes_jar, "androidx.navigation3.scene.DialogSceneStrategy"], capture_output=True, text=True)
print(result.stdout)
