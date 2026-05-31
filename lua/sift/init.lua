local config = require("sift.config")

local M = {}

local augroup = nil

local function register_autocmds()
  augroup = vim.api.nvim_create_augroup("sift", { clear = true })

  vim.api.nvim_create_autocmd({ "BufReadPost", "BufNewFile" }, {
    group = augroup,
    pattern = "*.sift",
    callback = function(args)
      require("sift.view").attach(args.buf)
    end,
  })

  vim.api.nvim_create_autocmd("BufWriteCmd", {
    group = augroup,
    pattern = "*.sift",
    callback = function(args)
      require("sift.view").write(args.buf)
    end,
  })

  vim.api.nvim_create_autocmd("BufWipeout", {
    group = augroup,
    pattern = "*.sift",
    callback = function(args)
      require("sift.view").detach(args.buf)
    end,
  })
end

local function register_command()
  pcall(vim.api.nvim_del_user_command, "Sift")
  vim.api.nvim_create_user_command("Sift", function(command)
    require("sift.command").create(command.args)
  end, {
    nargs = 1,
    complete = "file",
  })
end

function M.setup(opts)
  config.setup(opts)
  register_autocmds()
  register_command()

  local current = vim.api.nvim_get_current_buf()
  local name = vim.api.nvim_buf_get_name(current)
  if name:match("%.sift$") then
    require("sift.view").attach(current)
  end
end

return M
