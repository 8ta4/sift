{
  pkgs,
  ...
}:

{
  # https://devenv.sh/basics/
  env.GREET = "devenv";

  # https://devenv.sh/packages/
  packages = [
    pkgs.ghcid
    pkgs.git
    pkgs.gitleaks
    pkgs.nil
    pkgs.pre-commit
    pkgs.rubyPackages.solargraph
    pkgs.web-ext
  ];

  # https://devenv.sh/languages/
  # languages.rust.enable = true;
  languages.clojure.enable = true;

  # https://devenv.sh/processes/
  # processes.dev.exec = "${lib.getExe pkgs.watchexec} -n -- ls -la";

  # https://devenv.sh/services/
  # services.postgres.enable = true;

  # https://devenv.sh/scripts/
  # https://github.com/mozilla-firefox/firefox/blob/d6bfff43852356ca98af848b4705d37f8d41856f/modules/libpref/init/all.js#L3158
  # https://github.com/mozilla-firefox/firefox/blob/d6bfff43852356ca98af848b4705d37f8d41856f/modules/libpref/init/all.js#L3160
  scripts.browse.exec = ''
    cd "$DEVENV_ROOT/cljs/public" && web-ext run --devtools \
    --pref devtools.toolbox.alwaysOnTop=false \
    --pref extensions.webextensions.base-content-security-policy.v3-with-localhost="script-src 'self' 'wasm-unsafe-eval' http://localhost:* http://127.0.0.1:* 'unsafe-eval';" \
    --pref extensions.webextensions.default-content-security-policy.v3="script-src 'self' 'unsafe-eval';"
  '';
  # Avoid naming this script 'install'.
  # Doing so can shadow the standard system 'install' utility in the PATH.
  # This conflict can cause CI runs to time out.
  scripts.build.exec = ''
    cd "$DEVENV_ROOT/hs" && stack install
  '';
  scripts.hello.exec = ''
    echo hello from $GREET
  '';
  scripts.release.exec = ''
    cd "$DEVENV_ROOT/cljs" && rm -rf release/js && shadow-cljs release background --config-merge '{:output-dir "release/js"}' && rm -rf ../rplugin && shadow-cljs release main
  '';
  scripts.run.exec = ''
    nvim "$DEVENV_ROOT/demo.sift"
  '';
  scripts.log.exec = ''
    nvim +star "+te tail -F node.log -n +1"
  '';
  # ':set -Wprepositive-qualified-module' command works around a ghcid crash related to the `-Wprepositive-qualified-module` warning.
  # The warning can be triggered by GHCi's internal startup process, causing a crash if enabled from the start.
  # The fix is to disable the warning during initial GHCi loading in a .ghci file with `:set -Wno-prepositive-qualified-module`
  # and then use this ghcid command to re-enable it after ghcid has successfully started.
  # The trade-off is that the initial module load is not checked for this specific warning.
  scripts.watch-build.exec = ''
    cd "$DEVENV_ROOT/hs" && ghcid -a \
    -c 'stack ghci --ghci-options "-ghci-script ghci/build.ghci" --no-load ' \
    --no-height-limit \
    -r \
    -s ':set -Wprepositive-qualified-module' \
    -W
  '';
  scripts.watch-host.exec = ''
    cd "$DEVENV_ROOT/hs" && ghcid -a \
    -c 'stack ghci --ghci-options "-ghci-script ghci/host.ghci" --no-load ' \
    --no-height-limit \
    -r \
    -s ':set -Wprepositive-qualified-module' \
    -W
  '';

  # https://devenv.sh/basics/
  enterShell = ''
    hello         # Run scripts directly
    git --version # Use packages
    brew bundle
    export PATH="$DEVENV_ROOT/node_modules/.bin:$PATH"
    npm i
    export PATH="$HOME/.ghcup/bin:$PATH"
    ghcup install hls 2.13.0.0
    ghcup install stack 3.7.1
    ghcup set ghc 9.6.7
    cd "$DEVENV_ROOT/hs" && stack run
    cd "$DEVENV_ROOT"
    export NVIM_NODE_LOG_FILE="$DEVENV_ROOT/node.log"
    export NVIM_NODE_LOG_LEVEL=info
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"
  '';

  # https://devenv.sh/git-hooks/
  # git-hooks.hooks.shellcheck.enable = true;
  git-hooks.hooks = {
    cljfmt.enable = true;
    end-of-file-fixer.enable = true;
    gitleaks = {
      enable = true;
      # https://github.com/gitleaks/gitleaks/blob/81fc7f93b7a0e790c3b272594b883e89f4e36c87/.pre-commit-hooks.yaml#L4
      # Direct execution of gitleaks here results in '[git] fatal: cannot change to 'devenv.nix': Not a directory'.
      entry = "bash -c 'exec gitleaks git --redact --staged --verbose'";
    };
    lua-ls.enable = true;
    # https://github.com/NixOS/nixfmt/blob/1d1bf077b9a6e675e7558db04b4c553e21858253/README.md?plain=1#L165
    nixfmt.enable = true;
    ormolu.enable = true;
    prettier.enable = true;
    stylua.enable = true;
    trim-trailing-whitespace.enable = true;
  };

  # See full reference at https://devenv.sh/reference/options/
}
