package cis5550.generic;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cis5550.tools.Logger;
import cis5550.webserver.Server;

public class Coordinator {
    private static final Logger logger = Logger.getLogger(Coordinator.class);

    
    private record ConnectionInfo(String ip, int port, Instant lastPing) {}  
    private static final Map<String, ConnectionInfo> workers = new ConcurrentHashMap<>();
    
    public static List<String> getWorkers() {
        List<String> workers = new ArrayList<>();
        Set<Map.Entry<String, ConnectionInfo>> activeWorkerEntries = getActiveWorkerEntries();
        for (Map.Entry<String, ConnectionInfo> worker : activeWorkerEntries) {
            String workerConnectionInfo = worker.getValue().ip() + ":" + worker.getValue().port();
            workers.add(workerConnectionInfo);
        }
        return workers;
    }

    public static String workerTable() {
        StringBuilder sb = new StringBuilder();
        Set<Map.Entry<String, ConnectionInfo>> activeWorkerEntries = getActiveWorkerEntries();

        sb.append("<table>");
        sb.append("<tr><th>ID</th><th>IP</th><th>port</th><th>link</th></tr>");
        for (var activeWorkerEntry : activeWorkerEntries) {
            String link = String.format("http://%s:%s/", activeWorkerEntry.getValue().ip(),
                    activeWorkerEntry.getValue().port);

            sb.append("<tr>");
            sb.append("<td>");
            sb.append(activeWorkerEntry.getKey());
            sb.append("</td>");
            sb.append("<td>");
            sb.append(activeWorkerEntry.getValue().ip());
            sb.append("</td>");
            sb.append("<td>");
            sb.append(activeWorkerEntry.getValue().port());
            sb.append("</td>");
            sb.append("<td>");
            sb.append("<a href=\"");
            sb.append(link);
            sb.append("\">");
            sb.append(link);
            sb.append("</a>");
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");

        return sb.toString();
    }

    public static void registerRoutes() {
        Server.get("/ping", (req, res) -> {
            String workerID = req.queryParams("id");
            String ip = req.ip();
            String stringPort = req.queryParams("port");

            if (workerID == null || stringPort == null) {
                res.status(400, "Bad Request");
                return null;
            }

            int port = Integer.parseInt(stringPort);

            if (!workers.containsKey(workerID)) {
                logger.info(String.format("RegisteringRoute: %s, %s, %s", workerID, ip, port));
            }
            logger.info(String.format("Received a ping from %s on (%s, %s)", workerID, req.ip(), req.port()));
            workers.put(workerID, new ConnectionInfo(ip, port, Instant.now()));
            return "OK";
        });

        Server.get("/workers", (req, res) -> {
            StringBuilder sb = new StringBuilder();
            Set<Map.Entry<String, ConnectionInfo>> activeWorkerEntries = getActiveWorkerEntries();

            sb.append(activeWorkerEntries.size());
            sb.append("\n");
            for (var activeWorkerEntry : activeWorkerEntries) {
                sb.append(activeWorkerEntry.getKey()); // The workerID
                sb.append(",");
                sb.append(activeWorkerEntry.getValue().ip());
                sb.append(":");
                sb.append(activeWorkerEntry.getValue().port);
                sb.append("\n");
            }

            return sb.toString();
        });
    }

    private static Set<Map.Entry<String, ConnectionInfo>> getActiveWorkerEntries() {
        Set<Map.Entry<String, ConnectionInfo>> workerEntries = new HashSet<>();
        Set<String> expiredWorkers = new HashSet<>();

        for (var workerEntry : workers.entrySet()) {
            // Check if the ping was within the last 15 seconds.
            if (Duration.between(workerEntry.getValue().lastPing, Instant.now()).getSeconds() < 15) {
                workerEntries.add(workerEntry);
            } else {
                expiredWorkers.add(workerEntry.getKey());
            }
        }

        for (String expiredWorkerID : expiredWorkers) {
            workers.remove(expiredWorkerID);
        }

        return workerEntries;
    }
}