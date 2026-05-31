#!/bin/sh

set -eu

script_path=$0
case $script_path in
  */*) ;;
  *)
    script_path=$(command -v "$script_path") || {
      printf '%s\n' "Could not resolve script path: $0" >&2
      exit 1
    }
    ;;
esac

script_dir=$(CDPATH= cd "$(dirname "$script_path")" && pwd -P)
repo_root=$(CDPATH= cd "$script_dir/.." && pwd -P)

if ! command -v nvim >/dev/null 2>&1; then
  printf '%s\n' "nvim is required for the manual smoke test." >&2
  exit 127
fi

cd "$repo_root"

tmp_base=${TMPDIR:-/tmp}
tmp_dir=$(mktemp -d "${tmp_base%/}/sift-manual.XXXXXX")
sample_file=$tmp_dir/sample.sift
init_file=$tmp_dir/init.lua

mkdir -p "$tmp_dir/state" "$tmp_dir/cache" "$tmp_dir/data" "$tmp_dir/config"

cat >"$sample_file" <<'SIFT'
[ ] alpha
[ ] beta
[A] revisit gamma
[D] completed delta
[X] removed epsilon
SIFT

cat >"$init_file" <<'LUA'
vim.opt.runtimepath:prepend(assert(vim.env.SIFT_MANUAL_REPO_ROOT))
vim.opt.swapfile = false
vim.opt.shadafile = "NONE"
vim.g.loaded_remote_plugins = 1
vim.g.loaded_sift = 1

vim.api.nvim_create_autocmd("VimEnter", {
  once = true,
  callback = function()
    require("sift").setup({
      references = {},
      recovery_path = vim.fn.stdpath("state") .. "/sift/",
    })
  end,
})
LUA

printf 'sift manual smoke-test temp directory: %s\n' "$tmp_dir"
printf 'sample file: %s\n' "$sample_file"
printf '%s\n' 'In Neovim, press d/a/x/c to mark, :w to save, and :q to exit.'

set +e
SIFT_MANUAL_REPO_ROOT=$repo_root \
XDG_STATE_HOME=$tmp_dir/state \
XDG_CACHE_HOME=$tmp_dir/cache \
XDG_DATA_HOME=$tmp_dir/data \
XDG_CONFIG_HOME=$tmp_dir/config \
NVIM_LOG_FILE=$tmp_dir/nvim.log \
nvim -u "$init_file" -n "$sample_file"
status=$?
set -e

printf '\nsift manual smoke-test temp directory: %s\n' "$tmp_dir"
printf 'sample file: %s\n' "$sample_file"
printf 'nvim log file: %s\n' "$tmp_dir/nvim.log"

exit "$status"
