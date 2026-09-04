import subprocess
import os
classes_jar = r"C:\Users\Abhishek soni\.gradle\caches\temp_nav3\classes.jar"
result = subprocess.run(["javap", "-classpath", classes_jar, "androidx.navigation3.scene.DialogSceneStrategy$Companion"], capture_output=True, text=True)
print(result.stdout)
