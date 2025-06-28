package application;

import file.*;
import peer.*;


import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Scanner;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.UUID;


public class Peer2 {
    private final PeerInfo self;
    private final KnownPeerTrack knownPeers;
    private final LocalFileManager localFileManager;
    private final SharedFileManager sharedFileManager;
    private final PeerDiscoveryService peerDiscoveryService;
    private final PeerServer peerServer;
    private final PeerClient peerClient;
    private final String myFileCsvPath;
    private final String sharedFileCsvPath;
    private final String myFolderPath;

    public Peer2(PeerInfo self, String myFileCsvPath, String sharedFileCsvPath, String myFolderPath) throws IOException {
        this.self = self;
        this.knownPeers = new KnownPeerTrack();

        this.myFileCsvPath = myFileCsvPath;
        this.sharedFileCsvPath = sharedFileCsvPath;
        this.myFolderPath = myFolderPath;

        //file managers
        this.localFileManager = new LocalFileManager(myFileCsvPath, myFolderPath);
        this.sharedFileManager = new SharedFileManager(sharedFileCsvPath);

        //network service
        this.peerDiscoveryService = new PeerDiscoveryService(self, knownPeers, localFileManager, sharedFileManager);
        this.peerServer = new PeerServer(self.getPort(), localFileManager);
        this.peerClient = new PeerClient(knownPeers, sharedFileManager);
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            //register as new peer or login with peer id
            System.out.println("=== P2P File Sharing System ===");
            System.out.println("1. Register as new peer");
            System.out.println("2. Login as existing peer");
            System.out.print("Choose option: ");
            int option = Integer.parseInt(scanner.nextLine());

            PeerInfo self;
            if (option == 1) {
                self = registerNewPeer(scanner);
            } else {
                self = loginExistingPeer(scanner);
            }

            //configure csv file path
            System.out.print("Enter path for CSV file containing your shared files: ");
            String myFileCsvPath = scanner.nextLine().trim();

            System.out.print("Enter path for CSV file containing network shared files: ");
            String sharedFileCsvPath = scanner.nextLine().trim();

            System.out.print("Enter path for your shared folder: ");
            String myFolderPath = scanner.nextLine().trim();

            //initialize application
            Peer1 app = new Peer1(self, myFileCsvPath, sharedFileCsvPath, myFolderPath);
            app.start();
            Thread.sleep(5000);

        } catch (Exception e) {
            System.err.println("Application failed to start: " + e.getMessage());
        }
    }

    private static PeerInfo registerNewPeer(Scanner scanner) throws UnknownHostException, SocketException {
        System.out.println("\n=== New Peer Registration ===");
        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();

        //auto configuration
        String peerId = UUID.randomUUID().toString();
        String ip = getLanIpAddress();

        System.out.print("Enter port number [default: 8080]: ");
        String portStr = scanner.nextLine().trim();
        int port = portStr.isEmpty() ? 8080 : Integer.parseInt(portStr);

        System.out.println("\nRegistration successful!");
        System.out.println("Your ID: " + peerId);
        System.out.println("Your IP: " + InetAddress.getByName(ip));
        System.out.println("Your Port: " + port);

        PeerInfo self = new PeerInfo(peerId, name, ip, port);
        savePeerInfo(self);
        System.out.println("Your configuration has been saved in p2p_" + name + ".txt");
        System.out.println();

        return self;
    }

    private static String getLanIpAddress() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(nif -> {
                    try {
                        return nif.isUp() && !nif.isLoopback();
                    } catch (SocketException e) {
                        return false;
                    }
                })
                .flatMap(nif -> Collections.list(nif.getInetAddresses()).stream())
                .filter(addr -> addr instanceof Inet4Address && !addr.isLoopbackAddress())
                .findFirst()
                .orElse(InetAddress.getLoopbackAddress())
                .getHostAddress();
    }

    private static void savePeerInfo(PeerInfo peer) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("p2p_" + peer.getName() + ".txt"))) {
            writer.write("ID: " + peer.getPeerId());
            writer.newLine();
            writer.write("Name: " + peer.getName());
            writer.newLine();
            writer.write("IP: " + peer.getIp());
            writer.newLine();
            writer.write("Port: " + peer.getPort());
        } catch (IOException e) {
            System.err.println("Could not save info as text");
        }
    }

    private static PeerInfo loginExistingPeer(Scanner scanner) throws IOException {
        System.out.println("\n=== Existing Peer Login ===");
        System.out.println("Please enter the information exactly as it appears in the text file to continue using your previous data");

        System.out.print("Enter your ID: ");
        String peerId = scanner.nextLine().trim();

        System.out.print("Enter your registered name: ");
        String name = scanner.nextLine().trim();

        System.out.print("Enter your registered port: ");
        String portStr = scanner.nextLine().trim();
        int port = portStr.isEmpty() ? 8080 : Integer.parseInt(portStr);

        String ip = getLanIpAddress();

        return new PeerInfo(peerId, name, ip, port);
    }

    public void start() {
        peerDiscoveryService.start();
        peerServer.start();
        startCLI();
    }

    private void startCLI() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "0":
                    running = false;
                    shutdown();
                    System.out.println("Exiting application...");
                    break;
                case "1":
                    listAvailableFiles();
                    break;
                case "2":
                    searchForFle(scanner);
                    break;
                case "3":
                    downloadFile(scanner);
                    break;
                case "4":
                    viewConnectedPeers();
                    break;
                case "5":
                    listMySharedFiles();
                    break;
                case "6":
                    shareNewFile(scanner);
                    break;
                default:
                    System.out.println("Invalid choice");
            }
        }
        scanner.close();
    }

    private void printMenu() {
        System.out.println("\n=== P2P FILE SHARING ===");
        System.out.println("0. Exit");
        System.out.println("1. List available files");
        System.out.println("2. Search for a file");
        System.out.println("3. Download a file");
        System.out.println("4. View connected peers");
        System.out.println("5. List my shared files");
        System.out.println("6. Share a new file");
        System.out.print("Enter your choice: ");
    }

    private void shutdown() {
        try {
            sharedFileManager.exportToCsv();
            System.out.println("Shared files index saved to csv file successfully");
        } catch (IOException e) {
            System.err.println("Failed to save shared files index to csv file: " + e.getMessage());
        }
    }

    private void listAvailableFiles() {
        System.out.println("\n=== AVAILABLE FILES ===:");
        sharedFileManager.printSharedFilesIndex();
    }

    private void searchForFle(Scanner scanner) {
        System.out.print("\nEnter a file name to search: ");
        String fileName = scanner.nextLine().trim();

        PeerInfo peer = sharedFileManager.getPeersForFiles(fileName);
        if (peer != null) {
            System.out.println("File found!");
            System.out.println("File: " + fileName);
            System.out.println("Shared by: " + peer.getName() + " (ID: " + peer.getPeerId() + ")");
            System.out.println("Host: " + peer.getPeerId() + ":" + peer.getPort());
        } else {
            System.out.println("File not found");
        }
    }

    private void downloadFile(Scanner scanner) {
        System.out.print("\nEnter a file name to download: ");
        String fileName = scanner.nextLine().trim();

        System.out.print("\nEnter save path: ");
        String savePath = scanner.nextLine().trim();

        boolean success = peerClient.downloadFile(fileName, savePath);
        System.out.println(success ? "Download completed successfully!" : "Download failed.");
    }

    private void viewConnectedPeers() {
        System.out.println("\n=== CONNECTED PEERS ===");
        Map<String, PeerInfo> peers = knownPeers.getAllPeers();

        if (peers.isEmpty()) {
            System.out.println("No peers currently connected.");
            return;
        }

        for(Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            String peerId = entry.getKey();
            PeerInfo peer = entry.getValue();

            System.out.println("ID: " + peerId);
            System.out.println("Name: " + peer.getName());
            System.out.println("Host: " + peer.getIp() + ":" + peer.getPort());
            System.out.println("----------------------");
        }
    }

    private void listMySharedFiles() {
        System.out.println("\n=== My SHARED FILES ===");
        localFileManager.printLocalSharedFiles();
    }

    private void shareNewFile(Scanner scanner) {
        System.out.print("\nEnter a file path to share: ");
        String filePath = scanner.nextLine().trim();
        File dest = null;

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileNotFoundException("File does not exist: " + filePath);
            }

            //copy file to local shared folder
            String fileName = file.getName();
            dest = new File(localFileManager.getSharedFolderPath(), fileName);
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            //update local file manager
            localFileManager.addSharedFile(fileName);

            //register in shared file manager
            if(sharedFileManager.addFile(fileName, self)) {
                //broadcast sharing file announcement
                //peerDiscoveryService.announceFile(fileName);
                System.out.println("Sucessfully shared file: " + fileName);
            } else {
                System.out.println("File was not added to shared files index of other peers, file name may already exist " + fileName);
            }

        } catch (IOException e) {
            System.out.println("Failed to share file: " + e.getMessage());

            //clean up if partially complete
            if(dest != null && dest.exists()) {
                try {
                    dest.delete();
                } catch (SecurityException se) {
                    System.err.println("Could not delete temporary file: " + se.getMessage());
                }
            }
        }
    }

}
