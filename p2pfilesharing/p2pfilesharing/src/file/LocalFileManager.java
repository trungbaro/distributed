//manage list of own shared files
package file;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class LocalFileManager {
    private final String csvPath; //csv file contains list of own shared files
    private final List<String> sharedFiles; //list of file names uploaded from csv file
    private final String sharedFolderPath; //path of folder containing shared files

    public LocalFileManager(String csvPath, String sharedFolderPath) throws IOException {
        this.csvPath = csvPath;
        this.sharedFiles = loadFromCsv(csvPath);
        this.sharedFolderPath = sharedFolderPath;
    }

    public String getCsvPath() {
        return csvPath;
    }

    public String getSharedFolderPath() {
        return sharedFolderPath;
    }

    public List<String> getLocalSharedFiles() {
        return Collections.unmodifiableList(sharedFiles);
    }

    public boolean hasFile(String fileName) {
        return sharedFiles.contains(fileName);
    }

    private List<String> loadFromCsv(String csvPath) throws IOException {
        List<String> files = new ArrayList<>();
        File file = new File(csvPath);

        if(file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        files.add(line.trim());
                    }
                }
            }
        }
        return files;
    }

    public synchronized void addSharedFile(String fileName) throws IOException {
        if(!sharedFiles.contains(fileName)) {
            sharedFiles.add(fileName);
            updateCsv();
        }
    }

    public synchronized void removeSharedFile(String fileName) throws IOException {
        if(sharedFiles.remove(fileName)) {
            updateCsv();
        }
    }

    public void updateCsv() throws IOException {
        File tempFile = new File(csvPath + ".temp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            for(String fileName : sharedFiles) {
                writer.write(fileName);
                writer.newLine();
            }
        }

        File originalFile = new File(csvPath);
        Files.move(tempFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    }

    public File getLocalFileByName(String fileName) {
        if (!hasFile(fileName)) {
            return null;
        }
        File file = new File(sharedFolderPath, fileName);
        return (file.exists() && file.isFile()) ? file : null;
    }

    public void printLocalSharedFiles() {
        System.out.println("=== LOCAL SHARED FILES ===");
        for (String fileName : sharedFiles) {
            File file = new File(sharedFolderPath, fileName);
            System.out.println(fileName + " -> " + (file.exists() ? file.getAbsolutePath() : "NOT FOUND"));
        }
    }
}

