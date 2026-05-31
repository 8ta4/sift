local M = {}

local status_by_mark = {
  [" "] = "unmarked",
  A = "flagged",
  D = "done",
  X = "deleted",
}

local mark_by_status = {
  unmarked = " ",
  flagged = "A",
  done = "D",
  deleted = "X",
}

M.status_by_mark = status_by_mark
M.mark_by_status = mark_by_status

function M.trim(text)
  return (text:gsub("^%s+", ""):gsub("%s+$", ""))
end

function M.normalize_status(status)
  if mark_by_status[status] then
    return status
  end
  return "unmarked"
end

function M.parse_line(line)
  local mark, text = line:match("^%[([ ADX])%]%s?(.*)$")
  if mark then
    return {
      status = status_by_mark[mark] or "unmarked",
      text = text,
    }
  end

  return {
    status = "unmarked",
    text = line,
  }
end

function M.parse_lines(lines)
  local items = {}
  for _, line in ipairs(lines) do
    if line ~= "" then
      table.insert(items, M.parse_line(line))
    end
  end
  return items
end

function M.serialize_item(item)
  local status = M.normalize_status(item.status)
  return ("[%s] %s"):format(mark_by_status[status], item.text or "")
end

function M.serialize_lines(items)
  local lines = {}
  for _, item in ipairs(items) do
    table.insert(lines, M.serialize_item(item))
  end
  return lines
end

return M
