local M = {}

local windows = {}
local runner = nil

local function notify_failure(args, result)
  local stderr = result and result.stderr or ""
  local message = "sift: chrome-cli failed: " .. table.concat(args, " ")
  if stderr ~= "" then
    message = message .. "\n" .. stderr
  end
  vim.notify(message, vim.log.levels.ERROR)
end

local function run(opts, args, callback)
  if runner then
    runner(args, callback, opts)
    return
  end

  local cmd = { opts.chrome_cli or "chrome-cli" }
  vim.list_extend(cmd, args)

  if vim.system then
    vim.system(cmd, { text = true }, function(result)
      vim.schedule(function()
        if result.code ~= 0 then
          notify_failure(args, result)
        end
        if callback then
          callback(result)
        end
      end)
    end)
    return
  end

  local stdout = {}
  local stderr = {}
  vim.fn.jobstart(cmd, {
    stdout_buffered = true,
    stderr_buffered = true,
    on_stdout = function(_, data)
      stdout = data or {}
    end,
    on_stderr = function(_, data)
      stderr = data or {}
    end,
    on_exit = function(_, code)
      vim.schedule(function()
        local result = {
          code = code,
          stdout = table.concat(stdout, "\n"),
          stderr = table.concat(stderr, "\n"),
        }
        if code ~= 0 then
          notify_failure(args, result)
        end
        if callback then
          callback(result)
        end
      end)
    end,
  })
end

function M.set_runner(new_runner)
  runner = new_runner
end

function M.reset()
  windows = {}
  runner = nil
end

function M.percent_encode(text)
  return (tostring(text):gsub("([^A-Za-z0-9%-%._~])", function(char)
    return ("%%%02X"):format(char:byte())
  end))
end

function M.expand_template(template, item_text)
  local encoded = M.percent_encode(item_text)
  return (template:gsub("%%s", function()
    return encoded
  end))
end

function M.parse_window_ids(output)
  local ids = {}
  for line in tostring(output or ""):gmatch("[^\r\n]+") do
    local id = line:match("%[(%d+)%]") or line:match("[Ww]indow%s+(%d+)")
    if id then
      table.insert(ids, tonumber(id))
    end
  end
  return ids
end

function M.parse_tabs(output)
  local tabs = {}
  for line in tostring(output or ""):gmatch("[^\r\n]+") do
    local id = line:match("%[(%d+)%]") or line:match("[Tt]ab%s+(%d+)")
    if id then
      table.insert(tabs, {
        id = tonumber(id),
        active = line:find("%*") ~= nil or line:lower():find("active", 1, true) ~= nil,
      })
    end
  end
  return tabs
end

local function contains_id(ids, expected)
  for _, id in ipairs(ids) do
    if id == expected then
      return true
    end
  end
  return false
end

local function remember_last_window(source_index, result)
  local ids = M.parse_window_ids(result and result.stdout or "")
  if #ids > 0 then
    windows[source_index] = ids[#ids]
  end
end

local function create_window(source_index, url, opts)
  run(opts, { "open", url, "-n" }, function()
    run(opts, { "list", "windows" }, function(result)
      remember_last_window(source_index, result)
    end)
  end)
end

local function close_extra_tabs(window_id, keep_tab_id, opts)
  run(opts, { "list", "tabs", "-w", tostring(window_id) }, function(result)
    local tabs = M.parse_tabs(result and result.stdout or "")
    for _, tab in ipairs(tabs) do
      if tab.id ~= keep_tab_id then
        run(opts, { "close", "-t", tostring(tab.id) })
      end
    end
  end)
end

local function reuse_window(window_id, url, opts)
  run(opts, { "list", "tabs", "-w", tostring(window_id) }, function(result)
    local tabs = M.parse_tabs(result and result.stdout or "")
    local active = tabs[1]
    for _, tab in ipairs(tabs) do
      if tab.active then
        active = tab
        break
      end
    end

    local open_args = { "open", url, "-w", tostring(window_id) }
    if active then
      vim.list_extend(open_args, { "-t", tostring(active.id) })
    end

    run(opts, open_args, function()
      if active then
        close_extra_tabs(window_id, active.id, opts)
      end
    end)
  end)
end

function M.open_url(source_index, url, opts)
  local known_window = windows[source_index]
  if not known_window then
    create_window(source_index, url, opts)
    return
  end

  run(opts, { "list", "windows" }, function(result)
    local ids = M.parse_window_ids(result and result.stdout or "")
    if contains_id(ids, known_window) then
      reuse_window(known_window, url, opts)
    else
      windows[source_index] = nil
      create_window(source_index, url, opts)
    end
  end)
end

function M.open_reference(source_index, template, item_text, opts)
  M.open_url(source_index, M.expand_template(template, item_text), opts)
end

return M
