"""Boot a real dedicated server with a built jar of the mod on a Continent world, and check it is ours.

Usage:
  python tools/server_smoke.py --loader {fabric,quilt,neoforge,forge} --minecraft VERSION --loader-version VERSION
      --mod JAR [--mod JAR ...] --work EMPTY_DIR [--timeout SECONDS] [--map-out NEW_PNG]

Installs the loader's server into WORK (which must be empty or missing), adds the mods, writes a server.properties
with level-type the_continent:continent, starts the server and waits for "Done". Then it checks:
  - the mod logged "[the_continent] initialized";
  - /locate biome minecraft:mushroom_fields answers near (-1250, -50), where the continent's layout puts it, so the
    biome source is ours and not vanilla's;
  - the world's generator settings name the_continent:continent;
  - after /stop the server exits with code 0, and the log has no ERROR or FATAL line.
With --map-out, it also runs /continent map (seed 12345, as the README's map) and copies the PNG to NEW_PNG, which
must not exist yet. Exits 0 only if every check passed; prints each check and, on failure, the log's last lines. The log is kept at
WORK/smoke.log. Needs Java 25 on PATH.
"""
import argparse
import gzip
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import zlib

MUSHROOM_FIELDS = (-1250, -50)
MUSHROOM_TOLERANCE = 300
ERROR_LINE = re.compile(r"(/|\[)(ERROR|FATAL)\]")
SILENCE = 180
MAP_REPLY = re.compile(r"Wrote (continent-maps/map-[0-9-]+\.png)")
LOCATE_REPLY = re.compile(r"minecraft:mushroom_fields is at \[(-?\d+), [^,]+, (-?\d+)\]")


def fail(message):
    print("SMOKE FAIL:", message)
    sys.exit(1)


def download(url, path):
    print("downloading", url)
    # Forge's Maven answers Python's default user agent with 403.
    request = urllib.request.Request(url, headers={"User-Agent": "the-continent-server-smoke/1"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response, open(path, "wb") as out:
            out.write(response.read())
    except OSError as e:
        fail("downloading %s: %s" % (url, e))


def run(cmd, cwd):
    print("running", " ".join(cmd))
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, errors="replace")
    if result.returncode != 0:
        print(result.stdout[-4000:], result.stderr[-4000:])
        fail("%s exited with %d" % (cmd[0], result.returncode))


def install(loader, minecraft, version, work):
    """Installs the server into work and returns the command that starts it."""
    if loader == "fabric":
        installers = json.load(urllib.request.urlopen("https://meta.fabricmc.net/v2/versions/installer", timeout=60))
        installer = next(i["version"] for i in installers if i["stable"])
        download("https://meta.fabricmc.net/v2/versions/loader/%s/%s/%s/server/jar" % (minecraft, version, installer),
                 os.path.join(work, "server.jar"))
        return ["java", "-Xmx3G", "-jar", "server.jar", "nogui"]
    if loader == "quilt":
        repo = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/"
        metadata = os.path.join(work, "quilt-installer-metadata.xml")
        download(repo + "maven-metadata.xml", metadata)
        release = re.search(r"<release>([^<]+)</release>", open(metadata, encoding="utf-8").read())
        if release is None:
            fail("no <release> in the Quilt installer's maven metadata")
        installer = os.path.join(work, "quilt-installer.jar")
        download("%s%s/quilt-installer-%s.jar" % (repo, release.group(1), release.group(1)), installer)
        run(["java", "-jar", installer, "install", "server", minecraft, version, "--download-server",
             "--install-dir=" + work], work)
        return ["java", "-Xmx3G", "-jar", "quilt-server-launch.jar", "nogui"]
    if loader == "neoforge":
        installer = os.path.join(work, "installer.jar")
        download("https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar"
                 % (version, version), installer)
        run(["java", "-jar", installer, "--install-server", work], work)
        args = "win_args.txt" if os.name == "nt" else "unix_args.txt"
        return ["java", "-Xmx3G", "@libraries/net/neoforged/neoforge/%s/%s" % (version, args), "nogui"]
    if loader == "forge":
        full = "%s-%s" % (minecraft, version)
        installer = os.path.join(work, "installer.jar")
        download("https://maven.minecraftforge.net/net/minecraftforge/forge/%s/forge-%s-installer.jar" % (full, full),
                 installer)
        run(["java", "-jar", installer, "--installServer", work], work)
        shim = "forge-%s-shim.jar" % full
        if os.path.exists(os.path.join(work, shim)):
            return ["java", "-Xmx3G", "-jar", shim, "nogui"]
        args = "win_args.txt" if os.name == "nt" else "unix_args.txt"
        return ["java", "-Xmx3G", "@libraries/net/minecraftforge/forge/%s/%s" % (full, args), "nogui"]
    fail("unknown loader " + loader)


def names_continent(world):
    """Whether the saved generator settings name the_continent:continent (data/minecraft/... from 26.1)."""
    for root, _, files in os.walk(world):
        for name in files:
            if not name.endswith(".dat"):
                continue
            raw = open(os.path.join(root, name), "rb").read()
            for decode in (gzip.decompress, zlib.decompress, lambda b: b):
                try:
                    if b"the_continent:continent" in decode(raw):
                        return True
                except (OSError, zlib.error, EOFError):
                    continue
    return False


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--loader", required=True, choices=["fabric", "quilt", "neoforge", "forge"])
    p.add_argument("--minecraft", required=True)
    p.add_argument("--loader-version", required=True)
    p.add_argument("--mod", action="append", required=True)
    p.add_argument("--work", required=True)
    p.add_argument("--timeout", type=int, default=900)
    p.add_argument("--map-out")
    a = p.parse_args()
    if a.map_out and os.path.exists(a.map_out):
        fail("--map-out %s exists; give a new path, this never overwrites" % a.map_out)

    work = os.path.abspath(a.work)
    if os.path.exists(work) and os.listdir(work):
        fail("work dir %s is not empty; give a new one" % work)
    os.makedirs(os.path.join(work, "mods"), exist_ok=True)
    for mod in a.mod:
        if not os.path.isfile(mod):
            fail("no such mod jar " + mod)
        with open(mod, "rb") as src, open(os.path.join(work, "mods", os.path.basename(mod)), "wb") as dst:
            dst.write(src.read())

    command = install(a.loader, a.minecraft, a.loader_version, work)
    with open(os.path.join(work, "eula.txt"), "w") as f:
        f.write("eula=true\n")  # The automated test server only, as the gametests do.
    with open(os.path.join(work, "server.properties"), "w") as f:
        f.write("level-type=the_continent\\:continent\nlevel-seed=12345\nserver-ip=127.0.0.1\nserver-port=25599\n"
                "online-mode=false\nsync-chunk-writes=false\nspawn-protection=0\n")

    print("starting", " ".join(command))
    server = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    lines = []
    last_output = [time.time()]
    done = threading.Event()
    located = threading.Event()
    mapped = threading.Event()

    def pump():
        for line in server.stdout:
            last_output[0] = time.time()
            lines.append(line.rstrip("\n"))
            if "Done (" in line and "For help" in line:
                done.set()
            if LOCATE_REPLY.search(line) or "Could not find a biome of type" in line:
                located.set()
            if MAP_REPLY.search(line) or "Map export failed" in line:
                mapped.set()

    threading.Thread(target=pump, daemon=True).start()

    def send(command_line):
        """False once the server is gone, as it is after a crash at startup."""
        try:
            server.stdin.write(command_line + "\n")
            server.stdin.flush()
            return True
        except OSError:
            return False

    def wait_for(event, seconds):
        """Waits for event; gives up early once the server exits, or logs nothing for SILENCE seconds (a server
        that failed to load can sit there without exiting)."""
        deadline = time.time() + seconds
        while time.time() < deadline:
            if event.wait(1):
                return True
            if server.poll() is not None:
                return event.is_set()
            if time.time() - last_output[0] > SILENCE:
                print("no output for %d s; giving up" % SILENCE)
                return False
        return False

    started = time.time()
    problems = []
    if not wait_for(done, a.timeout):
        problems.append("the server did not reach Done (%s, after %d s)"
                        % ("it exited" if server.poll() is not None else "stalled or timed out", time.time() - started))
    else:
        print("reached Done after %d s" % (time.time() - started))
        if not send("locate biome minecraft:mushroom_fields") or not wait_for(located, 300):
            problems.append("/locate biome gave no answer")
        if a.map_out and (not send("continent map") or not wait_for(mapped, 300)):
            problems.append("/continent map gave no answer")
    send("stop")
    try:
        code = server.wait(180)
    except subprocess.TimeoutExpired:
        server.kill()
        code = None
        problems.append("the server did not stop within 180 s of /stop")
    with open(os.path.join(work, "smoke.log"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    if not any("[the_continent] initialized" in line for line in lines):
        problems.append('the mod never logged "[the_continent] initialized"')
    reply = next((LOCATE_REPLY.search(line) for line in lines if LOCATE_REPLY.search(line)), None)
    if reply is None:
        problems.append("no Mushroom Fields found, so the biome source isn't the continent's")
    else:
        x, z = int(reply.group(1)), int(reply.group(2))
        off = max(abs(x - MUSHROOM_FIELDS[0]), abs(z - MUSHROOM_FIELDS[1]))
        print("Mushroom Fields at (%d, %d), %d blocks from the layout's (%d, %d)" % (x, z, off, *MUSHROOM_FIELDS))
        if off > MUSHROOM_TOLERANCE:
            problems.append("Mushroom Fields at (%d, %d), not near the layout's (%d, %d)" % (x, z, *MUSHROOM_FIELDS))
    if not names_continent(os.path.join(work, "world")):
        problems.append("the saved world's generator settings don't name the_continent:continent")
    errors = [line for line in lines if ERROR_LINE.search(line)]
    if errors:
        problems.append("%d ERROR/FATAL log lines, first: %s" % (len(errors), errors[0]))
    if code != 0:
        problems.append("the server exited with %s" % code)
    if a.map_out:
        written = next((MAP_REPLY.search(line).group(1) for line in lines if MAP_REPLY.search(line)), None)
        if written is None:
            problems.append("/continent map wrote no map")
        else:
            shutil.copyfile(os.path.join(work, "world", written), a.map_out)
            print("map copied to", a.map_out)

    if problems:
        print("\n".join(lines[-60:]))
        for problem in problems:
            print("SMOKE FAIL:", problem)
        sys.exit(1)
    print("SMOKE PASS: %s %s on Minecraft %s" % (a.loader, a.loader_version, a.minecraft))


main()
