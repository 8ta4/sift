# https://github.com/NixOS/nixpkgs/issues/227966#issuecomment-1521073421
brew 'ghcup'
# We use Homebrew's Neovim instead of `pkgs.neovim`.
# The Nix Neovim package triggers "Vim(let):E117: Unknown function: provider#node#Require".
brew 'neovim'
# We use Homebrew's Neovim instead of `languages.javascript.npm.enable`
# The Nix Neovim package triggers "Vim(echoerr):Cannot find the "neovim" node package. Try :checkhealth".
brew 'node'

cask 'firefox'
cask 'google-chrome'
