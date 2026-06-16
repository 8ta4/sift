# sift

## Goals

### No Mouse

> Can I use `sift` without a mouse?

Yes.

### No Ads

> Can I block ads on the reference pages?

Yes.

You can pair `sift` with [uBO Lite](https://github.com/uBlockOrigin/uBOL-home) to kill ads.

### Latency

> Is there a latency target for opening references?

No.

`sift` hands that off to your browser. So latency depends on external factors like your network and the target website.

> Is there a latency target for filtering?

Yes.

The goal is under 0.1 seconds for lists with up to 100,000 items.

[0.1 second is about the limit for having the user feel that the system is reacting instantaneously](https://www.nngroup.com/articles/response-times-3-important-limits/#:~:text=0.1%20second%20is%20about%20the%20limit%20for%20having%20the%20user%20feel%20that%20the%20system%20is%20reacting%20instantaneously).

### Dark Mode

> Can I use dark mode on reference pages?

Yes.

You can pair `sift` with [Dark Reader](https://github.com/darkreader/darkreader).

## Architecture

> Does `sift` use AppleScript to open reference tabs?

No.

When you set a tab's URL via AppleScript without using JavaScript, Chrome ends up taking the OS focus.

I could get the focus back onto your terminal. If you type it quickly, Neovim could miss some keystrokes.

To avoid focus‑stealing, I can fire up AppleScript to run JavaScript inside Chrome. But you'll need to turn on Chrome's `Allow JavaScript from Apple Events` setting. That setting could open your machine up to security risks.

> Does the extension connect to a local HTTP server to talk to the plugin?

No.

- Opening a network port is a security risk I'd rather not take.

- It's also a polling nightmare. Truly appalling. The extension has to keep asking, "Do you have a job for me?"

> Does the extension connect to a local WebSocket server to talk to the plugin?

No.

You're opening a network port. So that security risk is on the table.

Instead, the extension connects to a native messaging host to talk to the plugin.

> Will the service worker connected to a native host go inactive?

No.

"[Connecting to a native messaging host using chrome.runtime.connectNative() will keep a service worker alive.](<https://developer.chrome.com/docs/extensions/develop/concepts/service-workers/lifecycle#:~:text=Connecting%20to%20a%20native%20messaging%20host%20using%20chrome.runtime.connectNative()%20will%20keep%20a%20service%20worker%20alive.>)"

> Does the plugin connect to a WebSocket on the Native Messaging host?

No.

That would mean opening a network port, which is a security risk.

Instead, it just uses a UNIX domain socket.

## Functionality

### Creating

> Will `sift` convert an empty line from your clipboard into an item?

No.

> Can `sift` remove duplicate items?

Yes.

`sift` trims each line's string and removes duplicates while building a list.

### Navigating

> Does `sift` show lists in a browser?

No.

`sift` shows lists in Neovim. You get to leverage Neovim's performance and reuse your configuration.

> Does Neovim stay focused when I open a reference?

Yes.

If the focus shifts to the browser when you open a reference, it'd break your sifting flow.

> If I open references, can the cursor move to another row?

No.

This way, you won't have to go back and mark the item after opening references.

> If I hit `s` in Visual mode, does the visual selection disappear?

No.

Preserving the visual selection allows you to press `d` to mark the highlighted block without having to select the items again.

> Does `sift` use AppleScript to open reference tabs?

No.

`sift` uses a Chrome extension.

Automating Google Chrome via AppleScript forces the browser window to grab focus when a tab is updated.

> Can I use [Vimium](https://github.com/philc/vimium) to navigate within reference windows?

Yes.

You can pair `sift` with Vimium to get around.

> Does `sift` open multiple references in the same window?

No.

`sift` gives each reference source its own window. That way, you can glance at multiple references at once.

> If I close the `sift` window and hit `s`, will `sift` open a new one?

Yes.

> If you press `s` while the window opened by `sift` is still open, will `sift` open a new window?

No.

This will stop the windows count from getting out of hand.

> If you press `s` while the window opened by `sift` is still open, which tab of that window does `sift` use to open a reference?

`sift` uses whichever tab is active in that window.

Changing tabs could look jarring.

> Can `sift` close tabs?

Yes.

If you press `s` when you have a bunch of tabs open in a window that `sift` has opened, `sift` will open a reference in the active tab and close the other tabs in the window.

When you manually open one additional tab in the same window, you can press `⌘ + Shift + [` to navigate to the reference tab. Even when you have a bunch of extra tabs open in the same window, you can still press `⌘ + 1` to go to the reference tab.

Even if you accidentally hit `s` and closed some tabs, you can try reopening them by pressing `⌘ + Shift + t`.

### Marking

> If I press `d` to mark items, can the cursor move to a different row?

Yes.

The cursor will move to the next row after any rows you mark with `d`, as long as there's a next row. Otherwise, the cursor will be on the last row.

You'll probably want to go to the next item anyway.

`a`, `c` and `x` work similarly.

> If I press `d` to mark an item, can the cursor move to another column?

Yes.

`sift` wants the cursor to stay on the same column. But the new row might not have enough columns. If that happens, the cursor jumps to the row's last column.

> If I hit `d` in Visual mode, does the visual selection vanish?

Yes.

If the visual selection remained active, the advancing cursor might stretch the selection box downward.

> If I've pressed `D` to hide done items, does marking an item as done make it disappear?

No.

The item remains visible for the following reasons:

- If an item disappeared and caused the items below it to move up, the sudden layout shift would be jarring.

- Suppose you've also pressed `X` to hide deleted items and you mean to press `d` but accidentally hit `x`. If the item disappeared without distinct feedback, you'd lack clear confirmation of whether it was marked done or deleted.

> If I press `u` to undo a mark, does the visibility of items get restored?

Yes.

When you undo a mark, the items go back to being visible or hidden just like they were when you first marked them.

If `sift` didn't do this, marks could revert without visual feedback.

> If I hit `u` to undo a mark, will the regex filter come back?

Yes.

If the regex filter didn't revert, the undone items appearing on the screen might conflict with your current search input.

> If I hit `u` to undo a mark, will the visibility toggles be restored?

Yes.

It'd be inconsistent not to restore the visibility toggles while restoring the regex filter.

> If I press `u` to undo a mark, can the cursor move?

Yes.

If you set the mark in Normal mode, the cursor jumps back to the row and column the cursor was on.

If you set the mark in Visual mode, the cursor jumps back to the start of the visual selection. That way, it's easier to scan down and see the status of the items you just restored.

> If I press `u` in Visual mode, does the visual selection disappear?

Yes.

Undo rewinds the cursor. Neovim anchors visual selections to the cursor. If you jump the cursor across the file, the selection box might get stretched. Dropping the selection prevents this jarring behavior.

### Saving

> After I save and close a buffer, does `sift` retain my undo history?

Yes.

`sift` drops your undo history into `vim.fn.stdpath("state") .. "/sift/"`.

Storing your undo history here stops your working directory from getting messy. To figure out the exact path on your machine, run:

```
:lua print(vim.fn.stdpath("state") .. "/sift/")
```

> If I mark an item and Neovim crashes before I save, does `sift` keep recovery logs?

Yes.

`sift` drops these recovery logs into `vim.fn.stdpath("state") .. "/sift/"`.

> When I reopen a file, will `sift` reapply the same filters as before?

No.

The items that were unhidden at the time of closing remain unhidden. Excel works like this.

But `sift` keeps track of the filters you used before closing the file. If you add another filter after reopening the file, the combined effect is just like you never reopened it.

> When I reopen a file, will the cursor stay on the same item as before?

Yes.

But the scroll position could change. Neovim behaves like this with other types of files.

> If I open the file again, will the cursor land on the same column as before?

Yes.

Neovim acts like this with other file types.

### Filtering

> Is the filter window a floating one or a split one?

The filter window is a split one. A split window lets you quickly hop between the filter window and the list window using Neovim's usual navigation commands.

> If the filter doesn't hide the item under the cursor, will the cursor stay on that same item?

Yes.

Excel works like this.

If the cursor kept jumping around, it'd be disorienting.

> If a filter doesn't hide the item under the cursor, will the cursor stay on the same column?

Yes.

> If a filter hides the item under the cursor, will the cursor stay on the same item?

No.

In Excel, the active cell stays the same even if a filter hides it.

A cursor in Neovim must be somewhere on the screen.

If all items get hidden, the cursor ends up on an empty line.

If any item stays visible, the cursor moves to one of the visible ones. The cursor will hop to the closest visible item that's lower in the unfiltered list than the one under the cursor, if such an item exists. If there's no lower item, the cursor will hop to the nearest visible item that sits above the current one in the unfiltered list.

> If a filter hides the item under the cursor and I clear it right away, will the cursor jump back to that specific item?

Yes.

If you move the cursor before clearing the filter, it might not hop back to that specific item.

Excel works like that.

> If a filter hides the item under the cursor, can the cursor remain on the same column?

Yes. If the item the cursor will land on has enough columns, the cursor will stay on the same column. Otherwise, the cursor snaps to the last column.

> If I close the list window, does the filter window close too?

Yes.

The filter window is just there to filter the list window.

> If I close the filter window, will the list window close too?

No.

Once you've set the regex filter, you can close the filter window to get more screen space.

> Does opening the filter window clear the regex filter I'm using?

No.

The filter window comes preloaded with the active regex.

> If the string in the filter window isn't a valid regex, can `sift` still apply a regex filter?

Yes.

`sift` will grab the most recent valid regex from the filter window and use it to filter the list.

> If the filter window's regex matches nothing, does the list window show an empty list?

Yes.

Displaying an empty list might look pointless. But some items become visible when you press `D` or other keys. So, an empty list might actually be useful.
