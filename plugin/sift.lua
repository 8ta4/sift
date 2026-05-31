if vim.g.loaded_sift == 1 then
  return
end

vim.g.loaded_sift = 1
require("sift").setup()
