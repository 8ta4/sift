local config = require("sift.config")
local parser = require("sift.parser")
local recovery = require("sift.recovery")
local browser = require("sift.browser")

local M = {}

local states = {}
local item_text_column = 4
local virtualedit_var = "sift_previous_virtualedit"

local statuses = {
  unmarked = true,
  flagged = true,
  done = true,
  deleted = true,
}

local function set_modifiable(bufnr, value)
  if vim.api.nvim_buf_is_valid(bufnr) then
    vim.bo[bufnr].modifiable = value
  end
end

local function set_modified(bufnr, value)
  if vim.api.nvim_buf_is_valid(bufnr) then
    vim.bo[bufnr].modified = value
  end
end

local function windows_for_buffer(bufnr)
  local wins = {}
  for _, win in ipairs(vim.api.nvim_list_wins()) do
    if vim.api.nvim_win_get_buf(win) == bufnr then
      table.insert(wins, win)
    end
  end
  return wins
end

local function has_virtualedit(value, name)
  for part in tostring(value or ""):gmatch("[^,]+") do
    if part == name then
      return true
    end
  end
  return false
end

local function add_virtualedit_onemore(win)
  local value = vim.wo[win].virtualedit
  if has_virtualedit(value, "all") or has_virtualedit(value, "onemore") then
    return
  end

  local has_previous = pcall(vim.api.nvim_win_get_var, win, virtualedit_var)
  if not has_previous then
    vim.api.nvim_win_set_var(win, virtualedit_var, value)
  end

  if value == "" then
    vim.wo[win].virtualedit = "onemore"
  else
    vim.wo[win].virtualedit = value .. ",onemore"
  end
end

local function restore_virtualedit(win)
  if not vim.api.nvim_win_is_valid(win) then
    return
  end

  local ok, value = pcall(vim.api.nvim_win_get_var, win, virtualedit_var)
  if not ok then
    return
  end

  vim.wo[win].virtualedit = value
  pcall(vim.api.nvim_win_del_var, win, virtualedit_var)
end

local function sync_virtualedit_for_line(win, line)
  if #line <= item_text_column then
    add_virtualedit_onemore(win)
  else
    restore_virtualedit(win)
  end
end

local function clear_keep_visible(state)
  for _, item in ipairs(state.items) do
    item.keep_visible = false
  end
end

local function regex_matches(regex, text)
  return not regex or regex:match_str(text) ~= nil
end

local function is_visible(state, item)
  if state.hidden[item.status] and not item.keep_visible then
    return false
  end
  return regex_matches(state.regex, item.text)
end

local function restored_cursor_col(line, previous_col)
  if previous_col < item_text_column or previous_col > #line then
    return item_text_column
  end

  return previous_col
end

function M.recompute(state)
  state.visible = {}
  state.row_to_item = {}

  for index, item in ipairs(state.items) do
    if is_visible(state, item) then
      table.insert(state.visible, index)
      state.row_to_item[#state.visible] = index
    end
  end
end

function M.clamp_cursor(bufnr, win, opts)
  bufnr = bufnr or vim.api.nvim_get_current_buf()
  win = win or vim.api.nvim_get_current_win()
  opts = opts or {}

  if not vim.api.nvim_buf_is_valid(bufnr) or not vim.api.nvim_win_is_valid(win) then
    return
  end
  if vim.api.nvim_win_get_buf(win) ~= bufnr then
    return
  end

  local state = states[bufnr]
  if not state or #state.visible == 0 then
    restore_virtualedit(win)
    return
  end

  local cursor = vim.api.nvim_win_get_cursor(win)
  local row = math.min(math.max(opts.row or cursor[1], 1), #state.visible)
  local line = vim.api.nvim_buf_get_lines(bufnr, row - 1, row, false)[1]
  if not line then
    restore_virtualedit(win)
    return
  end

  sync_virtualedit_for_line(win, line)

  local col = restored_cursor_col(line, opts.column or cursor[2])
  if row ~= cursor[1] or col ~= cursor[2] then
    pcall(vim.api.nvim_win_set_cursor, win, { row, col })
  end
end

local function clamp_buffer_cursors(bufnr, opts)
  for _, win in ipairs(windows_for_buffer(bufnr)) do
    M.clamp_cursor(bufnr, win, opts)
  end
end

function M.render(bufnr, modified)
  local state = states[bufnr]
  if not state then
    return
  end

  local window_cursors = {}
  for _, win in ipairs(windows_for_buffer(bufnr)) do
    window_cursors[win] = vim.api.nvim_win_get_cursor(win)
  end

  M.recompute(state)

  local lines = {}
  for _, item_index in ipairs(state.visible) do
    table.insert(lines, parser.serialize_item(state.items[item_index]))
  end

  local current_modified = vim.bo[bufnr].modified
  set_modifiable(bufnr, true)
  vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, lines)
  set_modifiable(bufnr, false)

  if modified ~= nil then
    set_modified(bufnr, modified)
  else
    set_modified(bufnr, current_modified)
  end

  for _, win in ipairs(windows_for_buffer(bufnr)) do
    local cursor = window_cursors[win]
    M.clamp_cursor(bufnr, win, {
      row = cursor and cursor[1],
      column = cursor and cursor[2],
    })
  end
end

local function state_for_current()
  return states[vim.api.nvim_get_current_buf()]
end

local function item_index_for_row(state, row)
  return state and state.row_to_item[row] or nil
end

local function visual_rows()
  local start_row = vim.fn.line("v")
  local end_row = vim.fn.line(".")
  if start_row > end_row then
    start_row, end_row = end_row, start_row
  end
  vim.api.nvim_feedkeys(vim.api.nvim_replace_termcodes("<Esc>", true, false, true), "n", false)
  return start_row, end_row
end

local function build_transaction(state, item_indices, status)
  local seen = {}
  local changes = {}

  for _, item_index in ipairs(item_indices) do
    if item_index and not seen[item_index] then
      seen[item_index] = true
      local item = state.items[item_index]
      if item and item.status ~= status then
        table.insert(changes, {
          index = item_index,
          text = item.text,
          before = item.status,
          after = status,
        })
      end
    end
  end

  return { changes = changes }
end

local function apply_transaction(bufnr, transaction, direction, log)
  local state = states[bufnr]
  if not state or not transaction or #transaction.changes == 0 then
    return false
  end

  local applied = { changes = {} }
  for _, change in ipairs(transaction.changes) do
    local item = state.items[change.index]
    if item and item.text == change.text then
      local before = item.status
      local after = direction == "forward" and change.after or change.before
      item.status = after
      item.keep_visible = state.hidden[after] or false
      table.insert(applied.changes, {
        index = change.index,
        text = change.text,
        before = before,
        after = after,
      })
    end
  end

  if #applied.changes == 0 then
    return false
  end

  if log then
    recovery.append(state.file, config.get(), applied)
  end

  M.render(bufnr, true)
  return true
end

function M.mark_rows(bufnr, rows, status)
  local state = states[bufnr]
  if not state then
    return
  end

  local item_indices = {}
  for _, row in ipairs(rows) do
    table.insert(item_indices, item_index_for_row(state, row))
  end

  local transaction = build_transaction(state, item_indices, status)
  if #transaction.changes == 0 then
    return
  end

  if apply_transaction(bufnr, transaction, "forward", true) then
    table.insert(state.undo_stack, transaction)
    state.redo_stack = {}
  end
end

function M.mark_current(status)
  local bufnr = vim.api.nvim_get_current_buf()
  M.mark_rows(bufnr, { vim.api.nvim_win_get_cursor(0)[1] }, status)
end

function M.mark_visual(status)
  local bufnr = vim.api.nvim_get_current_buf()
  local start_row, end_row = visual_rows()
  local rows = {}
  for row = start_row, end_row do
    table.insert(rows, row)
  end
  M.mark_rows(bufnr, rows, status)
end

function M.undo()
  local bufnr = vim.api.nvim_get_current_buf()
  local state = states[bufnr]
  if not state or #state.undo_stack == 0 then
    return
  end

  local transaction = table.remove(state.undo_stack)
  if apply_transaction(bufnr, transaction, "backward", true) then
    table.insert(state.redo_stack, transaction)
  end
end

function M.redo()
  local bufnr = vim.api.nvim_get_current_buf()
  local state = states[bufnr]
  if not state or #state.redo_stack == 0 then
    return
  end

  local transaction = table.remove(state.redo_stack)
  if apply_transaction(bufnr, transaction, "forward", true) then
    table.insert(state.undo_stack, transaction)
  end
end

function M.toggle_status(status)
  local bufnr = vim.api.nvim_get_current_buf()
  local state = states[bufnr]
  if not state or not statuses[status] then
    return
  end

  state.hidden[status] = not state.hidden[status]
  clear_keep_visible(state)
  M.render(bufnr)
end

function M.open_references()
  local state = state_for_current()
  if not state then
    return
  end

  local item_index = item_index_for_row(state, vim.api.nvim_win_get_cursor(0)[1])
  local item = item_index and state.items[item_index]
  if not item then
    return
  end

  local opts = config.get()
  for index, template in ipairs(opts.references or {}) do
    browser.open_reference(index, template, item.text, opts)
  end
end

function M.open_references_visual()
  visual_rows()
  M.open_references()
end

local function find_window_for_buffer(bufnr)
  return windows_for_buffer(bufnr)[1]
end

local function filter_text(filter_bufnr)
  return table.concat(vim.api.nvim_buf_get_lines(filter_bufnr, 0, -1, false), "\n")
end

function M.update_filter_from_buffer(filter_bufnr)
  local list_bufnr = vim.b[filter_bufnr].sift_list_bufnr
  local state = list_bufnr and states[list_bufnr]
  if not state then
    return
  end

  local text = filter_text(filter_bufnr)
  local regex = nil

  if text ~= "" then
    local ok, compiled = pcall(vim.regex, text)
    if not ok then
      M.render(list_bufnr)
      return
    end
    regex = compiled
  end

  if state.regex_text ~= text then
    clear_keep_visible(state)
  end

  state.regex_text = text
  state.regex = regex
  M.render(list_bufnr)
end

function M.open_filter()
  local list_bufnr = vim.api.nvim_get_current_buf()
  local state = states[list_bufnr]
  if not state then
    return
  end

  local existing = state.filter_bufnr and vim.api.nvim_buf_is_valid(state.filter_bufnr)
  if existing then
    local win = find_window_for_buffer(state.filter_bufnr)
    if win then
      vim.api.nvim_set_current_win(win)
    else
      vim.cmd("belowright 1split")
      vim.api.nvim_win_set_buf(0, state.filter_bufnr)
    end
  else
    vim.cmd("belowright 1new")
    state.filter_bufnr = vim.api.nvim_get_current_buf()
    vim.bo[state.filter_bufnr].buftype = "nofile"
    vim.bo[state.filter_bufnr].bufhidden = "wipe"
    vim.bo[state.filter_bufnr].swapfile = false
    vim.bo[state.filter_bufnr].filetype = "sift-filter"
    vim.b[state.filter_bufnr].sift_list_bufnr = list_bufnr
    vim.api.nvim_create_autocmd({ "TextChanged", "TextChangedI" }, {
      buffer = state.filter_bufnr,
      callback = function(args)
        require("sift.view").update_filter_from_buffer(args.buf)
      end,
    })
  end

  set_modifiable(state.filter_bufnr, true)
  vim.api.nvim_buf_set_lines(state.filter_bufnr, 0, -1, false, { state.regex_text or "" })
  set_modified(state.filter_bufnr, false)
  vim.api.nvim_win_set_cursor(0, { 1, #(state.regex_text or "") })
  vim.api.nvim_win_set_height(0, 1)
  vim.cmd("startinsert")
end

local function set_keymap(bufnr, mode, lhs, rhs, desc)
  vim.keymap.set(mode, lhs, rhs, {
    buffer = bufnr,
    silent = true,
    nowait = true,
    desc = desc,
  })
end

local function set_cursor_motion_keymaps(bufnr)
  set_keymap(bufnr, "n", "0", function()
    vim.cmd("normal! 0")
    require("sift.view").clamp_cursor(bufnr)
  end, "sift: move to item text")

  set_keymap(bufnr, "n", "h", function()
    local count = vim.v.count
    if count > 0 then
      vim.cmd("normal! " .. count .. "h")
    else
      vim.cmd("normal! h")
    end
    require("sift.view").clamp_cursor(bufnr)
  end, "sift: move left")
end

function M.set_keymaps(bufnr)
  local keys = config.get().keymaps

  set_cursor_motion_keymaps(bufnr)

  set_keymap(bufnr, { "n", "x" }, keys.open_references, function()
    if vim.fn.mode():match("[vV\22]") then
      require("sift.view").open_references_visual()
    else
      require("sift.view").open_references()
    end
  end, "sift: open references")

  set_keymap(bufnr, "n", keys.mark_done, function()
    require("sift.view").mark_current("done")
  end, "sift: mark done")
  set_keymap(bufnr, "x", keys.mark_done, function()
    require("sift.view").mark_visual("done")
  end, "sift: mark done")

  set_keymap(bufnr, "n", keys.mark_flagged, function()
    require("sift.view").mark_current("flagged")
  end, "sift: mark flagged")
  set_keymap(bufnr, "x", keys.mark_flagged, function()
    require("sift.view").mark_visual("flagged")
  end, "sift: mark flagged")

  set_keymap(bufnr, "n", keys.mark_unmarked, function()
    require("sift.view").mark_current("unmarked")
  end, "sift: clear mark")
  set_keymap(bufnr, "x", keys.mark_unmarked, function()
    require("sift.view").mark_visual("unmarked")
  end, "sift: clear mark")

  set_keymap(bufnr, "n", keys.mark_deleted, function()
    require("sift.view").mark_current("deleted")
  end, "sift: mark deleted")
  set_keymap(bufnr, "x", keys.mark_deleted, function()
    require("sift.view").mark_visual("deleted")
  end, "sift: mark deleted")

  set_keymap(bufnr, { "n", "x" }, keys.undo, function()
    require("sift.view").undo()
  end, "sift: undo mark")
  set_keymap(bufnr, { "n", "x" }, keys.redo, function()
    require("sift.view").redo()
  end, "sift: redo mark")

  set_keymap(bufnr, { "n", "x" }, keys.toggle_done, function()
    require("sift.view").toggle_status("done")
  end, "sift: toggle done")
  set_keymap(bufnr, { "n", "x" }, keys.toggle_flagged, function()
    require("sift.view").toggle_status("flagged")
  end, "sift: toggle flagged")
  set_keymap(bufnr, { "n", "x" }, keys.toggle_deleted, function()
    require("sift.view").toggle_status("deleted")
  end, "sift: toggle deleted")
  set_keymap(bufnr, { "n", "x" }, keys.toggle_unmarked, function()
    require("sift.view").toggle_status("unmarked")
  end, "sift: toggle unmarked")

  set_keymap(bufnr, { "n", "x" }, keys.open_filter, function()
    require("sift.view").open_filter()
  end, "sift: open filter")
end

local function install_cursor_clamp(bufnr)
  local group = vim.api.nvim_create_augroup("sift_cursor_" .. bufnr, { clear = true })

  vim.api.nvim_create_autocmd({ "BufEnter", "BufWinEnter", "CursorMoved", "CursorMovedI" }, {
    group = group,
    buffer = bufnr,
    callback = function(args)
      require("sift.view").clamp_cursor(args.buf)
    end,
  })

  vim.api.nvim_create_autocmd({ "BufLeave", "BufWinLeave" }, {
    group = group,
    buffer = bufnr,
    callback = function()
      restore_virtualedit(vim.api.nvim_get_current_win())
    end,
  })

  return group
end

function M.attach(bufnr)
  bufnr = bufnr or vim.api.nvim_get_current_buf()
  if states[bufnr] then
    return states[bufnr]
  end

  local file = vim.api.nvim_buf_get_name(bufnr)
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local items = parser.parse_lines(lines)
  local replayed = recovery.replay(file, config.get(), items)

  local state = {
    bufnr = bufnr,
    file = file,
    items = items,
    visible = {},
    row_to_item = {},
    hidden = {
      unmarked = false,
      flagged = false,
      done = false,
      deleted = false,
    },
    regex_text = "",
    regex = nil,
    undo_stack = {},
    redo_stack = {},
    cursor_augroup = nil,
  }

  states[bufnr] = state

  vim.bo[bufnr].filetype = "sift"
  vim.bo[bufnr].swapfile = false
  vim.b[bufnr].sift_attached = true
  M.set_keymaps(bufnr)
  state.cursor_augroup = install_cursor_clamp(bufnr)
  M.render(bufnr, replayed > 0)

  return state
end

local function atomic_write(path, lines)
  local tmp = ("%s.tmp.%d.%d"):format(path, vim.fn.getpid(), math.random(100000, 999999))
  local ok, err = pcall(vim.fn.writefile, lines, tmp)
  if not ok then
    error(err)
  end

  local rename_ok, rename_err = os.rename(tmp, path)
  if not rename_ok then
    vim.fn.delete(tmp)
    error(rename_err)
  end
end

function M.write(bufnr)
  bufnr = bufnr or vim.api.nvim_get_current_buf()
  local state = states[bufnr] or M.attach(bufnr)
  local path = state.file ~= "" and state.file or vim.api.nvim_buf_get_name(bufnr)
  if path == "" then
    error("sift: cannot write unnamed buffer")
  end

  atomic_write(path, parser.serialize_lines(state.items))
  recovery.clear(path, config.get())
  M.render(bufnr, false)
end

function M.detach(bufnr)
  local state = states[bufnr]
  if state and state.cursor_augroup then
    pcall(vim.api.nvim_del_augroup_by_id, state.cursor_augroup)
  end
  for _, win in ipairs(windows_for_buffer(bufnr)) do
    restore_virtualedit(win)
  end
  states[bufnr] = nil
end

function M.get_state(bufnr)
  return states[bufnr or vim.api.nvim_get_current_buf()]
end

return M
