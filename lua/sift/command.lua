local parser = require("sift.parser")

local M = {}

local function clipboard_lines()
  local text = ""
  for _, register in ipairs({ "+", "*", '"' }) do
    local ok, value = pcall(vim.fn.getreg, register)
    if ok and value ~= "" then
      text = value
      break
    end
  end

  text = text:gsub("\r\n", "\n"):gsub("\r", "\n")

  local lines = {}
  for line in (text .. "\n"):gmatch("(.-)\n") do
    table.insert(lines, line)
  end
  return lines
end

local function sift_path(name)
  local path = parser.trim(name)
  if path == "" then
    error("sift: name is required")
  end
  if not path:match("%.sift$") then
    path = path .. ".sift"
  end
  return vim.fn.fnamemodify(path, ":p")
end

function M.create(name)
  local seen = {}
  local items = {}

  for _, line in ipairs(clipboard_lines()) do
    local text = parser.trim(line)
    if text ~= "" and not seen[text] then
      seen[text] = true
      table.insert(items, {
        status = "unmarked",
        text = text,
      })
    end
  end

  local path = sift_path(name)
  vim.fn.writefile(parser.serialize_lines(items), path)
  vim.cmd.edit(vim.fn.fnameescape(path))
end

return M
