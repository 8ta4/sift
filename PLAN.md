# Implement `sift` Neovim Plugin

## Summary

Build the modern Neovim Lua plugin described by `README.md` and `DONTREADME.md` from the current docs-only repo. Target macOS, current stable Neovim, lazy.nvim usage, and `chrome-cli`.

## Key Interfaces

- Add `require("sift").setup(opts)` with defaults for keymaps, `references = {}`, `chrome_cli = "chrome-cli"`, and recovery path `vim.fn.stdpath("state") .. "/sift/"`.
- Expose documented functions: `mark(status)`, `toggle(status)`, `filter()`, and `open()`.
- Register only `:Sift <name>`:
  - Read clipboard lines.
  - Create or overwrite `<name>.sift` in the current directory, adding `.sift` if omitted.
  - Do not add `:Sift!`.
- Use readable `.sift` lines:
  - `[ ] item text` for unmarked
  - `[A] item text` for flagged
  - `[D] item text` for done
  - `[X] item text` for deleted
  - Plain unprefixed lines parse as unmarked and normalize on save.

## Implementation Changes

- Add a standard Lua plugin layout under `lua/sift/`, command/autocmd registration in `plugin/sift.lua`, and headless tests under `tests/`.
- Treat `*.sift` buffers as read-only plugin views backed by an item model. `:w` writes the model atomically.
- Marking:
  - Normal mode marks the cursor item.
  - Visual mode marks selected visible items.
  - `mark("unmarked")` clears marks.
  - `u` and `<C-r>` undo/redo full mark transactions.
  - If an active status filter would hide a newly marked item, keep that item visible until the next explicit filter change.
- Filtering:
  - `toggle(status)` flips hidden status filters for `flagged`, `unmarked`, `done`, and `deleted`.
  - `filter()` prompts with `vim.ui.input`; empty input clears the regex filter.
  - Use pure Lua first with cached arrays and virtualized/render-window updates where needed.
  - Invalid regex handling is **TBD** and should be left as an explicit TODO, not silently invented.
- References:
  - `open()` applies configured URL templates to the cursor item text using percent encoding.
  - Browser calls run asynchronously through a small `chrome-cli` wrapper.
  - Tab reuse strategy is **TBD** and should be left as an explicit TODO; do not choose exact-match, fuzzy-match, or window-selection behavior yet.
- Recovery:
  - Append JSONL mark transactions to a per-file recovery log under the documented state directory.
  - On reopening a `.sift` file, replay logged changes only when the item text matches the current item.
  - No mismatch handling is needed beyond not applying entries that do not match.
  - Clear the recovery log after a successful save.

## Test Plan

- Parser/writer tests for prefixed lines, plain legacy lines, special characters, blank lines, and round-trip stability.
- Command tests for `:Sift`, file naming, clipboard splitting, overwrite behavior, and save behavior.
- Marking tests for normal mode, visual mode, undo/redo transactions, modified state, and recovery replay with matching item text.
- Filtering tests for status toggles, regex apply/clear, and "marked item remains visible until refiltered."
- Browser wrapper tests for URL template expansion, percent encoding, async command invocation, and surfaced command failures; exclude tab reuse until the strategy is decided.
- Performance tests generating one million items and benchmarking pure-Lua filtering against the documented 0.1s target.

## Assumptions And TBDs

- Full plugin scope, not MVP.
- Modern Neovim on macOS only.
- `:Sift` intentionally overwrites existing `<name>.sift` files.
- `:Sift!` is intentionally out of scope.
- Invalid regex behavior is TBD.
- Reference tab reuse strategy is TBD.
- Browser extensions for ads, dark mode, and Vimium remain external and are not implemented by `sift`.
