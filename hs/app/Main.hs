module Main (main) where

import Relude
import System.Directory (createDirectoryIfMissing)
import System.FilePath (takeDirectory)

main :: IO ()
main = writeManifest "../cljs/public/manifest.json"

writeManifest :: FilePath -> IO ()
writeManifest path = createDirectoryIfMissing True $ takeDirectory path
