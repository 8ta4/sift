module Host (main) where

import Control.Exception (bracket, catch, throwIO)
import Data.ByteString.Lazy (hPut)
import Network.Socket (Family (AF_UNIX), SockAddr (SockAddrUnix), Socket, SocketType (Stream), accept, bind, close, defaultProtocol, listen, socket)
import Network.Socket.ByteString.Lazy (getContents)
import Relude
import System.Directory (removeFile)
import System.IO.Error (isDoesNotExistError)

main :: IO ()
main = do
  removeIfExists socketPath
  unixSocket <- socket AF_UNIX Stream defaultProtocol
  bind unixSocket $ SockAddrUnix socketPath
  listen unixSocket 1
  forever $ bracket (fst <$> accept unixSocket) close serveClient

socketPath :: FilePath
socketPath = "/tmp/sift.sock"

-- https://stackoverflow.com/a/8502391
removeIfExists :: FilePath -> IO ()
removeIfExists fileName = removeFile fileName `catch` handleExists
  where
    handleExists e
      | isDoesNotExistError e = pure ()
      | otherwise = throwIO e

serveClient :: Socket -> IO ()
serveClient socket = do
  contents <- getContents socket
  hPut stdout contents
  hFlush stdout
