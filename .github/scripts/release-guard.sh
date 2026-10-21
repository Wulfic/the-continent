#!/usr/bin/env bash
# Refuses to publish a release unless every check below passes. Run from the repository root.
#
#   release-guard.sh <tag> <dist-dir> <notes-out>
#
# Checks: the tag is vMAJOR.MINOR.PATCH[-pre] and equals "v" + gradle.properties' version · the tag is
# the checked-out commit, and that commit is on origin/main · <dist-dir> holds exactly one jar and one sources
# jar per band and loader, named the_continent-<version>+<loader>-mc<label>[-sources].jar, and nothing else ·
# each jar's own metadata (fabric.mod.json, META-INF/neoforge.mods.toml, META-INF/mods.toml) carries the mod
# id, the version and the band's Minecraft range · CHANGELOG.md has a non-empty "## <version>" section. That
# section is written to <notes-out>, which must not exist yet.
# The band table is gradle.properties: every band_label.<band> key is a band, and each band needs its
# minecraft_range, neoforge_band_label, neoforge_minecraft_range, forge_band_label and forge_minecraft_range.
# Exit 0 means safe to publish. Anything else prints "REFUSED: <reason>" and exits 1.
set -euo pipefail

MOD_ID=the_continent
LOADERS=(fabric neoforge forge)

refuse() {
	echo "release-guard: REFUSED: $*" >&2
	echo "release-guard: nothing was published. Fix the cause and push a new tag, or re-run this job." >&2
	exit 1
}

ok() {
	echo "release-guard: ok: $*"
}

# Any command failing where no check expected it still ends in a REFUSED line, never a bare exit.
trap 'refuse "unexpected failure (exit $?) at line $LINENO"' ERR

# A missing tool must not be blamed on the jar or the changelog.
for tool in git unzip jq awk sed; do
	command -v "$tool" >/dev/null || refuse "required tool '$tool' is not installed"
done

[ "$#" -eq 3 ] || refuse "expected 3 arguments (tag, dist dir, notes file), got $#"
tag=$1
dist=$2
notes_out=$3
[ -n "$tag" ] || refuse "empty tag"
[ -n "$dist" ] || refuse "empty dist dir"
[ -n "$notes_out" ] || refuse "empty notes file path"

[[ $tag =~ ^v([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?)$ ]] \
	|| refuse "tag '$tag' is not vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-prerelease"
tag_version=${BASH_REMATCH[1]}
ok "tag format: $tag"

[ -f gradle.properties ] || refuse "no gradle.properties in $(pwd)"
# A checkout on Windows can carry CRLF line endings, so strip the CR before comparing.
versions=$(sed -n 's/^version=//p' gradle.properties | tr -d '\r')
[ -n "$versions" ] || refuse "gradle.properties has no version= line"
[ "$(printf '%s\n' "$versions" | wc -l)" -eq 1 ] || refuse "gradle.properties has more than one version= line"
version=$versions
[ "$tag_version" = "$version" ] \
	|| refuse "tag '$tag' does not match gradle.properties version '$version' (expected tag 'v$version')"
ok "tag matches gradle.properties version $version"

tag_commit=$(git rev-parse --verify --quiet "refs/tags/$tag^{commit}") || refuse "tag '$tag' does not exist in this checkout"
head_commit=$(git rev-parse --verify HEAD)
[ "$tag_commit" = "$head_commit" ] \
	|| refuse "tag '$tag' is commit $tag_commit, but the checkout is $head_commit"
git rev-parse --verify --quiet refs/remotes/origin/main >/dev/null \
	|| refuse "origin/main is not fetched, so it can't be checked that $tag is on main"
git merge-base --is-ancestor "$head_commit" refs/remotes/origin/main \
	|| refuse "commit $head_commit is not on origin/main. Releases are cut from main only"
ok "commit $head_commit is the tagged commit and is on origin/main"

# prop <key>: the one value of an exact key in gradle.properties (dots in the key are not wildcards).
prop() {
	local values
	values=$(awk -v k="$1" '{ sub(/\r$/, "") } index($0, k "=") == 1 { print substr($0, length(k) + 2) }' gradle.properties)
	[ -n "$values" ] || refuse "gradle.properties has no $1= line"
	[ "$(printf '%s\n' "$values" | wc -l)" -eq 1 ] || refuse "gradle.properties has more than one $1= line"
	printf '%s\n' "$values"
}

# toml_fields <file contents>: "id=", "version=" from [[mods]] and "minecraft=" from the minecraft dependency's
# versionRange, one line each, from a generated mods.toml (one key = "value" per line, modId before versionRange).
toml_fields() {
	awk '
		{ sub(/\r$/, "") }
		/^\[\[/ { section = $0; dep = ""; next }
		/^[A-Za-z]+[ \t]*=[ \t]*"[^"]*"[ \t]*$/ {
			key = $0; sub(/[ \t]*=.*/, "", key)
			value = $0; sub(/^[^"]*"/, "", value); sub(/"[ \t]*$/, "", value)
			if (section == "[[mods]]" && key == "modId") print "id=" value
			if (section == "[[mods]]" && key == "version") print "version=" value
			if (section ~ /^\[\[dependencies\./ && key == "modId") dep = value
			if (section ~ /^\[\[dependencies\./ && key == "versionRange" && dep == "minecraft") print "minecraft=" value
		}' <<< "$1"
}

# field <name> <fields> <jar>: the one value of name= in toml_fields output.
field() {
	local values
	values=$(sed -n "s/^$1=//p" <<< "$2")
	[ -n "$values" ] || refuse "'$3' has no $1 in its mods.toml"
	[ "$(printf '%s\n' "$values" | wc -l)" -eq 1 ] || refuse "'$3' has more than one $1 in its mods.toml"
	printf '%s\n' "$values"
}

# check_jar <loader> <jar> <minecraft range>: the jar's own metadata names this mod, version and range.
check_jar() {
	local loader=$1 jar=$2 range=$3 meta id jar_version jar_range fields entry
	if [ "$loader" = fabric ]; then
		meta=$(unzip -p "$jar" fabric.mod.json 2>/dev/null) || refuse "'$jar' is not a jar with a fabric.mod.json"
		id=$(jq -r '.id // empty' <<< "$meta" 2>/dev/null) || refuse "'$jar' has an unreadable fabric.mod.json"
		jar_version=$(jq -r '.version // empty' <<< "$meta" 2>/dev/null) || refuse "'$jar' has an unreadable fabric.mod.json"
		jar_range=$(jq -r '.depends.minecraft // empty' <<< "$meta" 2>/dev/null) || refuse "'$jar' has an unreadable fabric.mod.json"
	else
		entry=META-INF/mods.toml
		[ "$loader" = neoforge ] && entry=META-INF/neoforge.mods.toml
		meta=$(unzip -p "$jar" "$entry" 2>/dev/null) || refuse "'$jar' is not a jar with a $entry"
		[ -n "$meta" ] || refuse "'$jar' is not a jar with a $entry"
		fields=$(toml_fields "$meta")
		id=$(field id "$fields" "$jar") || exit 1
		jar_version=$(field version "$fields" "$jar") || exit 1
		jar_range=$(field minecraft "$fields" "$jar") || exit 1
	fi
	[ "$id" = "$MOD_ID" ] || refuse "'$jar' has mod id '$id', expected '$MOD_ID'"
	[ "$jar_version" = "$version" ] || refuse "'$jar' has version '$jar_version', expected '$version'"
	[ "$jar_range" = "$range" ] || refuse "'$jar' declares Minecraft '$jar_range', but its band says '$range'"
}

[ -d "$dist" ] || refuse "dist dir '$dist' does not exist"
bands=$(sed -n 's/^band_label\.\([^=]*\)=.*/\1/p' gradle.properties | tr -d '\r')
[ -n "$bands" ] || refuse "gradle.properties lists no band_label.<band> keys"
declare -A expected=()
count=0
# Bands and labels end up in file names, so anything but a plain version (no glob, no slash) is refused.
VERSION_SHAPE='^[0-9]+(\.[0-9]+)+$'
mapfile -t band_list <<< "$bands"
for band in "${band_list[@]}"; do
	[[ $band =~ $VERSION_SHAPE ]] || refuse "band '$band' in gradle.properties is not a Minecraft version"
	for loader in "${LOADERS[@]}"; do
		prefix=
		[ "$loader" = fabric ] || prefix=${loader}_
		label=$(prop "${prefix}band_label.$band") || exit 1
		[[ $label =~ $VERSION_SHAPE ]] || refuse "${prefix}band_label.$band '$label' is not a Minecraft version"
		range=$(prop "${prefix}minecraft_range.$band") || exit 1
		jar=$MOD_ID-$version+$loader-mc$label.jar
		sources=$MOD_ID-$version+$loader-mc$label-sources.jar
		[ -z "${expected[$jar]:-}" ] \
			|| refuse "bands ${expected[$jar]} and $band both make $jar: two $loader bands share the label $label"
		expected[$jar]=$band
		expected[$sources]=$band
		for f in "$dist/$jar" "$dist/$sources"; do
			[ -f "$f" ] || refuse "missing build output '$f'"
			[ -s "$f" ] || refuse "build output '$f' is empty"
		done
		check_jar "$loader" "$dist/$jar" "$range"
		count=$((count + 1))
	done
done
# Exactly those files: a stale or stray file would be published by the upload's glob.
for f in "$dist"/* "$dist"/.[!.]*; do
	[ -e "$f" ] || [ -L "$f" ] || continue
	name=${f##*/}
	if [ -z "${expected[$name]:-}" ] || [ ! -f "$f" ] || [ -L "$f" ]; then
		refuse "'$f' is not a build output of any band; dist must hold only the release's jars"
	fi
done
ok "$count jars and their sources jars for bands $(tr '\n' ' ' <<< "$bands")match the band table"

[ -f CHANGELOG.md ] || refuse "no CHANGELOG.md"
# The section runs from a "## <version>" heading (optionally "## [<version>]", optionally followed by a
# date) up to the next level-2 heading; "###" subheadings stay in it. Exact string compare, so a version's
# dots aren't regex wildcards. awk reads the file itself: in a pipe, its early exit would SIGPIPE the writer.
notes=$(awk -v v="$version" '
	{ sub(/\r$/, "") }
	/^##([ \t]|$)/ { if (found) exit; seen = seen || ($2 == v || $2 == "[" v "]"); found = ($2 == v || $2 == "[" v "]"); next }
	found { print }
	END { if (!seen) exit 3 }
' CHANGELOG.md) || refuse "CHANGELOG.md has no '## $version' section"
# Headings alone (an unfilled "### Added") are not release notes.
grep -qvE '^[[:space:]]*(#.*)?$' <<< "$notes" || refuse "CHANGELOG.md's '## $version' section has no entries"
if [ -e "$notes_out" ] || [ -L "$notes_out" ]; then
	refuse "notes file '$notes_out' already exists; refusing to overwrite it"
fi
(set -o noclobber; printf '%s\n' "$notes" > "$notes_out") || refuse "could not create notes file '$notes_out'"
ok "release notes from CHANGELOG.md '## $version' written to $notes_out"

echo "release-guard: PASSED: $tag ($head_commit) may be published with:"
ls -l "$dist"
