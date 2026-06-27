#!/bin/sh
set -e
FILE="plugin.tar.gz"
trap 'rm -f "$FILE"' EXIT
curl -L https://github.com/8ta4/sift/releases/download/v0.1.2/plugin.tar.gz -o $FILE
echo "7a602463c94466c851feab1c801e67752c607bc2e6fbf78d3da5d577bccf7c98  $FILE" | shasum -a 256 -c
tar -xzf $FILE
