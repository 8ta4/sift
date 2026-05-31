local M = {}

local repo = vim.fn.getcwd()
local test_root = vim.fn.tempname()
vim.fn.mkdir(test_root, "p")

local total = 0
local failures = {}

local function inspect(value)
  return vim.inspect(value)
end

local function eq(actual, expected, message)
  if not vim.deep_equal(actual, expected) then
    error((message or "values differ") .. "\nexpected: " .. inspect(expected) .. "\nactual:   " .. inspect(actual), 2)
  end
end

local function ok(value, message)
  if not value then
    error(message or "expected truthy value", 2)
  end
end

local function lines(bufnr)
  bufnr = bufnr or 0
  return vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
end

local function press(keys)
  local encoded = vim.api.nvim_replace_termcodes(keys, true, false, true)
  vim.api.nvim_feedkeys(encoded, "x", false)
end

local function cursor()
  return vim.api.nvim_win_get_cursor(0)
end

local function set_cursor(row, col)
  vim.api.nvim_win_set_cursor(0, { row, col })
end

local function ok_item_text_col(message)
  ok(cursor()[2] >= 4, message or "cursor should stay on item text")
end

local function reset_modules()
  for name in pairs(package.loaded) do
    if name == "sift" or name:match("^sift%.") then
      package.loaded[name] = nil
    end
  end
end

local function setup(name, opts)
  reset_modules()
  local dir = test_root .. "/" .. name
  vim.fn.mkdir(dir, "p")
  vim.cmd("silent! %bwipeout!")
  vim.fn.chdir(dir)
  vim.opt.runtimepath:prepend(repo)
  require("sift").setup(vim.tbl_deep_extend("force", {
    recovery_path = dir .. "/state",
  }, opts or {}))
  return dir
end

local function write(path, file_lines)
  vim.fn.writefile(file_lines, path)
end

local function read(path)
  return vim.fn.readfile(path)
end

local tests = {}

tests.parser_round_trip = function()
  setup("parser")
  local parser = require("sift.parser")
  local items = parser.parse_lines({
    "[ ] alpha",
    "[A] beta",
    "[D] gamma",
    "[X] delta",
    "legacy [text]",
    "",
    "[ ] leading space",
  })

  eq(vim.tbl_map(function(item)
    return item.status .. ":" .. item.text
  end, items), {
    "unmarked:alpha",
    "flagged:beta",
    "done:gamma",
    "deleted:delta",
    "unmarked:legacy [text]",
    "unmarked:leading space",
  })

  eq(parser.serialize_lines(items), {
    "[ ] alpha",
    "[A] beta",
    "[D] gamma",
    "[X] delta",
    "[ ] legacy [text]",
    "[ ] leading space",
  })
end

tests.sift_command_creates_and_overwrites = function()
  local dir = setup("command")
  vim.fn.setreg("+", " alpha \n\nbeta\nalpha\n")
  vim.cmd("Sift sample")

  local path = dir .. "/sample.sift"
  eq(read(path), { "[ ] alpha", "[ ] beta" })
  ok(vim.api.nvim_buf_get_name(0):match("sample%.sift$"), "command should edit the created file")
  eq(vim.bo.filetype, "sift")
  eq(vim.bo.modifiable, false)

  local j_map = vim.fn.maparg("j", "n", false, true)
  eq(j_map.buffer or 0, 0, "sift must not override normal j navigation")
  ok(vim.fn.maparg("s", "n", false, true).buffer == 1, "s mapping should be buffer-local")

  vim.fn.setreg("+", "new\n")
  vim.cmd("Sift sample.sift")
  eq(read(path), { "[ ] new" })

  local sift_bang_ok = pcall(vim.cmd, "Sift! other")
  eq(sift_bang_ok, false, ":Sift! must not be registered")
end

tests.save_normalizes_existing_files = function()
  local dir = setup("save")
  local path = dir .. "/items.sift"
  write(path, {
    "plain",
    "[A] flagged",
    "[D] done",
    "[X] deleted",
    "",
  })

  vim.cmd.edit(path)
  eq(lines(), {
    "[ ] plain",
    "[A] flagged",
    "[D] done",
    "[X] deleted",
  })
  vim.cmd.write()
  eq(read(path), {
    "[ ] plain",
    "[A] flagged",
    "[D] done",
    "[X] deleted",
  })
  eq(vim.bo.modified, false)
end

tests.marking_undo_redo_and_recovery = function()
  local dir = setup("marking")
  local path = dir .. "/items.sift"
  write(path, { "[ ] alpha", "[ ] beta" })

  vim.cmd.edit(path)
  press("d")
  eq(lines(), { "[D] alpha", "[ ] beta" })
  eq(vim.bo.modified, true)

  press("u")
  eq(lines(), { "[ ] alpha", "[ ] beta" })
  press("<C-r>")
  eq(lines(), { "[D] alpha", "[ ] beta" })

  vim.cmd("silent! bwipeout!")
  vim.cmd.edit(path)
  eq(lines(), { "[D] alpha", "[ ] beta" }, "recovery should replay matching item changes")
  eq(vim.bo.modified, true)

  vim.cmd.write()
  local log_path = require("sift.recovery").log_path(path, require("sift.config").get())
  eq(vim.fn.filereadable(log_path), 0, "save should clear recovery log")
end

tests.mark_keys_preserve_item_text_cursor = function()
  setup("mark_cursor")
  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] alpha" })
  vim.cmd.edit(path)

  for _, key in ipairs({ "d", "a", "c", "x" }) do
    set_cursor(1, 6)
    press(key)
    eq(cursor(), { 1, 6 }, key .. " should preserve the cursor column")
    ok_item_text_col(key .. " should not leave cursor in the status prefix")
  end
end

tests.undo_redo_preserve_item_text_cursor = function()
  setup("undo_redo_cursor")
  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] alpha" })
  vim.cmd.edit(path)

  set_cursor(1, 6)
  press("d")
  press("u")
  eq(cursor(), { 1, 6 }, "undo should preserve the cursor column")
  ok_item_text_col("undo should not leave cursor in the status prefix")

  press("<C-r>")
  eq(cursor(), { 1, 6 }, "redo should preserve the cursor column")
  ok_item_text_col("redo should not leave cursor in the status prefix")
end

tests.status_toggles_keep_or_clamp_item_text_cursor = function()
  setup("status_toggle_cursor")
  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] alpha", "[D] beta-gamma" })
  vim.cmd.edit(path)

  set_cursor(1, 6)
  press("D")
  eq(lines(), { "[ ] alpha" })
  eq(cursor(), { 1, 6 }, "status toggle should keep a valid cursor column")
  ok_item_text_col("status toggle should not leave cursor in the status prefix")

  press("D")
  set_cursor(2, 10)
  press("D")
  eq(lines(), { "[ ] alpha" })
  eq(cursor(), { 1, 4 }, "status toggle should clamp an invalid cursor column to item text")
  ok_item_text_col("clamped status toggle cursor should stay out of the prefix")
end

tests.empty_or_short_item_text_cursor = function()
  setup("short_cursor")
  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] ", "[ ] a" })
  vim.cmd.edit(path)

  set_cursor(1, 4)
  press("d")
  eq(lines()[1], "[D] ")
  ok_item_text_col("empty item text should keep cursor at item text start")

  set_cursor(2, 5)
  press("x")
  eq(lines()[2], "[X] a")
  ok_item_text_col("short item text should keep cursor at or after item text start")
end

tests.visual_marking = function()
  setup("visual")
  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] alpha", "[ ] beta", "[ ] gamma" })
  vim.cmd.edit(path)

  vim.cmd("normal! ggVj")
  press("a")
  eq(lines(), { "[A] alpha", "[A] beta", "[ ] gamma" })
end

tests.filtering = function()
  setup("filter")
  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] alpha", "[D] beta", "[ ] gamma" })
  vim.cmd.edit(path)

  press("D")
  eq(lines(), { "[ ] alpha", "[ ] gamma" })

  press("d")
  eq(lines(), { "[D] alpha", "[ ] gamma" }, "newly hidden status should remain visible until refilter")

  press("D")
  eq(lines(), { "[D] alpha", "[D] beta", "[ ] gamma" })

  press("i")
  eq(vim.bo.filetype, "sift-filter")
  eq(lines(), { "" })

  vim.api.nvim_buf_set_lines(0, 0, -1, false, { "gamma" })
  require("sift.view").update_filter_from_buffer(0)
  local list_buf = vim.b.sift_list_bufnr
  eq(vim.api.nvim_buf_get_lines(list_buf, 0, -1, false), { "[ ] gamma" })

  vim.api.nvim_buf_set_lines(0, 0, -1, false, { "[" })
  require("sift.view").update_filter_from_buffer(0)
  eq(vim.api.nvim_buf_get_lines(list_buf, 0, -1, false), { "[ ] gamma" }, "invalid regex should keep previous valid regex")

  vim.api.nvim_buf_set_lines(0, 0, -1, false, { "" })
  require("sift.view").update_filter_from_buffer(0)
  eq(vim.api.nvim_buf_get_lines(list_buf, 0, -1, false), {
    "[D] alpha",
    "[D] beta",
    "[ ] gamma",
  })

  vim.api.nvim_buf_set_lines(0, 0, -1, false, { "no-match" })
  require("sift.view").update_filter_from_buffer(0)
  eq(vim.api.nvim_buf_get_lines(list_buf, 0, -1, false), { "" })
end

tests.open_references_keymap = function()
  setup("open_references_keymap", {
    references = {
      "https://example.test/%s",
    },
  })
  local browser = require("sift.browser")
  local calls = {}

  browser.set_runner(function(args, callback)
    table.insert(calls, vim.deepcopy(args))
    local stdout = ""
    if args[1] == "list" and args[2] == "windows" then
      stdout = "[42] window\n"
    end
    if callback then
      callback({ code = 0, stdout = stdout, stderr = "" })
    end
  end)

  local path = vim.fn.getcwd() .. "/items.sift"
  write(path, { "[ ] alpha beta", "[ ] gamma" })
  vim.cmd.edit(path)

  press("s")

  eq(calls[1], { "open", "https://example.test/alpha%20beta", "-n" })
  eq(calls[2], { "list", "windows" })
end

tests.browser_wrapper = function()
  setup("browser")
  local browser = require("sift.browser")
  local calls = {}

  browser.set_runner(function(args, callback)
    table.insert(calls, vim.deepcopy(args))
    local stdout = ""
    if args[1] == "list" and args[2] == "windows" then
      stdout = "[10] window\n[20] window\n"
    elseif args[1] == "list" and args[2] == "tabs" then
      stdout = "* [100] active\n[101] extra\n"
    end
    if callback then
      callback({ code = 0, stdout = stdout, stderr = "" })
    end
  end)

  eq(browser.percent_encode("a b/c?"), "a%20b%2Fc%3F")
  eq(browser.expand_template("https://example.test/%s?q=%s", "a b"), "https://example.test/a%20b?q=a%20b")

  browser.open_reference(1, "https://example.test/%s", "alpha beta", { chrome_cli = "chrome-cli" })
  browser.open_reference(2, "https://dict.test/%s", "beta", { chrome_cli = "chrome-cli" })
  browser.open_reference(1, "https://example.test/%s", "gamma", { chrome_cli = "chrome-cli" })

  eq(calls[1], { "open", "https://example.test/alpha%20beta", "-n" })
  eq(calls[2], { "list", "windows" })
  eq(calls[3], { "open", "https://dict.test/beta", "-n" })
  eq(calls[4], { "list", "windows" })
  eq(calls[5], { "list", "windows" })
  eq(calls[6], { "list", "tabs", "-w", "20" })
  eq(calls[7], { "open", "https://example.test/gamma", "-w", "20", "-t", "100" })
  eq(calls[8], { "list", "tabs", "-w", "20" })
  eq(calls[9], { "close", "-t", "101" })
end

local function run_test(name, fn)
  total = total + 1
  local ok_test, err = xpcall(fn, debug.traceback)
  if ok_test then
    print("ok - " .. name)
  else
    print("not ok - " .. name)
    table.insert(failures, name .. "\n" .. err)
  end
end

function M.run()
  for name, fn in pairs(tests) do
    run_test(name, fn)
  end

  if #failures > 0 then
    print("")
    print(table.concat(failures, "\n\n"))
    vim.cmd("silent! %bwipeout!")
    error(("%d/%d tests failed"):format(#failures, total))
  end

  vim.cmd("silent! %bwipeout!")
  print(("%d tests passed"):format(total))
end

return M
