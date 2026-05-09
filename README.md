# sift

## The Sift That Keeps on Sifting

`sift` lets you navigate lists, check references and mark items using only your keyboard.

## Usage

### Navigating

> How do I navigate to the item below?

Press `j` in Normal mode. Default Neovim navigation is left alone since `sift` only hijacks keys that have no default function in a read-only buffer.

### Marking

> How do I mark an item as done?

Press `d` in Normal mode. Think "done".

> How do I open references for an item?

Press `s` in Normal mode. Think "see".

> How do I mark an item to see it again?

Press `a` in Normal mode. Think "again". Seriously. Think "again".

> How do I unmark an item?

Press `c` in Normal mode. Think "clear".

> How do I soft delete an item from the list?

Press `x` in Normal mode. Think "x out". Since `x` removes a character in Neovim, using `x` here feels natural.

### Filtering

> How do I hide items marked as done?

Press `D` in Normal mode. The uppercase keys `A`, `X` and `C` follow this same pattern to hide their respective marks.

> How do I toggle the filter to stop excluding items marked as done?

Press `D` in Normal mode. The uppercase keys `A`, `X` and `C` follow this same pattern to toggle their respective exclusions.

> How do I filter the list by regex?

Press `i` in Normal mode, type your regex and press `Enter`. Think "input". This plays into your muscle memory for typing after you press `i` to enter Insert mode.
