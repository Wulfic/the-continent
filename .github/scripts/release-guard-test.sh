#!/usr/bin/env bash
# Tests release-guard.sh. Every bad input must be refused, for the expected reason, without writing the
# notes file. One good input must pass. Builds a throwaway git repo and fake jars in a fresh temp dir.
#
#   release-guard-test.sh        exit 0 = all cases behaved, 1 = at least one didn't
set -euo pipefail

guard=$(cd "$(dirname "$0")" && pwd)/release-guard.sh
[ -f "$guard" ] || { echo "release-guard-test: no guard at $guard" >&2; exit 1; }
for tool in git zip unzip jq awk; do
	command -v "$tool" >/dev/null || { echo "release-guard-test: required tool '$tool' is not installed" >&2; exit 1; }
done

# This suite commits, retags and deletes refs. An exported GIT_DIR (say, from a git hook) would point every
# one of those at the caller's real repository, so drop every variable that redirects git, and ignore the
# caller's git config (signing, hooks, default branch).
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES \
	GIT_COMMON_DIR GIT_NAMESPACE GIT_CEILING_DIRECTORIES
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

tmp=$(mktemp -d)
# Only ever remove the directory mktemp just made for this run.
if [ -z "$tmp" ] || [ ! -d "$tmp" ]; then
	echo "release-guard-test: mktemp -d failed" >&2
	exit 1
fi
trap 'rm -rf -- "$tmp"' EXIT

failures=0
pass() { echo "ok   - $1"; }
fail() { echo "FAIL - $1" >&2; failures=$((failures + 1)); }

# make_jar <path> [<entry> <contents>]...: a jar holding those entries (and a placeholder, so it's never empty).
make_jar() {
	local path=$1 stage
	shift
	stage=$(mktemp -d "$tmp/jar.XXXXXX")
	echo "placeholder" > "$stage/README"
	while [ "$#" -ge 2 ]; do
		mkdir -p "$(dirname "$stage/$1")"
		printf '%s' "$2" > "$stage/$1"
		shift 2
	done
	(cd "$stage" && zip -q -r "$path" .)
}

# props <version>: gradle.properties with the suite's band table, two bands spelled as the real file spells them.
# The 26.1.2 band's Fabric jar is labelled 26.1 (it runs on 26.1 - 26.1.2); NeoForge and Forge label theirs 26.1.2.
props() {
	printf 'org.gradle.parallel=true\nversion=%s\ngroup=com.example\n' "$1"
	printf 'band_label.26.1.2=26.1\nminecraft_range.26.1.2=>=26.1 <26.2\n'
	printf 'neoforge_band_label.26.1.2=26.1.2\nneoforge_minecraft_range.26.1.2=[26.1.2,26.2)\n'
	printf 'forge_band_label.26.1.2=26.1.2\nforge_minecraft_range.26.1.2=[26.1.2,26.2)\n'
	printf 'band_label.26.2=26.2\nminecraft_range.26.2=>=26.2 <26.3\n'
	printf 'neoforge_band_label.26.2=26.2\nneoforge_minecraft_range.26.2=[26.2,26.3)\n'
	printf 'forge_band_label.26.2=26.2\nforge_minecraft_range.26.2=[26.2,26.3)\n'
}

# fabric_json <id> <version> <minecraft range>
fabric_json() {
	printf '{"id": "%s", "version": "%s", "depends": {"minecraft": "%s"}}' "$1" "$2" "$3"
}

# mods_toml <id> <version> <minecraft range> <loader>: laid out as ModDevGradle and ForgeGradle generate it.
mods_toml() {
	printf 'modLoader = "javafml"\nloaderVersion = "[1,)"\n\n[[mods]]\nmodId = "%s"\nversion = "%s"\n' "$1" "$2"
	printf "description = '''\nA world type.\n'''\n\n"
	printf '[[dependencies.%s]]\nmodId = "%s"\nversionRange = "[1,)"\n\n' "$1" "$4"
	printf '[[dependencies.%s]]\nmodId = "minecraft"\nversionRange = "%s"\nside = "BOTH"\n' "$1" "$3"
}

# make_dist <dir> <version>: every band's jar and sources jar, with metadata matching props.
make_dist() {
	local dir=$1 v=$2 jar
	make_jar "$dir/the_continent-$v+fabric-mc26.1.jar" fabric.mod.json "$(fabric_json the_continent "$v" '>=26.1 <26.2')"
	make_jar "$dir/the_continent-$v+fabric-mc26.2.jar" fabric.mod.json "$(fabric_json the_continent "$v" '>=26.2 <26.3')"
	make_jar "$dir/the_continent-$v+neoforge-mc26.1.2.jar" META-INF/neoforge.mods.toml \
		"$(mods_toml the_continent "$v" '[26.1.2,26.2)' neoforge)"
	make_jar "$dir/the_continent-$v+neoforge-mc26.2.jar" META-INF/neoforge.mods.toml \
		"$(mods_toml the_continent "$v" '[26.2,26.3)' neoforge)"
	make_jar "$dir/the_continent-$v+forge-mc26.1.2.jar" META-INF/mods.toml "$(mods_toml the_continent "$v" '[26.1.2,26.2)' forge)"
	make_jar "$dir/the_continent-$v+forge-mc26.2.jar" META-INF/mods.toml "$(mods_toml the_continent "$v" '[26.2,26.3)' forge)"
	for jar in "$dir"/*.jar; do
		make_jar "${jar%.jar}-sources.jar"
	done
}

# new_repo <dir>: a repo whose main is on "origin/main", tagged v0.1.0, with matching jars in dist/.
new_repo() {
	local dir=$1
	mkdir -p "$dir/dist"
	git -C "$dir" init -q -b main
	# Fail closed: every git call below must land in this throwaway repo inside $tmp, nowhere else.
	if [ "$(git -C "$dir" rev-parse --absolute-git-dir)" != "$(cd "$dir" && pwd -P)/.git" ]; then
		echo "release-guard-test: git resolved $dir to another repository; stopping before touching it" >&2
		exit 1
	fi
	git -C "$dir" config user.email test@example.invalid
	git -C "$dir" config user.name test
	props 0.1.0 > "$dir/gradle.properties"
	printf '# Changelog\n\n## 0.2.0\n\n- later\n\n## 0.1.0 - 2026-10-17\n\n- First release.\n- Second line.\n\n## 0.0.1\n\n- older\n' > "$dir/CHANGELOG.md"
	git -C "$dir" add -A
	git -C "$dir" commit -q -m init
	git -C "$dir" update-ref refs/remotes/origin/main HEAD
	git -C "$dir" tag -a v0.1.0 -m v0.1.0
	make_dist "$dir/dist" 0.1.0
}

# expect_refused <name> <dir> <stderr substring> <guard args...>
expect_refused() {
	local name=$1 dir=$2 reason=$3 out code=0
	shift 3
	out=$(cd "$dir" && bash "$guard" "$@" 2>&1) || code=$?
	if [ "$code" -eq 0 ]; then
		fail "$name: guard passed, expected refusal"
	elif [[ $out != *"REFUSED: "*"$reason"* ]]; then
		fail "$name: refused for the wrong reason. Wanted '$reason', got: $out"
	elif [ -e "$dir/notes.md" ]; then
		fail "$name: refused but still wrote notes.md"
	else
		pass "$name"
	fi
}

# case_dir <name>: sets d to a fresh case repo. Not run in $(...), so errexit stops the suite on a failed setup.
case_dir() {
	d=$tmp/$1
	new_repo "$d"
}

# expect_passed <name> <dir> <tag> <expected notes>: the guard passes and writes exactly these notes.
expect_passed() {
	local name=$1 dir=$2 tag=$3 expected=$4 out
	if out=$(cd "$dir" && bash "$guard" "$tag" dist notes.md 2>&1); then
		if [ "$(cat "$dir/notes.md")" = "$expected" ]; then pass "$name"
		else fail "$name: passed, but notes.md is: $(cat "$dir/notes.md")"; fi
	else
		fail "$name: refused: $out"
	fi
}

# --- Good input -------------------------------------------------------------------------------------
case_dir good
expect_passed "good tag passes, notes are exactly the 0.1.0 section" "$d" v0.1.0 $'\n- First release.\n- Second line.'

case_dir prerelease
props 0.2.0-beta.1 > "$d/gradle.properties"
printf '## 0.2.0-beta.1\n\n- Beta.\n' > "$d/CHANGELOG.md"
git -C "$d" commit -q -am beta
git -C "$d" update-ref refs/remotes/origin/main HEAD
git -C "$d" tag -a v0.2.0-beta.1 -m beta
rm -- "$d"/dist/*.jar
make_dist "$d/dist" 0.2.0-beta.1
expect_passed "prerelease tag passes" "$d" v0.2.0-beta.1 $'\n- Beta.'

case_dir crlf
props 0.1.0 | sed 's/$/\r/' > "$d/gradle.properties"
git -C "$d" commit -q -am crlf && git -C "$d" update-ref refs/remotes/origin/main HEAD && git -C "$d" tag -f -a v0.1.0 -m v0.1.0 >/dev/null
if out=$(cd "$d" && bash "$guard" v0.1.0 dist notes.md 2>&1); then pass "CRLF gradle.properties still matches"
else fail "CRLF gradle.properties refused: $out"; fi

# --- Arguments and tag format -----------------------------------------------------------------------
case_dir args
expect_refused "no arguments" "$d" "expected 3 arguments"
expect_refused "empty tag" "$d" "empty tag" "" dist notes.md
expect_refused "empty dist dir" "$d" "empty dist dir" v0.1.0 "" notes.md
expect_refused "tag without v" "$d" "is not vMAJOR" 0.1.0 dist notes.md
git -C "$d" tag -a release-v0.1.0 -m release-v0.1.0
expect_refused "tag with a prefix" "$d" "is not vMAJOR" release-v0.1.0 dist notes.md
expect_refused "two-part tag" "$d" "is not vMAJOR" v0.1 dist notes.md
expect_refused "tag with shell junk" "$d" "is not vMAJOR" 'v0.1.0;rm' dist notes.md
expect_refused "tag with trailing newline" "$d" "is not vMAJOR" $'v0.1.0\n' dist notes.md

# --- Version ----------------------------------------------------------------------------------------
case_dir mismatch
git -C "$d" tag -a v0.2.0 -m v0.2.0
expect_refused "tag newer than gradle.properties" "$d" "does not match gradle.properties version '0.1.0'" v0.2.0 dist notes.md

case_dir prerelease-mismatch
expect_refused "prerelease tag for a release version" "$d" "does not match" v0.1.0-beta.1 dist notes.md

case_dir no-version
props 0.1.0 | grep -v '^version=' > "$d/gradle.properties"
expect_refused "no version line" "$d" "has no version= line" v0.1.0 dist notes.md

case_dir two-versions
{ props 0.1.0; echo version=0.2.0; } > "$d/gradle.properties"
expect_refused "two version lines" "$d" "more than one version= line" v0.1.0 dist notes.md

# --- Commit -----------------------------------------------------------------------------------------
case_dir missing-tag
git -C "$d" tag -d v0.1.0 >/dev/null
expect_refused "tag not in the checkout" "$d" "does not exist in this checkout" v0.1.0 dist notes.md

case_dir tag-not-head
git -C "$d" commit -q --allow-empty -m later
git -C "$d" update-ref refs/remotes/origin/main HEAD
expect_refused "tag is not the checked-out commit" "$d" "but the checkout is" v0.1.0 dist notes.md

case_dir off-main
git -C "$d" checkout -q -b side
git -C "$d" commit -q --allow-empty -m side
git -C "$d" tag -f -a v0.1.0 -m v0.1.0 >/dev/null
expect_refused "commit not on origin/main" "$d" "is not on origin/main" v0.1.0 dist notes.md

case_dir no-origin-main
git -C "$d" update-ref -d refs/remotes/origin/main
expect_refused "origin/main not fetched" "$d" "origin/main is not fetched" v0.1.0 dist notes.md

# --- Build outputs ----------------------------------------------------------------------------------
fab=dist/the_continent-0.1.0+fabric-mc26.2.jar
neo=dist/the_continent-0.1.0+neoforge-mc26.2.jar
frg=dist/the_continent-0.1.0+forge-mc26.1.2.jar

case_dir no-dist
expect_refused "dist dir missing" "$d" "dist dir 'nowhere' does not exist" v0.1.0 nowhere notes.md

for jar in "$fab" "$neo" "$frg" dist/the_continent-0.1.0+fabric-mc26.1-sources.jar; do
	case_dir "missing-${jar##*/}"
	rm -- "${d:?}/$jar"
	expect_refused "missing ${jar##*/}" "$d" "missing build output '$jar'" v0.1.0 dist notes.md
done

case_dir empty-jar
: > "$d/$neo"
expect_refused "empty jar" "$d" "is empty" v0.1.0 dist notes.md

case_dir not-a-zip
echo "not a zip" > "$d/$fab"
expect_refused "jar that isn't a zip" "$d" "is not a jar with a fabric.mod.json" v0.1.0 dist notes.md

case_dir no-mod-json
rm -- "$d/$fab"
make_jar "$d/$fab"
expect_refused "Fabric jar without fabric.mod.json" "$d" "is not a jar with a fabric.mod.json" v0.1.0 dist notes.md

case_dir no-neoforge-toml
rm -- "$d/$neo"
make_jar "$d/$neo" META-INF/mods.toml "$(mods_toml the_continent 0.1.0 '[26.2,26.3)' neoforge)"
expect_refused "NeoForge jar with only Forge's mods.toml" "$d" "is not a jar with a META-INF/neoforge.mods.toml" \
	v0.1.0 dist notes.md

case_dir bad-json
rm -- "$d/$fab"
make_jar "$d/$fab" fabric.mod.json '{"id": '
expect_refused "jar with broken fabric.mod.json" "$d" "unreadable fabric.mod.json" v0.1.0 dist notes.md

case_dir jar-wrong-version
rm -- "$d/$fab"
make_jar "$d/$fab" fabric.mod.json "$(fabric_json the_continent 0.0.9 '>=26.2 <26.3')"
expect_refused "Fabric jar built at another version" "$d" "has version '0.0.9'" v0.1.0 dist notes.md

case_dir toml-wrong-version
rm -- "$d/$frg"
make_jar "$d/$frg" META-INF/mods.toml "$(mods_toml the_continent 0.0.9 '[26.1.2,26.2)' forge)"
expect_refused "Forge jar built at another version" "$d" "has version '0.0.9'" v0.1.0 dist notes.md

case_dir jar-wrong-id
rm -- "$d/$fab"
make_jar "$d/$fab" fabric.mod.json "$(fabric_json other_mod 0.1.0 '>=26.2 <26.3')"
expect_refused "jar of another mod" "$d" "has mod id 'other_mod'" v0.1.0 dist notes.md

case_dir fabric-wrong-range
rm -- "$d/$fab"
make_jar "$d/$fab" fabric.mod.json "$(fabric_json the_continent 0.1.0 '>=26.1 <26.3')"
expect_refused "Fabric jar declaring a wider range" "$d" "declares Minecraft '>=26.1 <26.3', but its band says '>=26.2 <26.3'" \
	v0.1.0 dist notes.md

case_dir fabric-no-range
rm -- "$d/$fab"
make_jar "$d/$fab" fabric.mod.json '{"id": "the_continent", "version": "0.1.0"}'
expect_refused "Fabric jar declaring no Minecraft range" "$d" "declares Minecraft '', but its band says" v0.1.0 dist notes.md

case_dir neoforge-wrong-range
rm -- "$d/$neo"
make_jar "$d/$neo" META-INF/neoforge.mods.toml "$(mods_toml the_continent 0.1.0 '[26.1.2,26.2)' neoforge)"
expect_refused "NeoForge jar declaring another band's range" "$d" \
	"declares Minecraft '[26.1.2,26.2)', but its band says '[26.2,26.3)'" v0.1.0 dist notes.md

case_dir toml-no-minecraft
rm -- "$d/$frg"
make_jar "$d/$frg" META-INF/mods.toml \
	"$(mods_toml the_continent 0.1.0 '[26.1.2,26.2)' forge | sed 's/modId = "minecraft"/modId = "other"/')"
expect_refused "Forge jar with no minecraft dependency" "$d" "has no minecraft in its mods.toml" v0.1.0 dist notes.md

case_dir toml-two-mods
rm -- "$d/$frg"
make_jar "$d/$frg" META-INF/mods.toml \
	"$(mods_toml the_continent 0.1.0 '[26.1.2,26.2)' forge; printf '\n[[mods]]\nmodId = "other"\nversion = "1"\n')"
expect_refused "Forge jar holding two mods" "$d" "has more than one id in its mods.toml" v0.1.0 dist notes.md

case_dir stale-jar
make_jar "$d/dist/the_continent-0.1.0.jar" fabric.mod.json "$(fabric_json the_continent 0.1.0 '>=26.1 <26.2')"
expect_refused "a stale jar beside the band jars" "$d" "'dist/the_continent-0.1.0.jar' is not a build output of any band" \
	v0.1.0 dist notes.md

case_dir stray-dotfile
echo x > "$d/dist/.hidden"
expect_refused "a hidden file in dist" "$d" "'dist/.hidden' is not a build output" v0.1.0 dist notes.md

case_dir stray-dir
mkdir "$d/dist/the_continent-0.1.0+fabric-mc26.3.jar"
expect_refused "a directory named like a jar" "$d" "is not a build output of any band" v0.1.0 dist notes.md

# --- Band table -------------------------------------------------------------------------------------
case_dir no-bands
props 0.1.0 | grep -v 'band_label\|minecraft_range' > "$d/gradle.properties"
expect_refused "gradle.properties without bands" "$d" "lists no band_label.<band> keys" v0.1.0 dist notes.md

case_dir duplicate-band
{
	props 0.1.0
	printf 'band_label.26.2.1=26.2\nminecraft_range.26.2.1=>=26.2 <26.3\n'
	printf 'neoforge_band_label.26.2.1=26.2.1\nneoforge_minecraft_range.26.2.1=[26.2.1,26.3)\n'
	printf 'forge_band_label.26.2.1=26.2.1\nforge_minecraft_range.26.2.1=[26.2.1,26.3)\n'
} > "$d/gradle.properties"
expect_refused "two bands with one Fabric label" "$d" \
	"bands 26.2 and 26.2.1 both make the_continent-0.1.0+fabric-mc26.2.jar" v0.1.0 dist notes.md

case_dir band-missing-key
props 0.1.0 | grep -v '^forge_minecraft_range.26.2=' > "$d/gradle.properties"
expect_refused "a band without its Forge range" "$d" "has no forge_minecraft_range.26.2= line" v0.1.0 dist notes.md

case_dir band-key-dots-literal
props 0.1.0 | sed 's/^neoforge_band_label.26.2=/neoforge_band_label.26x2=/' > "$d/gradle.properties"
expect_refused "dots in a band key are not wildcards" "$d" "has no neoforge_band_label.26.2= line" v0.1.0 dist notes.md

case_dir label-with-a-path
props 0.1.0 | sed 's#^forge_band_label.26.2=.*#forge_band_label.26.2=../26.2#' > "$d/gradle.properties"
expect_refused "a band label that is a path" "$d" "forge_band_label.26.2 '../26.2' is not a Minecraft version" \
	v0.1.0 dist notes.md

case_dir band-with-a-glob
{ props 0.1.0; echo 'band_label.26.*=26.9'; } > "$d/gradle.properties"
expect_refused "a band key with a glob" "$d" "band '26.*' in gradle.properties is not a Minecraft version" v0.1.0 dist notes.md

case_dir band-key-twice
{ props 0.1.0; echo 'minecraft_range.26.2=>=26.2 <26.4'; } > "$d/gradle.properties"
expect_refused "a band key given twice" "$d" "more than one minecraft_range.26.2= line" v0.1.0 dist notes.md

case_dir one-refusal-line
props 0.1.0 | grep -v '^forge_minecraft_range.26.2=' > "$d/gradle.properties"
out=$(cd "$d" && bash "$guard" v0.1.0 dist notes.md 2>&1) && code=0 || code=$?
if [ "$code" -ne 0 ] && [ "$(grep -c 'REFUSED' <<< "$out")" -eq 1 ]; then
	pass "a refusal inside a lookup is reported once, not again as an unexpected failure"
else
	fail "lookup refusal: exit $code, output: $out"
fi

# --- Changelog and notes ----------------------------------------------------------------------------
case_dir no-changelog
rm -- "$d/CHANGELOG.md"
expect_refused "no CHANGELOG.md" "$d" "no CHANGELOG.md" v0.1.0 dist notes.md

case_dir no-section
printf '# Changelog\n\n## 0.0.1\n\n- older\n' > "$d/CHANGELOG.md"
expect_refused "no section for the version" "$d" "no '## 0.1.0' section" v0.1.0 dist notes.md

case_dir dots-are-literal
printf '# Changelog\n\n## 0x1y0\n\n- not a match\n' > "$d/CHANGELOG.md"
expect_refused "dots in the version are not wildcards" "$d" "no '## 0.1.0' section" v0.1.0 dist notes.md

case_dir empty-section
printf '# Changelog\n\n## 0.1.0\n\n   \n## 0.0.1\n\n- older\n' > "$d/CHANGELOG.md"
expect_refused "empty section" "$d" "section has no entries" v0.1.0 dist notes.md

case_dir headings-only
printf '## 0.1.0\n\n### Added\n\n### Fixed\n\n## 0.0.1\n\n- older\n' > "$d/CHANGELOG.md"
expect_refused "section with only subheadings" "$d" "section has no entries" v0.1.0 dist notes.md

case_dir bracketed
printf '## [0.1.0] - 2026-10-17\n\n- Bracketed.\n\n## [0.0.1]\n\n- older\n' > "$d/CHANGELOG.md"
expect_passed "## [0.1.0] heading passes" "$d" v0.1.0 $'\n- Bracketed.'

case_dir subsections
printf '## 0.1.0\n\n### Added\n\n- New.\n\n### Fixed\n\n- Bug.\n\n## 0.0.1\n\n- older\n' > "$d/CHANGELOG.md"
expect_passed "### subsections stay in the notes" "$d" v0.1.0 $'\n### Added\n\n- New.\n\n### Fixed\n\n- Bug.'

case_dir crlf-changelog
printf '## 0.1.0\r\n\r\n- Windows.\r\n\r\n## 0.0.1\r\n\r\n- older\r\n' > "$d/CHANGELOG.md"
expect_passed "CRLF changelog passes, notes have no CR" "$d" v0.1.0 $'\n- Windows.'

case_dir tab-heading
printf '## 0.1.0\n\n- Mine.\n\n##\t0.0.1\n\n- older\n' > "$d/CHANGELOG.md"
expect_passed "a tab-separated ## heading ends the section" "$d" v0.1.0 $'\n- Mine.'

case_dir huge-changelog
{
	printf '## 0.1.0\n\n- Latest.\n\n'
	for i in $(seq 1 20000); do printf '## 0.0.%s\n\n- an older release with a long enough line to add up\n\n' "$i"; done
} > "$d/CHANGELOG.md"
expect_passed "a changelog over 1 MB passes (no SIGPIPE)" "$d" v0.1.0 $'\n- Latest.'

case_dir notes-exist
echo "keep me" > "$d/notes.md"
out=$(cd "$d" && bash "$guard" v0.1.0 dist notes.md 2>&1) && code=0 || code=$?
if [ "$code" -ne 0 ] && [[ $out == *"already exists"* ]] && [ "$(cat "$d/notes.md")" = "keep me" ]; then
	pass "existing notes file is refused and left unchanged"
else
	fail "existing notes file: exit $code, contents '$(cat "$d/notes.md")', output: $out"
fi

case_dir dangling-notes
ln -s "$d/elsewhere.md" "$d/notes.md"
out=$(cd "$d" && bash "$guard" v0.1.0 dist notes.md 2>&1) && code=0 || code=$?
if [ "$code" -ne 0 ] && [[ $out == *"already exists"* ]] && [ ! -e "$d/elsewhere.md" ]; then
	pass "a dangling symlink at the notes path is refused, and nothing is written through it"
else
	fail "dangling notes symlink: exit $code, output: $out"
fi

case_dir no-jq
mkdir "$d/bin"
for tool in git unzip awk sed tr wc grep ls; do ln -s "$(command -v "$tool")" "$d/bin/$tool"; done
out=$(cd "$d" && PATH="$d/bin" "$BASH" "$guard" v0.1.0 dist notes.md 2>&1) && code=0 || code=$?
if [ "$code" -ne 0 ] && [[ $out == *"REFUSED: required tool 'jq' is not installed"* ]]; then
	pass "a missing tool is named, not blamed on the jar"
else
	fail "missing jq: exit $code, output: $out"
fi

if [ "$failures" -ne 0 ]; then
	echo "release-guard-test: $failures case(s) failed" >&2
	exit 1
fi
echo "release-guard-test: all cases passed"
