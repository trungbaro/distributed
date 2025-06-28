//track all known peers discovered via multicast
package peer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KnownPeerTrack {
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public KnownPeerTrack() {
        scheduler.scheduleAtFixedRate(
                () -> removeInactivePeers(30_000), //remove peer that not sending broadcast message in 30s
                0, 30, TimeUnit.SECONDS
        );
    }

    public Map<String, PeerInfo> getAllPeers() {
        return new HashMap<>(peers);
    }

    public void registerPeer(PeerInfo peer) {
        peers.put(peer.getPeerId(), peer);
        lastSeen.put(peer.getPeerId(), System.currentTimeMillis());
    }

    public void removeInactivePeers(long timeoutMs) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PeerInfo>> iterator = peers.entrySet().iterator();

        while(iterator.hasNext()) {
            Map.Entry<String, PeerInfo> entry = iterator.next();

            long lastSeenTime = lastSeen.getOrDefault(entry.getKey(), 0L);
            long inactiveDuration = now - lastSeenTime;
            if(inactiveDuration > timeoutMs) {
                iterator.remove();
            }

        }
    }

}

