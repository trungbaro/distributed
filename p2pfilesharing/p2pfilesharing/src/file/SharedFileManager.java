//each peer has list of all shared files in network which is stored in a csv file
package file;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import peer.PeerInfo;


public class SharedFileManager {
    private final Map<String, PeerInfo> fileIndex = new ConcurrentHashMap<>(); //mapping file name and which peer own that file
    private final String csvPath;

    public SharedFileManager(String csvPath) throws IOException {
        this.csvPath = csvPath;
        loadFromCsv();
    }

    public PeerInfo getPeersForFiles(String filename) {
        return fileIndex.get(filename);
    }

    public synchronized boolean addFile(String fileName, PeerInfo peer) throws IOException {
        PeerInfo existing = fileIndex.get(fileName);

        if (existing != null) {
            if (existing.equals(peer)) {
                return true;
            } else {
                System.out.println("This file name is already in use by another peer");
                return false;
            }
        }

        fileIndex.put(fileName, peer);
        exportToCsv();
        return true;
    }

    public void loadFromCsv() throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
                fileIndex.clear();
                String header = reader.readLine(); //skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 5) {
                        System.err.println("Invalid line in shared file CSV: " + line);
                        continue; // skip invalid lines
                    }
                    String fileName = parts[0];
                    PeerInfo peer = new PeerInfo(parts[1], parts[2], parts[3], Integer.parseInt(parts[4]));
                    fileIndex.put(fileName, peer);
                }
            }
    }

    public void exportToCsv() throws IOException {
        File tempFile = new File(csvPath + ".temp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write("fileName,peerId,name,ip,port\n");
            for (Map.Entry<String, PeerInfo> entry : fileIndex.entrySet()) {
                PeerInfo peer = entry.getValue();
                writer.write((entry.getKey() + ","
                        + peer.getPeerId() + ","
                        + peer.getName() + ","
                        + peer.getIp() + ","
                        + peer.getPort() + "\n"));
            }
        }

        //automatic file replacement
        File original = new File(csvPath);
        Files.move(tempFile.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);

    }

    public void printSharedFilesIndex() {
        synchronized (this) {
            System.out.println("=== SHARED FILES INDEX ===");
            for (Map.Entry<String, PeerInfo> entry : fileIndex.entrySet()) {
                System.out.print(entry.getKey() + " - ");
                PeerInfo peer = entry.getValue();
                System.out.println(peer.getPeerId() + ","
                        + peer.getName() + ","
                        + peer.getIp() + ","
                        + peer.getPort());
            }
        }
    }

    public Map<String, PeerInfo> getFileIndex() {
        return fileIndex;
    }
}
