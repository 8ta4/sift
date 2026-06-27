#!/bin/sh
set -e
FILE="plugin.tar.gz"
trap 'rm -f "$FILE"' EXIT
curl -L https://github.com/8ta4/sift/releases/download/v0.1.1/plugin.tar.gz -o $FILE
echo "17f2452cd3c21ec51873a4cef480f172d9c853b1bd0d2c5ddb1b4f19debe8b43  $FILE" | shasum -a 256 -c
tar -xzf $FILE
