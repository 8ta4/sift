module Host (main) where

import Control.Exception (bracket, catch, throwIO)
import Network.Socket (Family (AF_UNIX), SockAddr (SockAddrUnix), Socket, SocketType (Stream), accept, bind, close, defaultProtocol, listen, socket)
import Relude
import System.Directory (getTemporaryDirectory, removeFile)
import System.FilePath ((</>))
import System.IO.Error (isDoesNotExistError)

main :: IO ()
main = do
  socketPath <- getSocketPath
  removeIfExists socketPath
  unixSocket <- socket AF_UNIX Stream defaultProtocol
  bind unixSocket $ SockAddrUnix socketPath
  listen unixSocket 1
  forever $ bracket (fst <$> accept unixSocket) close serveClient

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

serveClient :: Socket -> IO ()
serveClient socket = pure ()
