vim.opt.runtimepath:prepend(vim.fn.getcwd())
vim.opt.swapfile = false
vim.opt.shadafile = "NONE"
vim.g.loaded_remote_plugins = 1

_G.sift_test_clipboard = { ["+"] = "", ["*"] = "" }
vim.g.clipboard = {
  name = "sift-test-clipboard",
  copy = {
    ["+"] = function(lines)
      _G.sift_test_clipboard["+"] = table.concat(lines, "\n")
    end,
    ["*"] = function(lines)
      _G.sift_test_clipboard["*"] = table.concat(lines, "\n")
    end,
  },
  paste = {
    ["+"] = function()
      return { vim.split(_G.sift_test_clipboard["+"] or "", "\n"), "v" }
    end,
    ["*"] = function()
      return { vim.split(_G.sift_test_clipboard["*"] or "", "\n"), "v" }
    end,
  },
  cache_enabled = 0,
}
