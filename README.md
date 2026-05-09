# sift

## The Sift That Keeps on Sifting

`sift` lets you navigate lists, check references and mark items using only your keyboard.

## Setup

> How do I set up `sift`?

1. Make sure you're using [`lazy.nvim`](https://github.com/folke/lazy.nvim).

1. Add this block to your `lazy.nvim` configuration:

```lua
{
  "8ta4/sift",
  opts = {
    keys = {
      { "A", function() require("sift").toggle_flagged() end, mode = { "n", "v" } },
      { "C", function() require("sift").toggle_unmarked() end, mode = { "n", "v" } },
      { "D", function() require("sift").toggle_done() end, mode = { "n", "v" } },
      { "X", function() require("sift").toggle_deleted() end, mode = { "n", "v" } },
      { "a", function() require("sift").mark_flagged() end, mode = { "n", "v" } },
      { "c", function() require("sift").mark_unmarked() end, mode = { "n", "v" } },
      { "d", function() require("sift").mark_done() end, mode = { "n", "v" } },
      { "i", function() require("sift").filter() end, mode = { "n", "v" } },
      { "s", function() require("sift").open() end, mode = { "n", "v" } },
      { "x", function() require("sift").mark_deleted() end, mode = { "n", "v" } },
    },
    references = {
      "https://en.wiktionary.org/wiki/%s",
    },
  },
}
```

## Usage

### Navigating

> How do I navigate to the item below?

Press `j` in Normal mode or Visual mode. Default Neovim navigation is left alone since `sift` only hijacks keys that have no default function in a read-only buffer.

### Marking

> How do I mark an item as done?

Press `d` in Normal mode or Visual mode. Think "done".

> How do I open references for an item?

Press `s` in Normal mode or Visual mode. Think "see". See? In Visual mode, `s` opens the references only for the item under the cursor.

> How do I flag an item for a second pass?

Press `a` in Normal mode or Visual mode. Think "again". Seriously. Think again.

> How do I unmark an item?

Press `c` in Normal mode or Visual mode. Think "clear". Clear?

> How do I soft delete an item from the list?

Press `x` in Normal mode or Visual mode. Think "x out". Since `x` removes a character in Neovim, using `x` here feels natural.

### Filtering

> How do I hide items marked as done?

Press `D` in Normal mode or Visual mode. The uppercase keys `A`, `X` and `C` follow this same pattern to hide their respective marks.

> How do I toggle the filter to stop excluding items marked as done?

Press `D` in Normal mode or Visual mode. The uppercase keys `A`, `X` and `C` follow this same pattern to toggle their respective exclusions.

> How do I filter the list by regex?

Press `i` in Normal mode or Visual mode, type your regex and press `Enter`. Think "input". This plays into your muscle memory for typing after you press `i` to enter Insert mode.
