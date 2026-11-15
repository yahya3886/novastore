#!/usr/bin/env python3
"""
NovaStore - yukarı akış (upstream) uygulama senkronizasyonu.

Depodaki seçili üçüncü taraf uygulamaları (Orbot, SimpleX Chat, Tor Browser)
resmî kaynaklarından kontrol eder; daha yeni bir sürüm varsa APK'yı apks/
altına indirir. Böylece depo düzenli güncellenebilir.

Kullanım:
    python3 tools/sync-upstream.py [--check] [--aapt2 /yol/aapt2]

    --check  yalnızca denetle, dosya değiştirme (değişiklik varsa çıkış kodu 10)

GitHub Actions ile zamanlanmış çalıştırma için .github/workflows/sync-upstream.yml
dosyasına bakın. İndeks üretimi deploy-repo workflow'u tarafından yapılır.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

ROOT = Path(__file__).resolve().parents[1]
APKS_DIR = ROOT / "apks"
USER_AGENT = "NovaStore-upstream-sync/1.0"

BADGING_PACKAGE_RE = re.compile(
    r"^package: name='(?P<name>[^']+)' versionCode='(?P<vc>[^']+)' versionName='(?P<vn>[^']*)'"
)


def log(msg: str) -> None:
    print(msg, flush=True)


def find_aapt2(explicit: str | None) -> str:
    if explicit:
        p = Path(explicit)
        if p.exists():
            return str(p)
        raise SystemExit(f"aapt2 bulunamadı: {explicit}")
    # ANDROID_HOME / SDK
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidates: list[Path] = []
    if sdk:
        bt = Path(sdk) / "build-tools"
        if bt.is_dir():
            for v in sorted(bt.iterdir(), reverse=True):
                candidates.append(v / "aapt2")
    # repo içindeki olası SDK kopyaları (CI ortamı)
    for base in [ROOT, ROOT.parent]:
        for bt in base.glob("**/build-tools/*/aapt2"):
            candidates.append(bt)
    found = shutil.which("aapt2")
    if found:
        candidates.append(Path(found))
    for c in candidates:
        if c.exists() and os.access(c, os.X_OK):
            return str(c)
    raise SystemExit("aapt2 bulunamadı; --aapt2 verin veya Android build-tools kurun")


def dump_badging(aapt2: str, apk: Path) -> dict:
    r = subprocess.run([aapt2, "dump", "badging", str(apk)], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"aapt2 başarısız ({apk.name}): {r.stderr.strip() or r.stdout.strip()}")
    for line in r.stdout.splitlines():
        m = BADGING_PACKAGE_RE.match(line.strip())
        if m:
            return {
                "packageName": m.group("name"),
                "versionCode": int(m.group("vc")),
                "versionName": m.group("vn"),
            }
    raise RuntimeError(f"paket bilgisi okunamadı: {apk}")


def http_get_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def download(url: str, dest: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(req, timeout=300) as resp, dest.open("wb") as out:
        shutil.copyfileobj(resp, out)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


@dataclass
class AppSpec:
    key: str  # apks/<key>.apk
    package: str
    resolve_url: Callable[[], str]
    note: str = ""


def resolve_simplex() -> str:
    # En son GitHub sürümü; arm64 (çoğu cihaz) APK'sı
    data = http_get_json("https://api.github.com/repos/simplex-chat/simplex-chat/releases/latest")
    assets = data.get("assets") or []
    # Tercih: simplex-aarch64.apk, yoksa simplex.apk
    preferred = ["simplex-aarch64.apk", "simplex.apk"]
    for name in preferred:
        for a in assets:
            if a.get("name") == name and a.get("browser_download_url"):
                return a["browser_download_url"]
    # yedek: içinde 'aarch64' ve '.apk' geçen ilk varlık
    for a in assets:
        n = (a.get("name") or "").lower()
        if "aarch64" in n and n.endswith(".apk") and a.get("browser_download_url"):
            return a["browser_download_url"]
    raise RuntimeError("SimpleX APK varlığı bulunamadı")


def resolve_orbot() -> str:
    # Guardian Project deposundaki evrensel (multi-arch) güncel paket
    return "https://guardianproject.info/fdroid/repo/orbot-latest.apk"


def resolve_torbrowser() -> str:
    # Guardian Project F-Droid indeksinden en yeni arm64-v8a (aarch64) sürüm
    idx = http_get_json("https://guardianproject.info/fdroid/repo/index-v1.json")
    pkgs = idx.get("packages", {}).get("org.torproject.torbrowser", [])
    arm64 = [p for p in pkgs if "arm64-v8a" in (p.get("nativecode") or []) and p.get("apkName")]
    if not arm64:
        raise RuntimeError("Tor Browser arm64 paketi bulunamadı")
    best = max(arm64, key=lambda p: int(p.get("versionCode") or 0))
    base = "https://guardianproject.info/fdroid/repo/"
    return base + best["apkName"]


SPECS: list[AppSpec] = [
    AppSpec("orbot", "org.torproject.android", resolve_orbot, "Guardian Project evrensel APK"),
    AppSpec("simplex", "chat.simplex.app", resolve_simplex, "GitHub releases (arm64)"),
    AppSpec("torbrowser-aarch64", "org.torproject.torbrowser", resolve_torbrowser, "Guardian Project (arm64-v8a)"),
]


def sync_one(spec: AppSpec, aapt2: str, check_only: bool) -> bool:
    log(f"\n== {spec.key} ({spec.package}) ==")
    try:
        url = spec.resolve_url()
    except Exception as e:
        log(f"  kaynak çözümlenemedi: {e}")
        return False
    log(f"  kaynak: {url}")

    with tempfile.TemporaryDirectory(prefix="ns-sync-") as td:
        tmp = Path(td) / f"{spec.key}.apk"
        try:
            download(url, tmp)
        except Exception as e:
            log(f"  indirme hatası: {e}")
            return False
        try:
            info = dump_badging(aapt2, tmp)
        except Exception as e:
            log(f"  APK okunamadı: {e}")
            return False
        if info.get("packageName") != spec.package:
            log(f"  beklenmeyen paket adı: {info.get('packageName')} != {spec.package}")
            return False

        new_vc = int(info["versionCode"])
        new_vn = info.get("versionName")
        target = APKS_DIR / f"{spec.key}.apk"

        old_vc = None
        if target.exists():
            try:
                old_vc = int(dump_badging(aapt2, target)["versionCode"])
            except Exception:
                old_vc = None

        if old_vc is not None and new_vc <= old_vc:
            log(f"  güncel: {old_vc} (yeni aday {new_vc})")
            return False

        action = "eklenecek" if old_vc is None else f"güncellenecek ({old_vc} -> {new_vc})"
        log(f"  sürüm: {new_vn} (versionCode {new_vc}) -> {action}")
        if check_only:
            return True
        APKS_DIR.mkdir(parents=True, exist_ok=True)
        # atomik sayılabilecek taşıma
        shutil.move(str(tmp), str(target))
        log(f"  yazıldı: {target} ({target.stat().st_size} bayt)")
        return True


def main() -> int:
    ap = argparse.ArgumentParser(description="Upstream APK senkronizasyonu")
    ap.add_argument("--check", action="store_true", help="Yalnızca kontrol et, dosya değiştirme")
    ap.add_argument("--aapt2", default="", help="aapt2 yolu")
    ap.add_argument("--only", default="", help="Yalnızca belirtilen anahtarlar (virgülle ayrılmış)")
    args = ap.parse_args()

    aapt2 = find_aapt2(args.aapt2 or None)
    log(f"aapt2: {aapt2}")

    specs = SPECS
    if args.only:
        wanted = {s.strip() for s in args.only.split(",") if s.strip()}
        specs = [s for s in SPECS if s.key in wanted]
        if not specs:
            raise SystemExit(f"bilinmeyen anahtar(lar): {args.only}")

    changed = False
    for spec in specs:
        if sync_one(spec, aapt2, args.check):
            changed = True

    if changed:
        log("\nSonuç: güncelleme var." + (" (--check)" if args.check else ""))
        return 10 if args.check else 0
    log("\nSonuç: tüm uygulamalar güncel.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
