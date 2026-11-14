#!/usr/bin/env python3
"""
NovaStore depo oluşturucu
=========================

APK dosyalarından F-Droid uyumlu bir statik depo (repository) üretir.
Çıktı, NovaStore'un (ve F-Droid istemcilerinin) doğrudan okuyabildiği
`index-v1.json` + `icons/` + APK dosyalarıdır.

Kullanım:
    python3 tools/mkindex.py \
        --apk-dir ./apks \
        --out ./repo \
        --address https://repo.ornek.com/repo \
        --name "Benim Depom"

Yardımcı dizinler (opsiyonel):
    meta/<paket>.json        uygulama künyesi (ad, özet, açıklama, lisans…)
    icons/<paket>.png        uygulama ikonu (512x512 önerilir)
    shots/<paket>/<dil>/<ekranGoruntusuTuru>/*.png   ekran görüntüleri
        türler: phoneScreenshots | sevenInchScreenshots |
                tenInchScreenshots | tvScreenshots | wearScreenshots

Daha sonra dizini statik sunucuya koy:
    python3 -m http.server -d repo 8080
Emülatörden erişim: http://10.0.2.2:8080/repo  (NovaStore > Ayarlar > Depo ekle)
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
import time
from pathlib import Path
from typing import Any

BADGING_PACKAGE_RE = re.compile(r"^package: name='(?P<name>[^']+)' versionCode='(?P<vc>[^']+)' versionName='(?P<vn>[^']*)'")
BADGING_SDK_RE = re.compile(r"^(sdkVersion|minSdkVersion|targetSdkVersion):'(?P<value>[^']*)'")
BADGING_PERMISSION_RE = re.compile(r"^uses-permission(?:-sdk-\d+)?: name='(?P<name>[^']+)'")
BADGING_LABEL_RE = re.compile(r"^application-label(?:-[\w-]+)?:'(?P<label>[^']*)'")
BADGING_NATIVE_RE = re.compile(r"^native-code: (?P<codes>.+)$")
BADGING_FEATURE_RE = re.compile(r"^uses-feature: name='(?P<name>[^']+)'")

DEFAULT_CATEGORIES = ["Tools"]


def die(message: str) -> None:
    print(f"hata: {message}", file=sys.stderr)
    sys.exit(1)


def find_aapt2(explicit: str | None) -> str:
    if explicit:
        if not Path(explicit).exists():
            die(f"aapt2 bulunamadı: {explicit}")
        return explicit

    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidates: list[Path] = []
    if sdk:
        build_tools = Path(sdk) / "build-tools"
        if build_tools.is_dir():
            # en yeni sürümü seç
            versions = sorted(
                (p for p in build_tools.iterdir() if p.is_dir()),
                key=lambda p: p.name,
                reverse=True,
            )
            candidates += [v / "aapt2" for v in versions]
    found = shutil.which("aapt2")
    if found:
        candidates.append(Path(found))

    for candidate in candidates:
        if candidate.exists():
            return str(candidate)
    die("aapt2 bulunamadı; --aapt2 ile yol ver veya ANDROID_HOME tanımla")


def dump_badging(aapt2: str, apk: Path) -> dict[str, Any]:
    """aapt2 çıktısından paket künyesini çıkarır."""
    result = subprocess.run(
        [aapt2, "dump", "badging", str(apk)],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        die(f"aapt2 başarısız oldu ({apk.name}): {result.stderr.strip() or result.stdout.strip()}")

    info: dict[str, Any] = {
        "packageName": None,
        "versionCode": None,
        "versionName": None,
        "label": None,
        "minSdkVersion": None,
        "targetSdkVersion": None,
        "permissions": [],
        "nativecode": [],
        "features": [],
    }
    for line in result.stdout.splitlines():
        line = line.strip()
        if match := BADGING_PACKAGE_RE.match(line):
            info["packageName"] = match.group("name")
            info["versionCode"] = int(match.group("vc"))
            info["versionName"] = match.group("vn")
            continue
        if match := BADGING_SDK_RE.match(line):
            key = match.group(0).split(":")[0]
            try:
                value = int(match.group("value"))
            except ValueError:
                continue
            if key in ("sdkVersion", "minSdkVersion"):
                info["minSdkVersion"] = value
            else:
                info["targetSdkVersion"] = value
            continue
        if match := BADGING_PERMISSION_RE.match(line):
            name = match.group("name")
            if name not in info["permissions"]:
                info["permissions"].append(name)
            continue
        if match := BADGING_LABEL_RE.match(line):
            if info["label"] is None or match.group("label"):
                info["label"] = match.group("label")
            continue
        if match := BADGING_NATIVE_RE.match(line):
            info["nativecode"] = [
                code.strip().strip("'\"")
                for code in match.group("codes").split()
                if code.strip().strip("'\"")
            ]
            continue
        if match := BADGING_FEATURE_RE.match(line):
            info["features"].append(match.group("name"))
    if not info["packageName"]:
        die(f"paket adı okunamadı: {apk}")
    return info


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_meta(meta_dir: Path, package_name: str) -> dict[str, Any]:
    path = meta_dir / f"{package_name}.json"
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        die(f"meta dosyası geçersiz: {path} ({exc})")


def copy_screenshots(shots_dir: Path, package_name: str, out_dir: Path) -> dict[str, dict[str, list[str]]]:
    """shots/<pkg>/<locale>/<kind>/* -> repo/<pkg>/<locale>/<kind>/*"""
    source = shots_dir / package_name
    localized: dict[str, dict[str, list[str]]] = {}
    if not source.is_dir():
        return localized

    for locale_dir in sorted(p for p in source.iterdir() if p.is_dir()):
        locale = locale_dir.name
        for kind_dir in sorted(p for p in locale_dir.iterdir() if p.is_dir()):
            kind = kind_dir.name
            files: list[str] = []
            for asset in sorted(kind_dir.iterdir()):
                if not asset.is_file() or asset.suffix.lower() not in {".png", ".jpg", ".jpeg", ".webp"}:
                    continue
                target_dir = out_dir / package_name / locale / kind
                target_dir.mkdir(parents=True, exist_ok=True)
                shutil.copy2(asset, target_dir / asset.name)
                files.append(asset.name)
            if files:
                localized.setdefault(locale, {})[kind] = files
    return localized


def main() -> None:
    parser = argparse.ArgumentParser(
        description="APK dosyalarından NovaStore/F-Droid uyumlu depo indeksi üretir.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--apk-dir", required=True, help="APK dosyalarının bulunduğu dizin")
    parser.add_argument("--out", default="./repo", help="Çıktı dizini (depo kökü)")
    parser.add_argument("--address", required=True, help="Deponun yayınlanacağı tam adres, ör. https://x.com/repo")
    parser.add_argument("--name", default="NovaStore Deposu", help="Depo adı")
    parser.add_argument("--description", default="NovaStore için özel depo", help="Depo açıklaması")
    parser.add_argument("--website", default="", help="Depo web sitesi")
    parser.add_argument("--meta-dir", default="", help="Künye dizini (varsayılan: <apk-dir>/../meta)")
    parser.add_argument("--icon-dir", default="", help="İkon dizini (varsayılan: <apk-dir>/../icons)")
    parser.add_argument("--shots-dir", default="", help="Ekran görüntüsü dizini (varsayılan: <apk-dir>/../shots)")
    parser.add_argument("--maxage", type=int, default=168, help="İndeksin taze sayılacağı saat")
    parser.add_argument("--aapt2", default="", help="aapt2 yolunu elle ver")
    args = parser.parse_args()

    apk_dir = Path(args.apk_dir).resolve()
    if not apk_dir.is_dir():
        die(f"APK dizini yok: {apk_dir}")

    out_dir = Path(args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    address = args.address.rstrip("/")
    if not address.startswith(("http://", "https://")):
        die("--address http:// veya https:// ile başlamalı")

    meta_dir = Path(args.meta_dir).resolve() if args.meta_dir else apk_dir.parent / "meta"
    icon_dir = Path(args.icon_dir).resolve() if args.icon_dir else apk_dir.parent / "icons"
    shots_dir = Path(args.shots_dir).resolve() if args.shots_dir else apk_dir.parent / "shots"

    aapt2 = find_aapt2(args.aapt2 or None)

    apks = sorted(p for p in apk_dir.iterdir() if p.is_file() and p.suffix.lower() == ".apk")
    if not apks:
        die(f"{apk_dir} içinde .apk dosyası yok")

    apps: list[dict[str, Any]] = []
    packages: dict[str, list[dict[str, Any]]] = {}
    now_ms = int(time.time() * 1000)
    total_bytes = 0

    for apk in apks:
        info = dump_badging(aapt2, apk)
        package_name: str = info["packageName"]
        meta = load_meta(meta_dir, package_name)

        # APK'yı depoya kopyala (F-Droid adlandırması: <paket>_<surumKodu>.apk)
        # Böylece aynı dosya adına sahip APK'lar birbirini ezmez.
        target_apk = out_dir / f"{package_name}_{info['versionCode']}.apk"
        shutil.copy2(apk, target_apk)
        total_bytes += target_apk.stat().st_size

        version = {
            "packageName": package_name,
            "apkName": target_apk.name,
            "versionCode": info["versionCode"],
            "versionName": info["versionName"],
            "size": target_apk.stat().st_size,
            "hash": sha256_of(target_apk),
            "hashType": "sha256",
            "added": now_ms,
            "minSdkVersion": info["minSdkVersion"],
            "targetSdkVersion": info["targetSdkVersion"],
            "uses-permission": [[p, None] for p in info["permissions"]],
        }
        if info["features"]:
            version["features"] = info["features"]
        if info["nativecode"]:
            version["nativecode"] = info["nativecode"]
        if meta.get("releaseNotes"):
            version["releaseNotes"] = meta["releaseNotes"]

        packages.setdefault(package_name, []).append(version)
        print(f"  + {package_name}  v{info['versionName']} ({info['versionCode']}) -> {target_apk.name}")

        # İkon
        icon_name: str | None = None
        icon_source = Path(meta["icon"]) if meta.get("icon") else icon_dir / f"{package_name}.png"
        if not icon_source.is_absolute():
            icon_source = (meta_dir / icon_source) if meta.get("icon") else icon_source
        if icon_source.exists():
            (out_dir / "icons").mkdir(parents=True, exist_ok=True)
            shutil.copy2(icon_source, out_dir / "icons" / f"{package_name}.png")
            icon_name = f"{package_name}.png"

        # Ekran görüntüleri
        localized = copy_screenshots(shots_dir, package_name, out_dir)

        name = meta.get("name") or info["label"] or package_name
        summary = meta.get("summary") or ""
        description = meta.get("description") or summary

        app: dict[str, Any] = {
            "packageName": package_name,
            "name": name,
            "summary": summary,
            "description": description,
            "categories": meta.get("categories") or DEFAULT_CATEGORIES,
            "license": meta.get("license") or "Non-free",
            "added": now_ms,
            "lastUpdated": now_ms,
            "suggestedVersionCode": str(info["versionCode"]),
            "suggestedVersionName": info["versionName"],
        }
        for key, meta_key in (
            ("sourceCode", "sourceCode"),
            ("webSite", "webSite"),
            ("authorName", "authorName"),
            ("issueTracker", "issueTracker"),
        ):
            if meta.get(meta_key):
                app[key] = meta[meta_key]
        if icon_name:
            app["icon"] = icon_name

        # Yerelleştirmeler (varsa) + ekran görüntüleri
        app_localized: dict[str, Any] = {}
        for locale, values in (meta.get("localized") or {}).items():
            entry = {k: values[k] for k in ("name", "summary", "description", "whatsNew") if values.get(k)}
            if entry:
                app_localized[locale] = entry
        for locale, kinds in localized.items():
            app_localized.setdefault(locale, {}).update(kinds)
        if app_localized:
            app["localized"] = app_localized

        apps.append(app)

    apps.sort(key=lambda a: a["name"].lower())
    for versions in packages.values():
        versions.sort(key=lambda v: v["versionCode"] or 0, reverse=True)

    index = {
        "repo": {
            "timestamp": now_ms,
            "version": 30000,
            "maxage": args.maxage,
            "name": args.name,
            "address": address,
            "description": args.description,
            **({"webSite": args.website} if args.website else {}),
        },
        "requests": {"install": [], "uninstall": []},
        "apps": apps,
        "packages": packages,
    }

    index_path = out_dir / "index-v1.json"
    index_path.write_text(
        json.dumps(index, ensure_ascii=False, indent=1, sort_keys=True),
        encoding="utf-8",
    )

    print()
    print(f"depo oluşturuldu : {out_dir}")
    print(f"indeks           : {index_path}")
    print(f"uygulama         : {len(apps)}")
    print(f"apk sürümü       : {sum(len(v) for v in packages.values())}")
    print(f"toplam boyut     : {total_bytes / (1024 * 1024):.1f} MB")
    print(f"yayın adresi     : {address}")
    print()
    print("yayınla:  python3 -m http.server -d %s 8080" % out_dir)
    print("ekle:     NovaStore > Ayarlar > Depo ekle > %s" % address)


if __name__ == "__main__":
    main()
