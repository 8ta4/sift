module Build (main) where

import Data.Aeson (Object, Value (Object), encode, object, (.=))
import Data.Aeson.KeyMap qualified as KeyMap
import Relude
import System.Directory (createDirectoryIfMissing)
import System.FilePath (takeDirectory)

main :: IO ()
main = do
  writeManifest "../cljs/public/manifest.json" firefox
  writeManifest "../cljs/release/manifest.json" chrome

writeManifest :: FilePath -> Object -> IO ()
writeManifest path config = do
  createDirectoryIfMissing True $ takeDirectory path
  writeFileLBS path $ encode $ Object $ base <> config

base :: Object
base =
  KeyMap.fromList
    [ "manifest_version" .= (3 :: Int),
      "name" .= ("sift" :: Text),
      "permissions"
        .= [ "background" :: Text,
             "nativeMessaging"
           ],
      "version" .= ("0.1.0" :: Text)
    ]

firefox :: Object
firefox =
  KeyMap.fromList
    [ "background"
        .= object
          [ "scripts" .= ["js/background.js" :: Text],
            "type" .= ("module" :: Text)
          ],
      "browser_specific_settings" .= object ["gecko" .= object ["id" .= ("@sift" :: Text)]]
    ]

chrome :: Object
chrome =
  KeyMap.fromList
    [ "background"
        .= object
          [ "service_worker" .= ("js/background.js" :: Text),
            "type" .= ("module" :: Text)
          ],
      "icons" .= object ["128" .= ("icon.png" :: Text)],
      "key" .= ("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsLBbTxtoxub0JD2snkyNBk87JLch7w8GGK5Wupi/P1Q3IS8qvMToUPFcRRLEUW6x+YrgcAtaAjuYjzr8Cye8yTEeLrwvtEqZ1+lH6XpmyZthB/4kBvvMC/rDEgJ4zu2MqjPNEGtcJnPwK5pkZGEzYXZgmAB0YFqGE3LWiGdtC+wShCp8hO4SPOyqytrwz95lM969cEhIV90x9CJHtINSFY2MBIs1GT1gvnnyYUZzhQTgretJZiNq4PdwUKY9pdzU1i2/DKWuhwnZx+D4iMNgNjx/gXU1rYrQ/H5afYA23vq7y6LwShOXRag0UGPqUZDg2ZHkdUQk2uwPXCCeN4QHSwIDAQAB" :: Text)
    ]
