//automatically discovering other peers and shared files on the local network
package peer;
import file.LocalFileManager;
import file.SharedFileManager;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerDiscoveryService {
    private static final String MULTICAST_GROUP = "239.0.0.0";
    private static final int MULTICAST_PORT = 6789;

    private final PeerInfo self;
    private final KnownPeerTrack peers;
    private final LocalFileManager localFileManager;
    private final SharedFileManager sharedFileManager;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isRunning;
    private MulticastSocket multicastSocket;
    private InetAddress multicastAddress;

    public PeerDiscoveryService(PeerInfo self, KnownPeerTrack peers, LocalFileManager localFileManager, SharedFileManager sharedFileManager) {
        this.self = self;
        this.peers = peers;
        this.localFileManager = localFileManager;
        this.sharedFileManager = sharedFileManager;
        this.scheduler = Executors.newScheduledThreadPool(2);
        isRunning = new AtomicBoolean(false);
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) { //thread safe, only run 1 time
            try {
                this.multicastAddress = InetAddress.getByName(MULTICAST_GROUP);
                this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
                multicastSocket.joinGroup(multicastAddress);

                startBroadcast();
                startListen();
                scheduler.scheduleAtFixedRate(() -> {
                    if (!isRunning.get()) return;
                    annouceAllSharedFile();
                }, 0, 10, TimeUnit.SECONDS); //broadcast own shared files every 10s

                System.out.println("Peer discovery service started");
            } catch (IOException e) {
                System.err.println("Peer discovery service failed: " + e.getMessage());
                stop();
            }
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            if (multicastSocket != null) {
                try {
                    multicastSocket.leaveGroup(multicastAddress);
                    multicastSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing multicast socket: " + e.getMessage());
                }
            }
            System.out.println("Peer discovery service stopped");
        }
    }

    //send udp packet every 3s to a multicast group to announce the presence of current peer (self) to other peers
    public void startBroadcast() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning.get()) return;

            try (DatagramSocket socket = new DatagramSocket()) {
                String message = "PEER_DISCOVERY|" + self.toDiscoveryString();
                byte[] buff = message.getBytes();

                DatagramPacket packet = new DatagramPacket(buff, buff.length, multicastAddress, MULTICAST_PORT);
                socket.send(packet);
                //System.out.println("Broadcast: " + message);
            } catch (Exception e) {
                System.out.println("Broadcast error: " + e.getMessage());
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    //receive multicast message (announce presence or announce shared file) from other peers
    private void startListen() {
        scheduler.execute(() -> {
            byte[] buffer = new byte[1024];
            while (isRunning.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    handleIncomingMessage(message);
                } catch (SocketException e) {
                    if (isRunning.get()) {
                        System.err.println("Socket error in listener: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("Error handling incoming message: " + e.getMessage());
                }
            }
        });
    }

    private void handleIncomingMessage(String message) throws IOException {
        //System.out.println("Received multicast: " + message);
        if (message.startsWith("PEER_DISCOVERY|")) {
            handlePeerDiscovery(message.substring("PEER_DISCOVERY|".length()));
        } else if (message.startsWith("FILE_ANNOUNCEMENT|")) {
            handleFileAnnouncement(message.substring("FILE_ANNOUNCEMENT|".length()));
        }
    }

    private void handlePeerDiscovery(String discoveryMessage) {
        try {
            PeerInfo peer = PeerInfo.parsePeerInfo(discoveryMessage);
            if (!peer.getPeerId().equals(self.getPeerId())) {
                peers.registerPeer(peer);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid peer discovery message: " + e.getMessage());
        }
    }

    private void handleFileAnnouncement(String announceMessage) throws IOException {
        String[] parts = announceMessage.split("\\|");
        if (parts.length == 2) {
            String peerId = parts[0];
            String fileName = parts[1];

            PeerInfo peer = peers.getAllPeers().get(peerId);
            if (peer != null) {
                sharedFileManager.addFile(fileName, peer);
                //System.out.println("Registered new file: " + fileName + " from peer " + peerId);
            }
        }
    }

    public void annouceAllSharedFile() {
        if (!isRunning.get()) return;

        for (String fileName : localFileManager.getLocalSharedFiles()) {
            announceFile(fileName);
        }
    }

    public void announceFile(String fileName) {
        if (!isRunning.get()) return;

        scheduler.execute(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                String message = "FILE_ANNOUNCEMENT|" + self.getPeerId() + "|" + fileName;
                byte[] buff = message.getBytes();

                DatagramPacket packet = new DatagramPacket(buff, buff.length, multicastAddress, MULTICAST_PORT);
                socket.send(packet);
            } catch (Exception e) {
                System.err.println("File announcement error: " + e.getMessage());
            }
        });
    }

}
