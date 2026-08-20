#!/bin/sh
# Download SDL3's official Android release into app/libs/, which is where Gradle's prefab looks.
#
# The AAR is not committed: it is 16 MB of binaries built against an NDK and an API level chosen by
# somebody else, and what belongs in a repository is source somebody can read. picokit makes the
# same call about its pico-sdk clone. Re-run this after changing SDL3_VERSION.
set -e

SDL3_VERSION=3.4.14
SDL3_TTF_VERSION=3.2.2

here=$(cd "$(dirname "$0")" && pwd)
libs="$here/app/libs"
aar="$libs/SDL3-$SDL3_VERSION.aar"

ttf="$libs/SDL3_ttf-$SDL3_TTF_VERSION.aar"

mkdir -p "$libs"

# One function, called twice: the two releases have the same shape, and the only things that differ
# are the repository, the asset name and the prefix an older copy is matched by.
fetch() {
    repo=$1
    tag=$2
    asset=$3
    prefix=$4
    want=$5

    if [ -f "$want" ]; then
        echo "already have $want"
        return
    fi

    tmp=$(mktemp -d)

    echo "fetching $asset"
    curl -fsSL -o "$tmp/a.zip" \
        "https://github.com/libsdl-org/$repo/releases/download/$tag/$asset"

    # The zip holds the AAR plus its README and licence; only the AAR is wanted, and only one of
    # them is in there, so the name is taken from the archive rather than assumed.
    unzip -q -j "$tmp/a.zip" "*.aar" -d "$libs"
    rm -rf "$tmp"

    # Any older AAR beside it would be a second module for prefab to choose between.
    for stale in "$libs/$prefix"*.aar; do
        case "$stale" in "$want"|*"_ttf-"*) continue ;; esac
        rm -f "$stale"
    done

    echo "wrote $want"
}

fetch SDL "release-$SDL3_VERSION" "SDL3-devel-$SDL3_VERSION-android.zip" "SDL3-" "$aar"
fetch SDL_ttf "release-$SDL3_TTF_VERSION" "SDL3_ttf-devel-$SDL3_TTF_VERSION-android.zip" "SDL3_ttf-" "$ttf"
