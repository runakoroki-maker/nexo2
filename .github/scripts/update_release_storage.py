#!/usr/bin/env python3
"""Updates public-available-release/ metadata, release notes, README, and APK copies."""

import argparse
import datetime
import json
import os
import shutil
from pathlib import Path


def load_existing_version_data(version_file: Path) -> dict:
    if version_file.exists():
        try:
            data = json.loads(version_file.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                return data
        except (json.JSONDecodeError, OSError):
            pass
    return {"history": []}


def build_release_entry(args, store_binary: bool, timestamp_utc: str) -> dict:
    repo = args.repo
    tag = args.tag
    apk_filename = args.apk_filename
    latest_filename = args.latest_filename

    release_asset_url = f"https://github.com/{repo}/releases/download/{tag}/{apk_filename}"
    latest_release_asset_url = (
        f"https://github.com/{repo}/releases/latest/download/{latest_filename}"
    )

    entry = {
        "tag": tag,
        "versionName": args.version_name,
        "versionCode": int(args.version_code),
        "packageId": args.package_id,
        "minSdk": int(args.min_sdk),
        "targetSdk": int(args.target_sdk),
        "apkFilename": apk_filename,
        "latestApkFilename": latest_filename,
        "sha256": args.sha256,
        "sizeBytes": int(args.size_bytes),
        "sizeHuman": args.size_human,
        "buildStatus": "SUCCESS",
        "publishedAtUtc": timestamp_utc,
        "commitSha": args.commit_sha,
        "workflowRunUrl": args.run_url,
        "releaseAssetUrl": release_asset_url,
        "latestReleaseAssetUrl": latest_release_asset_url,
        "storedBinaryInRepo": store_binary,
        "repoApkPath": f"public-available-release/{apk_filename}" if store_binary else None,
        "releaseNotes": args.release_notes.strip()
        or f"Automated signed public release build for NEXO {tag}.",
    }
    return entry


def write_version_json(
    storage_dir: Path, entry: dict, existing_data: dict
) -> list:
    history = existing_data.get("history", [])
    if not isinstance(history, list):
        history = []

    # Preserve all prior versioned releases; update existing entry only if same tag is rebuilt.
    filtered_history = [h for h in history if isinstance(h, dict) and h.get("tag") != entry["tag"]]
    updated_history = [entry] + filtered_history

    payload = {
        "appName": "NEXO",
        "studio": "RK Animation Studios",
        "packageId": entry["packageId"],
        "latestTag": entry["tag"],
        "latestVersionName": entry["versionName"],
        "latestVersionCode": entry["versionCode"],
        "latestApkFilename": entry["latestApkFilename"],
        "versionedApkFilename": entry["apkFilename"],
        "buildStatus": "SUCCESS",
        "lastUpdatedUtc": entry["publishedAtUtc"],
        "sha256": entry["sha256"],
        "sizeHuman": entry["sizeHuman"],
        "supportedAndroid": {
            "minSdk": entry["minSdk"],
            "minAndroidVersion": "Android 7.0 (Nougat)",
            "targetSdk": entry["targetSdk"],
            "targetAndroidVersion": "Android 16",
        },
        "downloadUrls": {
            "latestReleaseAsset": entry["latestReleaseAssetUrl"],
            "versionedReleaseAsset": entry["releaseAssetUrl"],
            "repoLatestApk": (
                f"public-available-release/{entry['latestApkFilename']}"
                if entry["storedBinaryInRepo"]
                else None
            ),
        },
        "history": updated_history,
    }

    (storage_dir / "version.json").write_text(
        json.dumps(payload, indent=2) + "\n", encoding="utf-8"
    )
    return updated_history


def write_build_status_md(storage_dir: Path, entry: dict) -> None:
    storage_mode = (
        "Stored in `public-available-release/` and attached to GitHub Releases"
        if entry["storedBinaryInRepo"]
        else "Stored in GitHub Release Assets (binary exceeded Git file threshold)"
    )

    content = f"""# NEXO Public Release Build Status

| Field | Value |
| :--- | :--- |
| **Build Status** | `BUILD SUCCESSFUL` |
| **Latest Version** | `{entry["tag"]}` (`versionName`: `{entry["versionName"]}`, `versionCode`: `{entry["versionCode"]}`) |
| **Package ID** | `{entry["packageId"]}` |
| **Latest APK** | `{entry["latestApkFilename"]}` |
| **Versioned APK** | `{entry["apkFilename"]}` |
| **APK Size** | `{entry["sizeHuman"]}` (`{entry["sizeBytes"]}` bytes) |
| **SHA-256** | `{entry["sha256"]}` |
| **Binary Storage** | {storage_mode} |
| **Last Updated (UTC)** | `{entry["publishedAtUtc"]}` |
| **Source Commit** | `{entry["commitSha"]}` |
| **Workflow Run** | [View GitHub Actions Run]({entry["workflowRunUrl"]}) |

> Previous working APKs are never overwritten on failed builds. If a future build fails, the last verified build above remains intact.
"""
    (storage_dir / "BUILD_STATUS.md").write_text(content, encoding="utf-8")


def write_release_notes_md(storage_dir: Path, history: list) -> None:
    lines = [
        "# NEXO – Public Release Notes",
        "",
        "Release notes for all verified public APK builds published through GitHub Releases.",
        "",
    ]

    for item in history:
        tag = item.get("tag", "unknown")
        v_name = item.get("versionName", "")
        v_code = item.get("versionCode", "")
        published = item.get("publishedAtUtc", "")
        apk_name = item.get("apkFilename", "")
        asset_url = item.get("releaseAssetUrl", "")
        notes = item.get("releaseNotes", "").strip()

        lines.extend(
            [
                f"## {tag} (v{v_name} / build {v_code})",
                "",
                f"- **Published (UTC):** `{published}`",
                f"- **APK Asset:** [{apk_name}]({asset_url})",
                f"- **SHA-256:** `{item.get('sha256', '')}`",
                "",
                notes,
                "",
                "---",
                "",
            ]
        )

    (storage_dir / "RELEASE_NOTES.md").write_text("\n".join(lines), encoding="utf-8")


def write_readme_md(storage_dir: Path, entry: dict, history: list, repo: str) -> None:
    direct_latest_url = entry["latestReleaseAssetUrl"]
    direct_versioned_url = entry["releaseAssetUrl"]
    repo_latest_link = (
        f"[`{entry['latestApkFilename']}`](./{entry['latestApkFilename']})"
        if entry["storedBinaryInRepo"]
        else f"[`{entry['latestApkFilename']}`]({direct_latest_url})"
    )

    history_rows = []
    for item in history:
        t = item.get("tag", "")
        vn = item.get("versionName", "")
        vc = item.get("versionCode", "")
        fn = item.get("apkFilename", "")
        sz = item.get("sizeHuman", "")
        url = item.get("releaseAssetUrl", "")
        repo_path = item.get("repoApkPath")
        if repo_path and (storage_dir / fn).exists():
            dl_cell = f"[GitHub Release]({url}) · [Folder Copy](./{fn})"
        else:
            dl_cell = f"[GitHub Release]({url})"
        history_rows.append(f"| `{t}` | `{vn}` (`{vc}`) | `{fn}` | `{sz}` | {dl_cell} |")

    history_table = "\n".join(history_rows)

    content = f"""# NEXO – Public Available Release

Central distribution folder for verified, signed public Android APK releases of **NEXO** by **RK Animation Studios**.

---

## Latest Release Information

- **Latest Version:** `{entry["tag"]}` (`versionName`: `{entry["versionName"]}`, `versionCode`: `{entry["versionCode"]}`)
- **Latest Build Status:** `BUILD SUCCESSFUL` ([Build Details](./BUILD_STATUS.md))
- **Updated (UTC):** `{entry["publishedAtUtc"]}`
- **Application ID:** `{entry["packageId"]}`
- **APK Size:** `{entry["sizeHuman"]}`
- **SHA-256:** `{entry["sha256"]}`

---

## Direct Download Links

- **Latest Public APK (`{entry["latestApkFilename"]}`):**
  - **GitHub Releases Direct Download:** {direct_latest_url}
  - **Folder File:** {repo_latest_link}
- **Versioned Archive APK (`{entry["apkFilename"]}`):**
  - **GitHub Releases Direct Download:** {direct_versioned_url}
- **Release Notes:** [RELEASE_NOTES.md](./RELEASE_NOTES.md)

---

## Supported Device & Runtime Requirements

Derived directly from `app/build.gradle.kts` and `AndroidManifest.xml`:

- **Minimum Supported Android Version:** Android 7.0 Nougat (`minSdk = 24`)
- **Target / Compiled Android SDK:** Android 16 (`targetSdk = 36`, `compileSdk = 36`)
- **Orientation:** Landscape Only (`sensorLandscape`)
- **Connectivity:** Online-only (active internet connection required)
- **Initial Launch Regions:** Netherlands (`NL`) & Indonesia (`ID`)

---

## How to Download & Install the APK

1. **Download** the latest signed public APK using the direct link above:
   - [`{entry["latestApkFilename"]}`]({direct_latest_url})
2. **Verify (Optional):** Compare the file's SHA-256 checksum against `{entry["sha256"]}`.
3. **Enable APK Installation on Android:**
   - Open the downloaded `.apk` file from your browser downloads or Android Files app.
   - If prompted, tap **Settings** and enable **Allow from this source** for your browser or file manager.
4. **Install & Launch:**
   - Tap **Install**, then open **NEXO** (ensure an active internet connection and supported region routing).

---

## Preserved Release History

| Release Tag | Version | APK Filename | Size | Download |
| :--- | :--- | :--- | :--- | :--- |
{history_table}
"""
    (storage_dir / "README.md").write_text(content, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--storage-dir", required=True)
    parser.add_argument("--apk-source", required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--apk-filename", required=True)
    parser.add_argument("--latest-filename", default="NEXO-Public-Latest.apk")
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--size-bytes", required=True)
    parser.add_argument("--size-human", required=True)
    parser.add_argument("--release-notes", default="")
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--package-id", default="com.aistudio.nexo.btr")
    parser.add_argument("--min-sdk", default="24")
    parser.add_argument("--target-sdk", default="36")
    parser.add_argument("--max-git-apk-bytes", type=int, default=99614720)
    parser.add_argument("--skip-binary", action="store_true")
    args = parser.parse_args()

    storage_dir = Path(args.storage_dir)
    storage_dir.mkdir(parents=True, exist_ok=True)

    apk_source = Path(args.apk_source)
    size_bytes = int(args.size_bytes)
    store_binary = (not args.skip_binary) and (size_bytes < args.max_git_apk_bytes)

    if store_binary:
        if not apk_source.is_file():
            raise FileNotFoundError(f"Verified APK not found: {apk_source}")
        # Always update NEXO-Public-Latest.apk to the newest successful build
        shutil.copy2(apk_source, storage_dir / args.latest_filename)
        # Preserve versioned copy without deleting previous versioned APKs
        shutil.copy2(apk_source, storage_dir / args.apk_filename)

    timestamp_utc = datetime.datetime.now(datetime.timezone.utc).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )
    existing_data = load_existing_version_data(storage_dir / "version.json")
    entry = build_release_entry(args, store_binary, timestamp_utc)
    updated_history = write_version_json(storage_dir, entry, existing_data)
    write_build_status_md(storage_dir, entry)
    write_release_notes_md(storage_dir, updated_history)
    write_readme_md(storage_dir, entry, updated_history, args.repo)


if __name__ == "__main__":
    main()
