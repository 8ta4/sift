#!/bin/sh

script_dir=$(
  CDPATH= cd "$(dirname "$0")" >/dev/null 2>&1 && pwd -P
) || exit 1
repo_root=$(
  CDPATH= cd "$script_dir/.." >/dev/null 2>&1 && pwd -P
) || exit 1

cd "$repo_root" || exit 1

if command -v mktemp >/dev/null 2>&1; then
  NVIM_LOG_FILE=$(mktemp "${TMPDIR:-/tmp}/sift-nvim-log.XXXXXX") || exit 1
else
  NVIM_LOG_FILE="${TMPDIR:-/tmp}/sift-nvim-log.$$"
fi
export NVIM_LOG_FILE

exec nvim --headless -u tests/minimal_init.lua -c "lua require('tests.run').run()" -c qa
