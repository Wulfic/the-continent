#!/usr/bin/env bash
# Takes thread dumps of a running Minecraft client, so a client gametest that hangs in CI shows what each
# thread was doing. Run it in the background next to the test; it only reads, and it never stops the client.
#
#   thread-dumps.sh <out-dir> [first-delay-seconds] [count] [interval-seconds]
#
# After the first delay it dumps every client JVM it finds (main class KnotClient or devlaunchinjector) to
# <out-dir>/threads-<pid>-<n>.txt, <count> times, <interval> seconds apart. A client that already exited is fine.
set -euo pipefail

out=${1:?usage: thread-dumps.sh <out-dir> [first-delay-seconds] [count] [interval-seconds]}
delay=${2:-90}
count=${3:-4}
interval=${4:-30}
jcmd="${JAVA_HOME:+$JAVA_HOME/bin/}jcmd"

sleep "$delay"
for ((n = 1; n <= count; n++)); do
	pids=$("$jcmd" -l | awk '/KnotClient|devlaunchinjector/ { print $1 }')
	if [[ -z $pids ]]; then
		echo "thread-dumps: no client JVM running, done after $((n - 1)) round(s)"
		exit 0
	fi
	mkdir -p "$out"
	for pid in $pids; do
		if "$jcmd" "$pid" Thread.print > "$out/threads-$pid-$n.txt" 2>&1; then
			echo "thread-dumps: wrote $out/threads-$pid-$n.txt"
		else
			echo "thread-dumps: could not dump $pid (it may have just exited); see $out/threads-$pid-$n.txt" >&2
		fi
	done
	sleep "$interval"
done
