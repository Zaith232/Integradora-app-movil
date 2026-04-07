import re
import os

file_path = 'app/src/main/java/com/armonihz/app/MusicianProfileFragment.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix standard git conflicts by choosing HEAD (the first part)
# Pattern: <<<<<<< HEAD\n(.*?)\n=======\n(.*?)\n>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
# Note: The hash might be different or there might be multiple. I'll use a more generic pattern.
content = re.sub(r'<<<<<<< HEAD\n(.*?)\n=======\n(.*?)\n>>>>>>> [a-f0-9]+', r'\1', content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Git markers removed from MusicianProfileFragment.kt!")
