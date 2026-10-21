"""Smoke one built jar: the band's jar for a loader, on that loader's real server, with every pin read from
gradle.properties. Wraps tools/server_smoke.py.

Usage: python tools/smoke_band.py --band 26.2 --loader {fabric,quilt,neoforge,forge} --work EMPTY_DIR
                                  [--map-out NEW_PNG]
  fabric, quilt: the band's Fabric jar on smoke_floor.<band> with smoke_fabric_api.<band> (downloaded, and
                 checked against the SHA-1 Maven publishes); quilt uses quilt_loader_version.
  neoforge, forge: the band's jar on the band itself with neoforge_version / forge_version.<band>.
Run from the repo root after `./gradlew build` (or the band's jar task). Exits with server_smoke.py's code.
"""
import argparse
import glob
import hashlib
import os
import subprocess
import sys
import urllib.request

FABRIC_API = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/%s/fabric-api-%s.jar"


def properties():
    props = {}
    with open("gradle.properties", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


def need(props, key):
    if key not in props:
        sys.exit("gradle.properties has no %s" % key)
    return props[key]


def one_jar(pattern):
    jars = [j for j in glob.glob(pattern) if not j.endswith("-sources.jar")]
    if len(jars) != 1:
        sys.exit("expected exactly one jar matching %s, found %s; build the band first" % (pattern, jars))
    return jars[0]


def fetch(url, path):
    request = urllib.request.Request(url, headers={"User-Agent": "the-continent-server-smoke/1"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def fabric_api(version, work_parent):
    path = os.path.join(work_parent, "fabric-api-%s.jar" % version)
    url = FABRIC_API % (version, version)
    data = fetch(url, path)
    expected = fetch(url + ".sha1", path).decode().split()[0]
    if hashlib.sha1(data).hexdigest() != expected:
        sys.exit("Fabric API %s doesn't match the SHA-1 Maven publishes" % version)
    with open(path, "wb") as f:
        f.write(data)
    return path


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--band", required=True)
    p.add_argument("--loader", required=True, choices=["fabric", "quilt", "neoforge", "forge"])
    p.add_argument("--work", required=True)
    p.add_argument("--map-out", help="also export the biome map to this new file (server_smoke.py --map-out)")
    a = p.parse_args()
    props = properties()
    band = a.band
    work = os.path.abspath(a.work)
    work_parent = os.path.dirname(work)
    os.makedirs(work_parent, exist_ok=True)

    if a.loader in ("fabric", "quilt"):
        label = need(props, "band_label." + band)
        jar = one_jar("fabric/versions/%s/build/libs/the_continent-*+fabric-mc%s.jar" % (band, label))
        minecraft = need(props, "smoke_floor." + band)
        mods = [jar, fabric_api(need(props, "smoke_fabric_api." + band), work_parent)]
        version = need(props, "loader_version" if a.loader == "fabric" else "quilt_loader_version")
    else:
        label = need(props, "%s_band_label.%s" % (a.loader, band))
        jar = one_jar("%s/versions/%s/build/libs/the_continent-*+%s-mc%s.jar" % (a.loader, band, a.loader, label))
        minecraft = band
        mods = [jar]
        version = need(props, "%s_version.%s" % (a.loader, band))

    command = [sys.executable, "-I", os.path.join("tools", "server_smoke.py"), "--loader", a.loader,
               "--minecraft", minecraft, "--loader-version", version, "--work", work]
    for mod in mods:
        command += ["--mod", mod]
    if a.map_out:
        command += ["--map-out", os.path.abspath(a.map_out)]
    print("smoke:", a.loader, version, "on Minecraft", minecraft, "with", ", ".join(os.path.basename(m) for m in mods))
    sys.exit(subprocess.call(command))


main()
