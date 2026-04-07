import re
with open('app/src/main/res/layout/fragment_musician_profile.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix orphaned ======= block
content = re.sub(r'=======\s*<TextView\s+android:id="@+id/tvPhone.*?>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816', '', content, flags=re.DOTALL)

# Fix remaining standard git conflicts
content = re.sub(r'<<<<<<< HEAD\n(.*?)\n=======\n(.*?)\n>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816', r'\1', content, flags=re.DOTALL)

with open('app/src/main/res/layout/fragment_musician_profile.xml', 'w', encoding='utf-8') as f:
    f.write(content)
print("Git markers removed!")
