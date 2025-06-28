//listen to file downloading request and send file to peer who has requested
package peer;

import file.LocalFileManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerServer {
    private final int port;
    private final LocalFileManager localFileManager;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private final ExecutorService clientThreadPool;


    public PeerServer(int port, LocalFileManager localFileManager) {
        this.port = port;
        this.localFileManager = localFileManager;
        this.clientThreadPool = Executors.newCachedThreadPool();
    }

    public void start() {
        running = true;
        new Thread(this::runServer, "PeerServer-Thread").start(); //create new thread with name "PeerServer-thread" which running in runServer()
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Peer server started on port " + port);

            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientThreadPool.execute(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             OutputStream out = clientSocket.getOutputStream()) {

            String request = in.readUTF();
            if (request.startsWith("GET_FILE|")) {
                handleFileRequest(request, out);
            } else {
                System.out.println("Unknown request: " + request);
            }
        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private void handleFileRequest(String request, OutputStream out) {
        String fileName = request.split("\\|")[1];
        File file = localFileManager.getLocalFileByName(fileName);

        if (file == null || !file.exists()) {
            System.out.println("File not found: " + fileName);
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            System.out.println("Serving file: " + fileName);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending file " + fileName + ": " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        clientThreadPool.shutdownNow();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
        System.out.println("Peer server stopped");
    }

}
