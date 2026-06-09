module Host (main) where

import Relude
import System.Directory (getTemporaryDirectory)
import System.FilePath ((</>))

main :: IO ()
main = pure ()

getSocketPath :: IO FilePath
getSocketPath = do
  temporaryDirectory <- getTemporaryDirectory
  pure $ temporaryDirectory </> "sift.sock"
