#!/usr/bin/env python3
"""Launch a plain VANILLA Minecraft client (no Fabric, no Polymer, no launcher, no login) against
the dev server. This is the client that counts for testing a Polymer mod: everything the player
sees comes from the server and the generated resource pack.

    python3 tools/vanilla_client.py [--username Name] [--server localhost:25565]

With --iris it is instead a Fabric client with Sodium, Iris and Fabric API (downloaded from
Modrinth for the game version) in run-iris/, sharing run-vanilla/'s assets and libraries. Use
--shaderpacks to also fetch popular shaderpacks into run-iris/shaderpacks and write a "+ovvar"
copy of each with the shader patcher, so both versions can be compared in Iris's pack list.

Downloads the client jar, its libraries and the asset objects for the version in
gradle.properties into run-vanilla/ (one-time, a few hundred MB), then starts the game with an
offline profile. Needs Java 25: JAVA_HOME, `/usr/libexec/java_home -v 25`, or the JDK that
Gradle's toolchain provisioning put under ~/.gradle/jdks.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import glob
import hashlib
import json
import os
import pathlib
import platform
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
RUN = ROOT / "run-vanilla"
IRIS_RUN = ROOT / "run-iris"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net/"
FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json"
MODRINTH = "https://api.modrinth.com/v2"
IRIS_MODS = ["fabric-api", "sodium", "iris"]
SHADERPACKS = ["complementary-reimagined", "complementary-unbound", "bsl-shaders", "photon-shader",
               "solas-shader", "bliss-shader", "makeup-ultra-fast-shaders", "super-duper-vanilla"]
PATCHER = ROOT / "build" / "patcher-resources" / "assets" / "ovvar" / "shaderpatcher" / "OvvarShaderPatcher.jar"


def loader_version() -> str:
    m = re.search(r"^\s*loader_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M)
    return m.group(1).strip() if m else "0.19.2"


def modrinth_file(slug: str, game_version: str | None, loader: str | None, mods: pathlib.Path | None = None) -> tuple[str | None, str]:
    """(url, filename) of the newest version of a Modrinth project, filtered when asked."""
    q = ""
    if game_version:
        q += "?game_versions=" + urllib.parse.quote(json.dumps([game_version]))
    if loader:
        q += ("&" if q else "?") + "loaders=" + urllib.parse.quote(json.dumps([loader]))
    try:
        versions = fetch_json(f"{MODRINTH}/project/{slug}/version{q}")
    except (urllib.error.URLError, OSError) as e:
        # Offline: whatever copy of the mod is already in place.
        have = sorted(p for p in mods.glob(f"{slug.split('-')[0]}*.jar")) if mods else []
        if not have:
            raise
        print(f"offline: keeping {have[-1].name} ({e})")
        return None, have[-1].name
    if not versions:
        sys.exit(f"Modrinth: no {slug} for {game_version or 'any version'}")
    # Releases over betas: a Sodium beta may declare it breaks the current Iris.
    chosen = next((v for v in versions if v["version_type"] == "release"), versions[0])
    f = next((f for f in chosen["files"] if f.get("primary")), chosen["files"][0])
    return f["url"], f["filename"]


def fabric_libraries(version: str, libs: pathlib.Path) -> tuple[list[pathlib.Path], str]:
    """Download the Fabric loader profile's libraries; returns (classpath entries, main class)."""
    try:
        profile = fetch_json(FABRIC_META.format(mc=version, loader=loader_version()))
    except (urllib.error.URLError, OSError) as e:
        # Offline: the loader, Mixin and ASM jars a previous run left behind.
        have = sorted(libs.glob("net/fabricmc/*/*/*.jar")) + sorted(libs.glob("org/ow2/asm/*/*/*.jar"))
        if not any("fabric-loader" in p.name for p in have):
            raise
        print(f"offline: using {len(have)} local Fabric libraries ({e})")
        return have, "net.fabricmc.loader.impl.launch.knot.KnotClient"
    out = []
    for lib in profile["libraries"]:
        group, artifact, ver = lib["name"].split(":")[:3]
        rel = f"{group.replace('.', '/')}/{artifact}/{ver}/{artifact}-{ver}.jar"
        dest = libs / rel
        download(lib["url"].rstrip("/") + "/" + rel, dest, lib.get("sha1"))
        out.append(dest)
    return out, profile["mainClass"]


def minecraft_version() -> str:
    m = re.search(r"^\s*minecraft_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M)
    if not m:
        sys.exit("minecraft_version not found in gradle.properties")
    return m.group(1).strip()


def fetch_json(url: str):
    """A JSON document, cached under run-vanilla/meta so a run without network still works once everything is there."""
    cache = RUN / "meta" / (hashlib.sha1(url.encode()).hexdigest() + ".json")
    try:
        with urllib.request.urlopen(url) as r:
            data = json.load(r)
    except (urllib.error.URLError, OSError) as e:
        if not cache.exists():
            raise
        print(f"offline: using cached {url} ({e})")
        return json.loads(cache.read_text())
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps(data))
    return data


def download(url: str, dest: pathlib.Path, sha1: str | None = None) -> None:
    if dest.exists() and (sha1 is None or hashlib.sha1(dest.read_bytes()).hexdigest() == sha1):
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(url) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    if sha1 and hashlib.sha1(tmp.read_bytes()).hexdigest() != sha1:
        tmp.unlink()
        raise IOError(f"hash mismatch for {url}")
    tmp.replace(dest)


def os_name() -> str:
    return {"Darwin": "osx", "Windows": "windows"}.get(platform.system(), "linux")


def rules_allow(rules, osn: str) -> bool:
    if not rules:
        return True
    allowed = False
    for rule in rules:
        applies = True
        if "os" in rule:
            o = rule["os"]
            if "name" in o and o["name"] != osn:
                applies = False
            if "arch" in o and o["arch"] == "x86" and platform.machine() not in ("i386", "i686", "x86"):
                applies = False
        if "features" in rule:  # demo, custom resolution, quick play… we set none
            applies = False
        if applies:
            allowed = rule["action"] == "allow"
    return allowed


def find_java() -> str:
    """Java 25: JAVA_HOME, macOS java_home, the JDK Gradle's toolchain provisioning downloaded
    (~/.gradle/jdks/<vendor-25-…>/<jdk-25…>/Contents/Home on macOS), Homebrew, system JVMs."""
    home = pathlib.Path.home()
    candidates: list[pathlib.Path] = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(pathlib.Path(os.environ["JAVA_HOME"]) / "bin" / "java")
    if platform.system() == "Darwin":
        try:
            out = subprocess.run(["/usr/libexec/java_home", "-v", "25"], capture_output=True, text=True, check=True).stdout.strip()
            if out:
                candidates.append(pathlib.Path(out) / "bin" / "java")
        except (subprocess.CalledProcessError, FileNotFoundError):
            pass
    patterns = [
        str(home / ".gradle/jdks/**/bin/java"),
        "/Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java",
        str(home / "Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java"),
        "/opt/homebrew/opt/openjdk*/bin/java",
        "/usr/local/opt/openjdk*/bin/java",
        "/usr/lib/jvm/*/bin/java",
        "/opt/jdk25/bin/java",
    ]
    for pat in patterns:
        candidates += [pathlib.Path(p) for p in sorted(glob.glob(pat, recursive=True))]
    seen = set()
    tried = []
    for c in candidates:
        if c in seen or not c.exists():
            continue
        seen.add(c)
        try:
            r = subprocess.run([str(c), "-version"], capture_output=True, text=True, timeout=20)
            out = (r.stderr or "") + (r.stdout or "")
        except (OSError, subprocess.TimeoutExpired) as e:
            tried.append(f"{c}: {e}")
            continue
        first = out.strip().splitlines()[0] if out.strip() else "(no output)"
        tried.append(f"{c}: {first}")
        if re.search(r'version "25', out):
            print(f"using Java: {c}")
            return str(c)
    print("Java candidates checked:")
    for t in tried:
        print("  " + t)
    if not tried:
        print("  (none found)")
    sys.exit("No Java 25 found. Run ./gradlew build once (Gradle downloads a JDK 25 into ~/.gradle/jdks) "
             "or set JAVA_HOME to a JDK 25.")


def offline_uuid(name: str) -> str:
    """Same as Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name), which offline servers use."""
    return str(uuid.UUID(bytes=hashlib.md5(f"OfflinePlayer:{name}".encode()).digest(), version=3))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--username", default="Dev")
    ap.add_argument("--server", default="localhost:25565", help="auto-join this server (use '' to open the title screen)")
    ap.add_argument("--dry-run", action="store_true", help="download everything, don't launch")
    ap.add_argument("--skip-assets", action="store_true", help="faster first run; no sounds or English names")
    ap.add_argument("--iris", action="store_true", help="Fabric + Sodium + Iris client in run-iris/")
    ap.add_argument("--shaderpacks", action="store_true", help="with --iris: fetch popular shaderpacks and their +ovvar copies")
    args = ap.parse_args()

    version = minecraft_version()
    osn = os_name()
    RUN.mkdir(exist_ok=True)

    vjson_path = RUN / "versions" / f"{version}.json"
    try:
        manifest = fetch_json(MANIFEST)
    except (urllib.error.URLError, OSError) as e:
        if not vjson_path.exists():
            raise
        print(f"offline: using {vjson_path} ({e})")
    else:
        entry = next((v for v in manifest["versions"] if v["id"] == version), None)
        if entry is None:
            sys.exit(f"version {version} not in Mojang manifest")
        download(entry["url"], vjson_path, entry.get("sha1"))
    vjson = json.loads(vjson_path.read_text())

    print(f"vanilla {version} for {osn}/{platform.machine()} into {RUN}")
    client_jar = RUN / "versions" / f"{version}.jar"
    download(vjson["downloads"]["client"]["url"], client_jar, vjson["downloads"]["client"]["sha1"])

    classpath = []
    libs = RUN / "libraries"
    jobs = []
    for lib in vjson["libraries"]:
        if not rules_allow(lib.get("rules"), osn):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if art:
            dest = libs / art["path"]
            classpath.append(dest)
            jobs.append((art["url"], dest, art["sha1"]))
    with concurrent.futures.ThreadPoolExecutor(16) as pool:
        list(pool.map(lambda j: download(*j), jobs))
    print(f"{len(classpath)} libraries")

    assets = RUN / "assets"
    index_id = vjson["assetIndex"]["id"]
    index_path = assets / "indexes" / f"{index_id}.json"
    download(vjson["assetIndex"]["url"], index_path, vjson["assetIndex"]["sha1"])
    if not args.skip_assets:
        objects = json.loads(index_path.read_text())["objects"]
        jobs = []
        for obj in objects.values():
            h = obj["hash"]
            dest = assets / "objects" / h[:2] / h
            if not dest.exists():
                jobs.append((RESOURCES + h[:2] + "/" + h, dest, h))
        if jobs:
            print(f"downloading {len(jobs)} asset objects…")
            with concurrent.futures.ThreadPoolExecutor(32) as pool:
                list(pool.map(lambda j: download(*j), jobs))
        print("assets ready")

    game_dir = RUN
    main_class = vjson["mainClass"]
    if args.iris:
        game_dir = IRIS_RUN
        mods = game_dir / "mods"
        mods.mkdir(parents=True, exist_ok=True)
        for slug in IRIS_MODS:
            url, filename = modrinth_file(slug, version, "fabric", mods)
            for old in mods.glob(f"{slug.split('-')[0]}*.jar"):
                if old.name != filename:
                    old.unlink()
            if url:
                download(url, mods / filename)
            print(f"mod: {filename}")
        fabric_cp, main_class = fabric_libraries(version, libs)
        classpath = fabric_cp + classpath
        if args.shaderpacks:
            packs = game_dir / "shaderpacks"
            packs.mkdir(exist_ok=True)
            fetched = []
            for slug in SHADERPACKS:
                url, filename = modrinth_file(slug, None, None)
                dest = packs / filename
                download(url, dest)
                fetched.append(dest)
                print(f"shaderpack: {filename}")
            if not PATCHER.exists():
                sys.exit(f"{PATCHER} missing: run ./gradlew patcherJar first")
            subprocess.run([find_java(), "-jar", str(PATCHER)] + [str(p) for p in fetched], check=False)

    if args.dry_run:
        return

    java = find_java()
    cmd = [java]
    if osn == "osx":
        cmd.append("-XstartOnFirstThread")
    cmd += ["-Xmx3G", "-cp", os.pathsep.join(str(p) for p in classpath + [client_jar]), main_class,
            "--username", args.username, "--version", version,
            "--gameDir", str(game_dir), "--assetsDir", str(assets), "--assetIndex", index_id,
            "--uuid", offline_uuid(args.username).replace("-", ""), "--accessToken", "0",
            "--userType", "legacy", "--versionType", "release"]
    if args.server:
        host, _, port = args.server.partition(":")
        cmd += ["--quickPlayMultiplayer", f"{host}:{port or 25565}"]
    print("launching:", " ".join(cmd[:4]), "…")
    if os.environ.get("OVVAR_PRINT_CMD"):
        print(" ".join(cmd)); return
    os.execv(java, cmd)


if __name__ == "__main__":
    main()
