local M = {}

local function json_encode(value)
  if vim.json and vim.json.encode then
    return vim.json.encode(value)
  end
  return vim.fn.json_encode(value)
end

local function json_decode(value)
  if vim.json and vim.json.decode then
    return vim.json.decode(value)
  end
  return vim.fn.json_decode(value)
end

function M.log_path(file, opts)
  local dir = opts.recovery_path
  vim.fn.mkdir(dir, "p")
  return dir .. vim.fn.sha256(vim.fn.fnamemodify(file, ":p")) .. ".jsonl"
end

function M.append(file, opts, transaction)
  if not file or file == "" or not transaction or not transaction.changes or #transaction.changes == 0 then
    return
  end

  local path = M.log_path(file, opts)
  local line = json_encode({
    time = os.time(),
    changes = transaction.changes,
  })

  vim.fn.writefile({ line }, path, "a")
end

function M.replay(file, opts, items)
  local path = M.log_path(file, opts)
  if vim.fn.filereadable(path) == 0 then
    return 0
  end

  local applied = 0
  for _, line in ipairs(vim.fn.readfile(path)) do
    if line ~= "" then
      local ok, entry = pcall(json_decode, line)
      if ok and type(entry) == "table" and type(entry.changes) == "table" then
        for _, change in ipairs(entry.changes) do
          local item = items[change.index]
          if item and item.text == change.text then
            item.status = change.after
            applied = applied + 1
          end
        end
      end
    end
  end

  return applied
end

function M.clear(file, opts)
  local path = M.log_path(file, opts)
  if vim.fn.filereadable(path) == 1 then
    vim.fn.delete(path)
  end
end

return M
