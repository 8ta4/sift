# sift

## Goals

### No Mouse

> Can I use `sift` without a mouse?

Yes.

### No Ads

> Can I block ads on the reference pages?

Yes. You can pair `sift` with uBO Lite to kill ads.

### Latency

> What's the latency target?

The goal is under 0.1 seconds.

[0.1 second is about the limit for having the user feel that the system is reacting instantaneously](https://www.nngroup.com/articles/response-times-3-important-limits/#:~:text=0.1%20second%20is%20about%20the%20limit%20for%20having%20the%20user%20feel%20that%20the%20system%20is%20reacting%20instantaneously).

## Lists

> Does `sift` show lists in a browser?

No. `sift` shows lists in Neovim. You get to leverage Neovim's performance and reuse your configuration.

## References

> Can I use Vimium to navigate within reference windows?

Yes. You can pair `sift` with Vimium to get around.

> Can `sift` reuse tabs for references?

Yes. If a tab for that target is already open, `sift` reuses it.

> If a tab is not open, does `sift` open the reference in a separate window?

Yes. `sift` opens the new tab in a separate window so you can view multiple references simultaneously. You can use your window manager to tile the windows.
