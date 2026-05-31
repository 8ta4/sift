# Implement `sift` Neovim Plugin

## Summary

Build the modern Neovim Lua plugin described by `README.md` and `DONTREADME.md` from the current repo. Target macOS, current stable Neovim, lazy.nvim usage, and the Sift Chrome extension.

## Public Interfaces

- Add `require("sift").setup(opts)` with defaults for keymaps, `references = {}`, and recovery path `vim.fn.stdpath("state") .. "/sift/"`.
- Register only `:Sift <name>`:
  - Read clipboard lines, trimming each line.
  - Ignore empty trimmed lines.
  - Remove duplicate trimmed items while preserving first occurrence order.
  - Create or overwrite `<name>.sift` in the current directory, adding `.sift` if omitted.
  - Do not add `:Sift!`.
- Use readable `.sift` lines:
  - `[ ] item text` for unmarked
  - `[A] item text` for flagged
  - `[D] item text` for done
  - `[X] item text` for deleted
  - Plain unprefixed lines parse as unmarked and normalize on save.
- Keep marking, filtering, reference opening, undo/redo, status toggles, recovery replay, and browser control as internal Lua behavior behind commands, autocmds, and buffer-local mappings.

## Implementation Changes

- Add a standard Lua plugin layout under `lua/sift/`, command/autocmd registration in `plugin/sift.lua`, and headless tests under `tests/`.
- Treat `*.sift` buffers as read-only plugin views backed by an item model. `:w` writes the model atomically.
- Keymaps:
  - Leave normal Neovim navigation intact, including `j` for moving to the item below.
  - In list buffers, map `s` to open references, `d` to done, `a` to flagged, `c` to unmarked, `x` to deleted, `u` to undo, `<C-r>` to redo, `D`/`A`/`X`/`C` to toggle status visibility, and `i` to open the filter window.
  - In Visual mode, `s` opens references for the item under the cursor; `d`, `a`, `c`, and `x` apply to every selected visible item.
  - In the filter window, `<Esc>` switches from Insert mode to Normal mode using standard Neovim behavior.
- Marking:
  - Normal mode marks the cursor item.
  - Visual mode marks selected visible items.
  - Clearing an item returns it to the unmarked state.
  - `u` and `<C-r>` undo/redo full mark transactions.
  - If an active status filter would hide a newly marked item, keep that item visible until the next explicit filter change.
- Filtering:
  - Uppercase status mappings flip hidden status filters for `flagged`, `unmarked`, `done`, and `deleted`.
  - The filter mapping opens a split filter window from the list window, preloaded with the active regex, and puts the user in Insert mode.
  - Editing the filter window updates the active regex when the contents are valid.
  - Empty filter text means no regex filter, not an empty regex match.
  - Invalid regex text keeps using the most recent valid regex from the filter window.
  - A valid regex that matches nothing renders an empty list.
  - Use pure Lua first with cached arrays and virtualized/render-window updates where needed.
- References:
  - The reference-opening mapping applies configured URL templates to the cursor item text using percent encoding.
  - Browser calls run asynchronously through a small Sift Chrome extension bridge.
  - The Neovim side sends reference URL, window, and tab management requests to the extension without forcing browser focus.
  - Give each configured reference source its own Chrome window so multiple references can be viewed at once.
  - If the window `sift` opened for a reference source was closed, create a replacement window the next time that source is opened.
  - If the window `sift` opened for a reference source is still open, reuse that window instead of opening another one.
  - When reusing a window, open the reference in whichever tab is currently active in that window, then close the other tabs in that window.
- Recovery:
  - Append JSONL mark transactions to a per-file recovery log under the documented state directory.
  - On reopening a `.sift` file, replay logged changes only when the item text matches the current item.
  - No mismatch handling is needed beyond not applying entries that do not match.
  - Clear the recovery log after a successful save.

## Test Plan

- Parser/writer tests for prefixed lines, plain legacy lines, special characters, blank lines, and round-trip stability.
- Command tests for `:Sift`, file naming, clipboard splitting, trimming, empty-line removal, duplicate removal, overwrite behavior, and save behavior.
- Keymap tests for documented list-window and filter-window mappings without overriding unrelated default navigation.
- Marking tests for normal mode, visual mode, undo/redo transactions, modified state, and recovery replay with matching item text.
- Filtering tests for status toggles, split filter window creation, preloaded active regex, Insert-mode entry, regex apply/clear, invalid-regex fallback, empty result display, and "marked item remains visible until refiltered."
- Reference-opening tests through the documented keymap, plus Chrome extension bridge tests for URL template expansion, percent encoding, async request dispatch, surfaced bridge failures, one window per reference source, window reuse, active-tab targeting, and closing extra tabs in reused windows.
- Performance tests generating one million items and benchmarking pure-Lua filtering against the documented 0.1s target.
- Do not require direct public calls to marking, filtering, status-toggle, or reference-opening helpers; implementation modules may still expose internals to tests where useful.

## Assumptions

- Full plugin scope, not MVP.
- Modern Neovim on macOS only.
- `:Sift` intentionally overwrites existing `<name>.sift` files.
- `:Sift!` is intentionally out of scope.
- `require("sift").setup(opts)` is the only required Lua public API.
- Internal modules may define functions for marking, filtering, toggling status visibility, or opening references, but those functions are not part of the external API contract.
- The Sift Chrome extension is in scope for opening references and managing its Chrome windows and tabs.
- Third-party browser extensions for ads, dark mode, and Vimium remain external and are not implemented by `sift`.
