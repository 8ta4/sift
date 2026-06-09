module Host (main) where

import Control.Exception (catch, throwIO)
import Relude
import System.Directory (getTemporaryDirectory, removeFile)
import System.FilePath ((</>))
import System.IO.Error (isDoesNotExistError)

main :: IO ()
main = do
  socketPath <- getSocketPath
  removeIfExists socketPath

getSocketPath :: IO FilePath
getSocketPath = do
  temporaryDirectory <- getTemporaryDirectory
  pure $ temporaryDirectory </> "sift.sock"

-- https://stackoverflow.com/a/8502391
removeIfExists :: FilePath -> IO ()
removeIfExists fileName = removeFile fileName `catch` handleExists
  where
    handleExists e
      | isDoesNotExistError e = pure ()
      | otherwise = throwIO e
