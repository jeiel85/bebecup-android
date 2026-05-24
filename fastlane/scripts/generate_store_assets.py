"""Generate Play Store graphic assets from the adaptive-icon vector design.

Outputs:
  fastlane/metadata/android/play_store/icon_512.png            (512x512 icon)
  fastlane/metadata/android/play_store/feature_graphic_1024x500.png

The design mirrors res/drawable/ic_launcher_foreground.xml + ic_launcher_background.xml.
Reusing the same primitives (face, cheeks, smile, eyes, crown) keeps Play assets
visually aligned with the launcher icon without any external rasterization tool.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = REPO_ROOT / "fastlane" / "metadata" / "android" / "play_store"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient_background(size, stops):
    """Diagonal linear gradient. stops = [(offset, (r,g,b)), ...]"""
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    diag = math.hypot(w, h)
    for y in range(h):
        for x in range(w):
            t = (x + y) / (w + h - 2) if (w + h) > 2 else 0
            for i in range(len(stops) - 1):
                o0, c0 = stops[i]
                o1, c1 = stops[i + 1]
                if o0 <= t <= o1:
                    local = (t - o0) / (o1 - o0) if o1 != o0 else 0
                    r, g, b = lerp(c0, c1, local)
                    px[x, y] = (r, g, b, 255)
                    break
            else:
                _, c = stops[-1]
                px[x, y] = (*c, 255)
    return img


def quad_bezier(p0, p1, p2, steps=64):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((x, y))
    return pts


def draw_baby(canvas: Image.Image, cx: float, cy: float, scale: float):
    """Draw the baby-with-crown design centered at (cx, cy).

    `scale` is the side length of the bounding square; the source viewport is
    108x108 and the visible mark occupies roughly (12..76 horizontally, 12..72
    vertically). We map (54, 42) -> (cx, cy) and use scale/108 as unit.
    """

    def pt(x, y):
        u = scale / 108.0
        return (cx + (x - 54) * u, cy + (y - 42) * u)

    def r_px(r):
        return r * scale / 108.0

    draw = ImageDraw.Draw(canvas, "RGBA")

    # Face — round, slightly creamy.
    face_cx, face_cy = pt(54, 50)
    face_r = r_px(22)
    draw.ellipse(
        [face_cx - face_r, face_cy - face_r, face_cx + face_r, face_cy + face_r],
        fill=(255, 240, 224, 255),
        outline=(255, 220, 200, 255),
        width=max(1, int(r_px(0.6))),
    )

    # Cheeks
    for (cxr, cyr) in [(38, 54), (70, 54)]:
        ccx, ccy = pt(cxr, cyr)
        cr = r_px(4)
        draw.ellipse(
            [ccx - cr, ccy - cr, ccx + cr, ccy + cr],
            fill=(255, 166, 158, 235),
        )

    # Eyes — quadratic curve sampled as polyline.
    eye_w = max(2, int(r_px(2.5)))
    eye_color = (112, 76, 56, 255)
    for (a, b, c) in [((42, 46), (46, 42), (50, 46)),
                      ((58, 46), (62, 42), (66, 46))]:
        pts = [pt(*p) for p in quad_bezier(a, b, c, steps=48)]
        draw.line(pts, fill=eye_color, width=eye_w, joint="curve")

    # Smile
    smile_w = max(2, int(r_px(2)))
    smile_pts = [pt(*p) for p in quad_bezier((50, 56), (54, 61), (58, 56), steps=48)]
    draw.line(smile_pts, fill=eye_color, width=smile_w, joint="curve")

    # Crown body — filled polygon, then outline.
    crown_pts = [pt(*p) for p in [
        (44, 28), (40, 16), (49, 21), (54, 12),
        (59, 21), (68, 16), (64, 28),
    ]]
    draw.polygon(crown_pts, fill=(255, 209, 102, 255), outline=(233, 196, 106, 255))

    # Crown gems
    for (gx, gy) in [(40, 15), (54, 11), (68, 15)]:
        cxp, cyp = pt(gx, gy)
        gr = r_px(1.6)
        draw.ellipse(
            [cxp - gr, cyp - gr, cxp + gr, cyp + gr],
            fill=(239, 71, 111, 255),
        )


def load_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = [
        r"C:\Windows\Fonts\malgunbd.ttf" if bold else r"C:\Windows\Fonts\malgun.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_icon():
    size = 512
    stops = [
        (0.0, (255, 243, 240)),
        (0.5, (255, 214, 224)),
        (1.0, (255, 179, 198)),
    ]
    bg = gradient_background((size, size), stops)
    # Soften the gradient a touch so the icon reads as a single warm mark.
    bg = bg.filter(ImageFilter.GaussianBlur(radius=0.5))
    draw_baby(bg, cx=size / 2, cy=size / 2 + 14, scale=size * 0.95)
    out = OUT_DIR / "icon_512.png"
    bg.convert("RGB").save(out, format="PNG", optimize=True)
    print(f"icon: {out}")


def make_feature_graphic():
    w, h = 1024, 500
    stops = [
        (0.0, (255, 243, 240)),
        (0.55, (255, 214, 224)),
        (1.0, (255, 179, 198)),
    ]
    bg = gradient_background((w, h), stops)

    # Soft vignette on the right for legibility of text.
    overlay = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    ov = ImageDraw.Draw(overlay)
    ov.rectangle([w * 0.45, 0, w, h], fill=(255, 255, 255, 60))
    bg = Image.alpha_composite(bg, overlay)

    # Baby on the left.
    draw_baby(bg, cx=w * 0.20, cy=h * 0.52, scale=380)

    # Title + tagline on the right.
    draw = ImageDraw.Draw(bg)
    title = "베베컵"
    tagline_lines = ["직접 고른 아기 사진으로", "만드는 가족 사진 월드컵"]

    title_font = load_font(100, bold=True)
    tagline_font = load_font(34, bold=False)

    title_color = (90, 35, 60, 255)
    tagline_color = (110, 70, 90, 255)

    title_x = int(w * 0.44)
    title_y = int(h * 0.26)
    draw.text((title_x, title_y), title, fill=title_color, font=title_font)

    tagline_y = title_y + 130
    for i, line in enumerate(tagline_lines):
        draw.text((title_x, tagline_y + i * 46), line, fill=tagline_color, font=tagline_font)

    out = OUT_DIR / "feature_graphic_1024x500.png"
    bg.convert("RGB").save(out, format="PNG", optimize=True)
    print(f"feature graphic: {out}")


if __name__ == "__main__":
    make_icon()
    make_feature_graphic()
