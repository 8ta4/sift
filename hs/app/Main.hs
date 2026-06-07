module Main (main) where

import Data.Aeson (Value (Object), encode, object, (.=))
import Data.Aeson.KeyMap qualified as KeyMap
import Relude
import System.Directory (createDirectoryIfMissing)
import System.FilePath (takeDirectory)

main :: IO ()
main = writeManifest "../cljs/public/manifest.json"

writeManifest :: FilePath -> IO ()
writeManifest path = do
  createDirectoryIfMissing True $ takeDirectory path
  writeFileLBS path
    $ encode
    $ Object
    $ KeyMap.fromList
      [ "background"
          .= object
            [ "scripts" .= ["js/background.js" :: Text],
              "type" .= ("module" :: Text)
            ],
        "manifest_version" .= (3 :: Int),
        "name" .= ("sift" :: Text),
        "version" .= ("0.1.0" :: Text)
      ]
