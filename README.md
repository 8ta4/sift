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
      { "A", function() require("sift").toggle("flagged") end, mode = { "n", "v" } },
      { "C", function() require("sift").toggle("unmarked") end, mode = { "n", "v" } },
      { "D", function() require("sift").toggle("done") end, mode = { "n", "v" } },
      { "X", function() require("sift").toggle("deleted") end, mode = { "n", "v" } },
      { "a", function() require("sift").mark("flagged") end, mode = { "n", "v" } },
      { "c", function() require("sift").mark("unmarked") end, mode = { "n", "v" } },
      { "d", function() require("sift").mark("done") end, mode = { "n", "v" } },
      { "i", function() require("sift").filter() end, mode = { "n", "v" } },
      { "s", function() require("sift").open() end, mode = { "n", "v" } },
      { "x", function() require("sift").mark("deleted") end, mode = { "n", "v" } },
    },
    references = {
      "https://en.wiktionary.org/wiki/%s",
    },
  },
}
```

## Usage

### Creating

> How do I create a list?

1. Copy your items to your clipboard.

1. Run `:Sift <name>` in Neovim.

This drops a `<name>.sift` file into your current directory.

`sift` turns each line in your clipboard into a separate item.

### Navigating

> How do I navigate to the item below?

Press `j` in Normal mode or Visual mode. Default Neovim navigation is left alone since `sift` only hijacks keys that have no default function in a read-only buffer.

### Marking

> How do I mark an item as done?

Press `d` in Normal mode or Visual mode. Think "done".

`Space` is often used as a leader key. `a`, `s` and `d` are the only home row keys left that don't have default functions in a read-only buffer. Out of those, `d` is probably the easiest to press. This makes it ideal for what is likely your most frequent marking action.

> How do I open references for an item?

Press `s` in Normal mode or Visual mode. Think "see". See?

In Visual mode, `s` opens the references only for the item under the cursor.

Of the available home row keys `a`, `s` and `d`, `s` is likely the second easiest to type. This makes `s` suitable for what is likely your second most frequent marking action.

> How do I flag an item for a second pass?

Press `a` in Normal mode or Visual mode. Think "again". Seriously. Think again.

Of the available home row keys `a`, `s` and `d`, `a` is likely the hardest to type. This makes `a` suitable for what is likely your third most frequent marking action.

> How do I unmark an item?

Press `c` in Normal mode or Visual mode. Think "clear". Clear?

> How do I soft delete an item from the list?

Press `x` in Normal mode or Visual mode. Think "x out". Since `x` removes a character in Neovim, using `x` here feels natural.

> How do I undo a mark?

Press `u`. This rolls back your last marking action for `d`, `s`, `a`, `c` or `x`.

> How do I redo a mark?

Press `<C-r>`. This reapplies a marking action you undid.

### Saving

> How do I save?

Press `:w`. That tells `sift` to write your changes to the file.

Manual saving is used so you can:

- Cut down on disk activity.

- Avoid excessive network activity if you're syncing through stuff like Dropbox.

- Fit into the standard Neovim workflow.

### Filtering

> How do I hide items marked as done?

Press `D` in Normal mode or Visual mode. The uppercase keys `A`, `X` and `C` follow this same pattern to hide their respective marks.

> How do I toggle the filter to stop excluding items marked as done?

Press `D` in Normal mode or Visual mode. The uppercase keys `A`, `X` and `C` follow this same pattern to toggle their respective exclusions.

> How do I filter the list by regex?

Press `i` in Normal mode or Visual mode, type your regex and press `Enter`. Think "input". This plays into your muscle memory for typing after you press `i` to enter Insert mode.
