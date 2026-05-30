# sift

## The Sift That Keeps on Sifting

`sift` lets you navigate lists, check references and mark items using only your keyboard.

## Setup

> How do I set up `sift`?

1. Make sure you're using a Mac.

1. Install [Homebrew](https://brew.sh/#install).

1. Install [`lazy.nvim`](https://github.com/folke/lazy.nvim).

1. Open a terminal.

1. Run the following command:

   ```bash
   brew install chrome-cli
   ```

1. Add this block to your `lazy.nvim` configuration:

   ```lua
   {
     "8ta4/sift",
     opts = {
       keys = {
         { "<C-r>", function() require("sift").redo() end, mode = { "n", "v" } },
         { "A", function() require("sift").toggle("flagged") end, mode = { "n", "v" } },
         { "C", function() require("sift").toggle("unmarked") end, mode = { "n", "v" } },
         { "D", function() require("sift").toggle("done") end, mode = { "n", "v" } },
         { "X", function() require("sift").toggle("deleted") end, mode = { "n", "v" } },
         { "a", function() require("sift").mark("flagged") end, mode = { "n", "v" } },
         { "c", function() require("sift").mark("unmarked") end, mode = { "n", "v" } },
         { "d", function() require("sift").mark("done") end, mode = { "n", "v" } },
         { "i", function() require("sift").filter() end, mode = { "n", "v" } },
         { "s", function() require("sift").open() end, mode = { "n", "v" } },
         { "u", function() require("sift").undo() end, mode = { "n", "v" } },
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

`sift` treats every line in your clipboard like a possible item.

### Navigating

> How do I navigate to the item below?

Press `j` in Normal or Visual mode. Default Neovim navigation is left alone since `sift` only hijacks keys that have no default function in a read-only buffer.

> How do I open references for an item?

Press `s` in Normal or Visual mode. Think "see". See?

In Visual mode, `s` opens the references only for the item under the cursor.

`Space` is often used as a leader key. `a`, `s` and `d` are the only home row keys left that don't have default functions in a read-only buffer. Out of those, `s` is likely the second easiest to type.

### Marking

> In Normal mode, how do I mark an item as done?

Press `d` while the cursor is on the item. Think "done".

Of the available home row keys `a`, `s` and `d`, `d` is probably the easiest to press. This makes it ideal for what is likely your most frequent marking action.

> In Normal mode, how do I flag an item for a second pass?

Press `a` while the cursor is on the item. Think "again". Seriously. Think again.

Of the available home row keys `a`, `s` and `d`, `a` is likely the hardest to type.

> In Normal mode, how do I unmark an item?

Press `c` while the cursor is on the item. Think "clear". Clear?

> In Normal mode, how do I soft delete an item from the list?

Press `x` while the cursor is on the item. Think "x out". Since `x` removes a character in Neovim, using `x` here feels natural.

> How do I undo a mark?

Press `u` in Normal or Visual mode. This rolls back your last marking action for `d`, `s`, `a`, `c` or `x`.

> How do I redo a mark?

Press `<C-r>` in Normal or Visual mode. This reapplies a marking action you undid.

> Can I mark several items as done with a single keystroke?

Yes. If your visual selection covers several items, hitting `d` will mark them as done. `a`, `c` and `x` work similarly.

### Saving

> How do I save?

Press `:w` in Normal mode. That tells `sift` to write your changes to the file.

Manual saving is used so you can:

- Avoid excessive network activity if you're syncing through stuff like Dropbox.

- Fit into the standard Neovim workflow.

For peace of mind, `sift` logs every time you change an item's mark status. If Neovim crashes, `sift` will try to replay that log to restore your work when you reopen your file.

> What directory contains my unsaved data?

`sift` drops these recovery logs into `vim.fn.stdpath("state") .. "/sift/"`.

Storing logs here stops your working directory from getting messy. To figure out the exact path on your machine, run:

`:lua print(vim.fn.stdpath("state") .. "/sift/")`

### Filtering

> How do I toggle the visibility of items marked as done?

Press `D` in Normal or Visual mode. You can use the uppercase keys `A`, `X` and `C` the same way to flip the visibility of the items marked with each one.

> How do I filter the list by regex?

Drop your regex into the filter window.

> How do I open the filter window from the list window?

Press `i` in Normal or Visual mode. Think "input". This plays into your muscle memory for typing after you press `i` to enter Insert mode.

> If the filter window is empty, does `sift` apply an empty regex?

No. An empty string is treated as having no regex filter.

In theory, an empty regex only matches an empty item, which doesn't exist when `sift` builds the list.

> How do I get back to the list window while keeping the current regex filter applied?

Press `<Enter>`.
