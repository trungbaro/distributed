package application;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Tab;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import peer.*;
import file.*;

import java.io.*;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.Map;

public class PeerGUIController {

    // Register tab fields
    @FXML private TextField regNameField;
    @FXML private TextField regPortField;
    @FXML private TextField regMyFileCsvField;
    @FXML private TextField regSharedFileCsvField;
    @FXML private TextField regMyFolderField;
    @FXML private TextArea regOutputArea;

    // Login tab fields
    @FXML private TextField loginIdField;
    @FXML private TextField loginNameField;
    @FXML private TextField loginPortField;
    @FXML private TextField loginMyFileCsvField;
    @FXML private TextField loginSharedFileCsvField;
    @FXML private TextField loginMyFolderField;
    @FXML private TextArea loginOutputArea;

    // Main tab fields
    @FXML private TabPane tabPane;
    @FXML private Tab registerTab;
    @FXML private Tab loginTab;
    @FXML private Tab mainTab;
    @FXML private TextArea mainOutputArea;

    // Peer objects
    private PeerInfo self;
    private KnownPeerTrack knownPeers;
    private LocalFileManager localFileManager;
    private SharedFileManager sharedFileManager;
    private PeerDiscoveryService peerDiscoveryService;
    private PeerServer peerServer;
    private PeerClient peerClient;

    // ========== Register ==========
    @FXML
    private void onRegister() {
        try {
            String name = regNameField.getText().trim();
            int port = Integer.parseInt(regPortField.getText().trim());
            String myFileCsv = regMyFileCsvField.getText().trim();
            String sharedFileCsv = regSharedFileCsvField.getText().trim();
            String myFolder = regMyFolderField.getText().trim();

            String id = UUID.randomUUID().toString();
            String ip = getLanIpAddress();

            self = new PeerInfo(id, name, ip, port);

            // Save info to txt
            try (PrintWriter out = new PrintWriter(new FileWriter(name + ".txt"))) {
                out.println(id);
                out.println(name);
                out.println(ip);
                out.println(port);
                out.println(myFileCsv);
                out.println(sharedFileCsv);
                out.println(myFolder);
            }

            regOutputArea.setText("Registered!\nID: " + id + "\nIP: " + ip + "\nInfo saved to peerinfo.txt");

            // Show main tab and hide register/login
            mainTab.setDisable(false);
            tabPane.getSelectionModel().select(mainTab);
            registerTab.setDisable(true);
            loginTab.setDisable(true);

            initializePeer(myFileCsv, sharedFileCsv, myFolder);
        } catch (Exception e) {
            regOutputArea.setText("Registration failed: " + e.getMessage());
        }
    }

    // ========== Login ==========
    @FXML
    private void onLogin() {
        try {
            String id = loginIdField.getText().trim();
            String name = loginNameField.getText().trim();
            int port = Integer.parseInt(loginPortField.getText().trim());
            String myFileCsv = loginMyFileCsvField.getText().trim();
            String sharedFileCsv = loginSharedFileCsvField.getText().trim();
            String myFolder = loginMyFolderField.getText().trim();
            String ip = getLanIpAddress();

            self = new PeerInfo(id, name, ip, port);

            loginOutputArea.setText("Login successful!\nID: " + id + "\nIP: " + ip);

            // Show main tab and hide register/login
            mainTab.setDisable(false);
            tabPane.getSelectionModel().select(mainTab);
            registerTab.setDisable(true);
            loginTab.setDisable(true);

            // Initialize backend
            initializePeer(myFileCsv, sharedFileCsv, myFolder);

        } catch (Exception e) {
            loginOutputArea.setText("Login failed: " + e.getMessage());
        }
    }

    // ========== Exit ==========
    @FXML
    private void onExit() {
        System.exit(0);
    }

    // ========== List All Shared Files ==========
    @FXML
    private void onListAllFiles() {
        try {
            StringBuilder sb = new StringBuilder("All shared files:\n");
            // Example: iterate over the file index map
            // (Assuming fileIndex is Map<String, PeerInfo> in SharedFileManager)
            for (Map.Entry<String, PeerInfo> entry : sharedFileManager.getFileIndex().entrySet()) {
                String fileName = entry.getKey();
                PeerInfo owner = entry.getValue();
                sb.append(fileName)
                  .append(" (shared by: ")
                  .append(owner.getName())
                  .append(" @ ")
                  .append(owner.getIp())
                  .append(":")
                  .append(owner.getPort())
                  .append(")\n");
            }
            mainOutputArea.setText(sb.toString());
        } catch (Exception e) {
            mainOutputArea.setText("Error: " + e.getMessage());
        }
    }

    // ========== Search File ==========
    @FXML
    private void onSearchFile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter file name to search:");
        dialog.showAndWait().ifPresent(filename -> {
            PeerInfo owner = sharedFileManager.getPeersForFiles(filename);
            if (owner != null) {
                mainOutputArea.setText("File '" + filename + "' is shared by: " + owner.getName() + " (" + owner.getIp() + ":" + owner.getPort() + ")");
            } else {
                mainOutputArea.setText("File not found in network.");
            }
        });
    }

    // ========== Download File ==========
    @FXML
    private void onDownloadFile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter file name to download:");
        dialog.showAndWait().ifPresent(filename -> {
            boolean ok = peerClient.downloadFile(filename, localFileManager.getSharedFolderPath());
            if (ok) {
                mainOutputArea.setText("Downloaded: " + filename);
            } else {
                mainOutputArea.setText("Download failed or file not found.");
            }
        });
    }

    // ========== View Connected Peers ==========
    @FXML
    private void onViewPeers() {
        StringBuilder sb = new StringBuilder("Connected peers:\n");
        for (PeerInfo peer : knownPeers.getAllPeers().values()) {
            sb.append(peer.getName()).append(" (").append(peer.getIp()).append(":").append(peer.getPort()).append(")\n");
        }
        mainOutputArea.setText(sb.toString());
    }

    // ========== List Own Shared Files ==========
    @FXML
    private void onListOwnFiles() {
        StringBuilder sb = new StringBuilder("Own shared files:\n");
        for (String file : localFileManager.getLocalSharedFiles()) {
            sb.append(file).append("\n");
        }
        mainOutputArea.setText(sb.toString());
    }

    // ========== Share New File ==========
    @FXML
    private void onShareNewFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to share");
        File file = fileChooser.showOpenDialog(new Stage());
        if (file != null) {
            try {
                // Copy file to shared folder
                File dest = new File(localFileManager.getSharedFolderPath(), file.getName());
                try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                localFileManager.addSharedFile(file.getName());
                sharedFileManager.addFile(file.getName(), self);
                mainOutputArea.setText("File shared: " + file.getName());
            } catch (Exception e) {
                mainOutputArea.setText("Failed to share file: " + e.getMessage());
            }
        }
    }

    // ========== Utility Methods ==========

    private void initializePeer(String myFileCsv, String sharedFileCsv, String myFolder) throws IOException {
        knownPeers = new KnownPeerTrack();
        localFileManager = new LocalFileManager(myFileCsv, myFolder);
        sharedFileManager = new SharedFileManager(sharedFileCsv);
        peerDiscoveryService = new PeerDiscoveryService(self, knownPeers, localFileManager, sharedFileManager);
        peerServer = new PeerServer(self.getPort(), localFileManager);
        peerClient = new PeerClient(knownPeers, sharedFileManager);

        peerDiscoveryService.start();
        peerServer.start();
    }

    private String getLanIpAddress() throws SocketException {
        // Use your existing getLanIpAddress() logic here
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                java.net.NetworkInterface intf = en.nextElement();
                for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            throw new SocketException("Cannot get LAN IP address");
        }
        return "127.0.0.1";
    }
}
