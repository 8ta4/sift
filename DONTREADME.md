# sift

## Goals

### No Mouse

> Can I use `sift` without a mouse?

Yes.

### No Ads

> Can I block ads on the reference pages?

Yes. You can pair `sift` with [uBO Lite](https://github.com/uBlockOrigin/uBOL-home) to kill ads.

### Latency

> Is there a latency target for opening references?

No. `sift` hands that off to your browser. So latency depends on external factors like your network and the target website.

> Is there a latency target for filtering?

Yes. The goal is under 0.1 seconds for lists with up to one million items.

[0.1 second is about the limit for having the user feel that the system is reacting instantaneously](https://www.nngroup.com/articles/response-times-3-important-limits/#:~:text=0.1%20second%20is%20about%20the%20limit%20for%20having%20the%20user%20feel%20that%20the%20system%20is%20reacting%20instantaneously).

### Dark Mode

> Can I use dark mode on reference pages?

Yes. You can pair `sift` with [Dark Reader](https://github.com/darkreader/darkreader).

## Architecture

> Does the extension connect to a local HTTP server to talk to the CLI?

No.

- Opening a network port is a security risk I'd rather not take.

- It's also a polling nightmare. Truly appalling. The extension has to keep asking, "Do you have a job for me?"

> Does the extension connect to a local WebSocket server to talk to the CLI?

No.

You're opening a network port. So that security risk is on the table.

Instead, the extension connects to a native messaging host to talk to the CLI.

> Will the service worker connected to a native host go inactive?

No. "[Connecting to a native messaging host using chrome.runtime.connectNative() will keep a service worker alive.](<https://developer.chrome.com/docs/extensions/develop/concepts/service-workers/lifecycle#:~:text=Connecting%20to%20a%20native%20messaging%20host%20using%20chrome.runtime.connectNative()%20will%20keep%20a%20service%20worker%20alive.>)"

> Does the CLI connect to a WebSocket on the Native Messaging host?

No. That would mean opening a network port, which is a security risk.

Instead, it just uses a UNIX domain socket.

## Creating

> Will `sift` convert an empty line from your clipboard into an item?

No.

> Can `sift` remove duplicate items?

Yes. `sift` trims each line's string and removes duplicates while building a list.

## Navigating

> Does `sift` show lists in a browser?

No. `sift` shows lists in Neovim. You get to leverage Neovim's performance and reuse your configuration.

> Does Neovim stay focused when I open a reference?

Yes. If the focus shifts to the browser when you open a reference, it'd break your sifting flow.

> Does `sift` use AppleScript to open reference tabs?

No. `sift` uses a Chrome extension.

Automating Google Chrome via AppleScript forces the browser window to grab focus when a tab is updated.

> Can I use [Vimium](https://github.com/philc/vimium) to navigate within reference windows?

Yes. You can pair `sift` with Vimium to get around.

> Does `sift` open multiple references in the same window?

No. `sift` gives each reference source its own window. That way, you can glance at multiple references at once.

> If I close the `sift` window and hit `s`, will `sift` open a new one?

Yes.

> If you press `s` while the window opened by `sift` is still open, will `sift` open a new window?

No. This will stop the windows count from getting out of hand.

> If you press `s` while the window opened by `sift` is still open, which tab of that window does `sift` use to open a reference?

`sift` uses whichever tab is active in that window.

Changing tabs could look jarring.

> Can `sift` close tabs?

Yes. If you press `s` when you have a bunch of tabs open in a window that `sift` has opened, `sift` will open a reference in the active tab and close the other tabs in the window.

When you manually open one additional tab in the same window, you can press `⌘ + Shift + [` to navigate to the reference tab. Even when you have a bunch of extra tabs open in the same window, you can still press `⌘ + 1` to go to the reference tab.

Even if you accidentally hit `s` and closed some tabs, you can try reopening them by pressing `⌘ + Shift + t`.

## Marking

> If I've pressed `D` to hide done items, does marking an item as done make it disappear?

No. The item remains visible for the following reasons:

- If an item disappeared and caused the items below it to move up, the sudden layout shift would be jarring.

- Suppose you've also pressed `X` to hide deleted items and you mean to press `d` but accidentally hit `x`. If the item disappeared without distinct feedback, you'd lack clear confirmation of whether it was marked done or deleted.

## Filtering

> If the string in the filter window isn't a valid regex, can `sift` still apply a regex filter?

Yes. `sift` will grab the most recent valid regex from the filter window and use it to filter the list.

> If the filter window's regex matches nothing, does the list window show an empty list?

Yes. Displaying an empty list might look pointless. But some items become visible when you press `D` or other keys. So, an empty list might actually be useful.

> Is the filter window a floating one or a split one?

The filter window is a split one. A split window lets you quickly hop between the filter window and the list window using Neovim's usual navigation commands.

> Does opening the filter window clear the regex filter I'm using?

No. The filter window comes preloaded with the active regex.
