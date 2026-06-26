#!/bin/sh
set -e
FILE="plugin.tar.gz"
trap 'rm -f "$FILE"' EXIT
curl -L https://github.com/8ta4/sift/releases/download/v0.1.0/plugin.tar.gz -o $FILE
echo "8ab0dc77d1dcaeb5ec81539222414a7a4de8b16f2aca223b868420726e80b616  $FILE" | shasum -a 256 -c
tar -xzf $FILE
