import re

PATH = "M50 5 C59.5 35.5 64.5 40.5 95 50 C64.5 59.5 59.5 64.5 50 95 C40.5 64.5 35.5 59.5 5 50 C35.5 40.5 40.5 35.5 50 5 Z"

def transform(tx, ty, s):
    nums = [float(n) for n in re.findall(r'-?\d+(?:\.\d+)?', PATH)]
    out = []
    i = 0
    # Pairs, in order: M then 4x C.
    for k, n in enumerate(nums):
        v = tx + s * n if k % 2 == 0 else ty + s * n
        out.append(f"{round(v,3):g}")
    # Rebuild the command structure: M p0, then C for each of the four segments (3 point pairs each).
    m = out[0:2]
    cs = [out[2+i*6 : 2+(i+1)*6] for i in range(4)]
    parts = [f"M{m[0]},{m[1]}"]
    for c in cs:
        parts.append("C" + ",".join(f"{c[j]},{c[j+1]}" for j in range(0, 6, 2)))
    return " ".join(parts) + " Z"

fg_outer = transform(18.36, 18.36, 0.7128)
fg_inner = transform(37.8, 37.8, 0.324)
mk_outer = transform(15, 15, 0.70)
mk_inner = transform(35, 35, 0.30)

fg = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
    Adaptive foreground: the final white star with its concentric cutout, on
    the 108 canvas. Geometry is the delivered master (translate 18.36, scale
    .7128; cutout translate 37.8, scale .324), emitted as one path with
    fillType evenOdd so the cutout shows the background layer through.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{fg_outer} {fg_inner}" />
</vector>
'''

mono = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
    Adaptive monochrome layer: the same star as the foreground, as a plain
    silhouette. The system tints it; only the alpha of this shape is honoured,
    so the cutout stays transparent and the tint passes through it.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{fg_outer} {fg_inner}" />
</vector>
'''

bg = '''<?xml version="1.0" encoding="utf-8"?>
<!--
    Adaptive background: the final full-bleed brand gradient, 108 canvas.
    Stops and direction from the delivered composite master (135 degrees,
    blue 0 / violet .55 / pink 1).
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0 H108 V108 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="#FF4285F4" />
                <item android:offset="0.55" android:color="#FF7C6CF0" />
                <item android:offset="1" android:color="#FFE85D9E" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

mark = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
    The final in-app mark: the composite (gradient bed, white star, concentric
    cutout), used by the hub's about row and the about page. Same geometry as
    the launcher's layers, on the 100-unit canvas the mark has always had.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="100dp"
    android:height="100dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
    <path android:pathData="M0,0 H100 V100 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="100"
                android:endY="100">
                <item android:offset="0" android:color="#FF4285F4" />
                <item android:offset="0.55" android:color="#FF7C6CF0" />
                <item android:offset="1" android:color="#FFE85D9E" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{mk_outer} {mk_inner}" />
</vector>
'''

open("app/src/main/res/drawable/ic_launcher_foreground.xml","w").write(fg)
open("app/src/main/res/drawable/ic_launcher_monochrome.xml","w").write(mono)
open("app/src/main/res/drawable/ic_launcher_background.xml","w").write(bg)
open("core/ui/src/main/res/drawable/ic_starveil_mark.xml","w").write(mark)
print("vectors written")
print("fg outer:", fg_outer)
