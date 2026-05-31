local M = {}

local keymaps = {
  open_references = "s",
  mark_done = "d",
  mark_flagged = "a",
  mark_unmarked = "c",
  mark_deleted = "x",
  undo = "u",
  redo = "<C-r>",
  toggle_done = "D",
  toggle_flagged = "A",
  toggle_deleted = "X",
  toggle_unmarked = "C",
  open_filter = "i",
}

function M.defaults()
  return {
    references = {},
    chrome_cli = "chrome-cli",
    recovery_path = vim.fn.stdpath("state") .. "/sift/",
    keymaps = vim.deepcopy(keymaps),
  }
end

M.options = M.defaults()

function M.setup(opts)
  opts = opts or {}
  M.options = vim.tbl_deep_extend("force", M.defaults(), opts)

  if M.options.recovery_path:sub(-1) ~= "/" then
    M.options.recovery_path = M.options.recovery_path .. "/"
  end

  return M.options
end

function M.get()
  return M.options
end

return M
