package peer;

public class PeerInfo {
    private final String peerId;
    private final String name;
    private final String ip;
    private final int port;

    public PeerInfo(String peerId, String name, String ip, int port) {
        this.peerId = peerId;
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String toDiscoveryString() {
        return peerId + ":" + name + ":" + ip + ":" + port;
    }

    public static PeerInfo parsePeerInfo(String discoveryStr) {
        if(discoveryStr == null || discoveryStr.isEmpty()) {
            throw new IllegalArgumentException("Empty discovery string");
        }

        String[] parts = discoveryStr.split(":");
        if(parts.length != 4) {
            throw new IllegalArgumentException("Invalid discovery string format");
        }

        String peerId = parts[0].trim();
        String name = parts[1].trim();
        String ip = parts[2].trim();
        int port;

        try{
            port = Integer.parseInt(parts[3].trim());
        }catch(NumberFormatException e){
            throw new IllegalArgumentException("Invalid port number in discovery string");
        }

        return new PeerInfo(peerId, name, ip, port);
    }

    public boolean equals(Object obj) {
        if(!(obj instanceof PeerInfo)) return false;
        PeerInfo other = (PeerInfo)obj;
        return this.peerId.equals(other.peerId);
    }

}
