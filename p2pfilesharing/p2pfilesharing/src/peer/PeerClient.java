//send request and download shared file from other peers
package peer;

import file.SharedFileManager;

import java.io.*;
import java.net.*;

public class PeerClient {
    private final KnownPeerTrack knownPeers;
    private final SharedFileManager sharedFileManager;

    public PeerClient(KnownPeerTrack knownPeers, SharedFileManager sharedFileManager) {
        this.knownPeers = knownPeers;
        this.sharedFileManager = sharedFileManager;
    }

    public boolean downloadFile(String fileName, String savePath) {
        PeerInfo peer = sharedFileManager.getPeersForFiles(fileName);

        if (peer == null) {
            System.out.println("File not found in shared files index: " + fileName);
            return false;
        }

        if (!knownPeers.getAllPeers().containsKey(peer.getPeerId())) {
            System.out.println("Peer sharing this file is no longer available: " + peer.getPeerId());
            return false;
        }

        return downloadFromPeer(peer.getPeerId(), fileName, savePath);
    }

    public boolean downloadFromPeer(String peerId, String fileName, String savePath) {
        PeerInfo peer = knownPeers.getAllPeers().get(peerId);
        if (peer == null) {
            System.out.println("Unknown peer ID: " + peerId);
            return false;
        }

        try {
            File directory = new File(savePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File saveFile = new File(directory, fileName);

            try (Socket socket = new Socket(peer.getIp(), peer.getPort());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                InputStream in = socket.getInputStream();
                FileOutputStream fos = new FileOutputStream(saveFile )) {

                out.writeUTF("GET_FILE|" + fileName);
                out.flush();

                byte[] buff = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buff)) > 0) {
                    fos.write(buff, 0, bytesRead);
                }

                System.out.println("Downloaded file: " + fileName);
                return true;
            }

        } catch (IOException e) {
            System.err.println("File requested failed: " + e.getMessage());
            return false;
        }
    }
}
