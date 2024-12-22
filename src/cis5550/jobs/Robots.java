package cis5550.jobs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Robots {
    private Robots() {}
    
    public sealed interface RetrieveRobotsTXTResult {
        public record Ok(String robotsTXT) implements RetrieveRobotsTXTResult {}
        public record NotPresent() implements RetrieveRobotsTXTResult {}
        public record OtherError(Exception e) implements RetrieveRobotsTXTResult {}
    }
    public static RetrieveRobotsTXTResult retrieveRobotsTXT(String protocol, String host, int port) {
        try {
            URL robotsURL = new URL(protocol, host, port, "/robots.txt");
            HttpURLConnection robotConnection = (HttpURLConnection) robotsURL.openConnection();
            robotConnection.setRequestMethod("GET");
            robotConnection.setRequestProperty("User-Agent", "cis5550-crawler");
            robotConnection.setConnectTimeout(1000);
            robotConnection.setReadTimeout(3000);
            robotConnection.connect();
            
            if (robotConnection.getResponseCode() == 200) {
                return new RetrieveRobotsTXTResult.Ok(new String(robotConnection.getInputStream().readAllBytes()));
            } else {
                return new RetrieveRobotsTXTResult.NotPresent();
            }
        } catch (MalformedURLException e) {
            String inputs = String.format("(%s, %s, %s)", protocol, host, port);
            throw new IllegalArgumentException("Cannot form a valid url from " + inputs);
        } catch (SocketTimeoutException e) {
            return new RetrieveRobotsTXTResult.NotPresent();
        } catch (IOException e) {
            return new RetrieveRobotsTXTResult.OtherError(e);
        }
    }
    
    public sealed interface RobotRule extends Serializable {
        public record Allow(String urlPrefix) implements RobotRule {}
        public record Disallow(String urlPrefix) implements RobotRule {}
        public record CrawlDelay(double crawlDelay) implements RobotRule {}
    }
    public static List<RobotRule> parseRobotsTXT(String robotsTXT, String userAgent) {
        String[] lines = robotsTXT.split("\\r?\\n");
        
        List<RobotRule> agentSpecificRules = new ArrayList<>();
        List<RobotRule> defaultRules = new ArrayList<>();
        
        String userAgentContext = null;
        for (String line : lines) {
            line = line.strip();
            if (line.isEmpty() || line.isBlank() || line.startsWith("#")) {
                continue;
            }
            
            String[] lineParts = line.split(":", 2);
            if (lineParts.length != 2) {
                continue;
            }
            
            String rule = lineParts[0].strip().toLowerCase();
            String value = lineParts[1].strip();
            
            if (rule.equals("user-agent")) {
                userAgentContext = value.toLowerCase();
                continue;
            }
            
            if (userAgentContext == null ||
                (!userAgentContext.equals("*") && !userAgentContext.equals(userAgent))) {
                continue;
            }
            
            RobotRule robotRule = null;
            if (rule.equals("allow")) {
                robotRule = new RobotRule.Allow(value);
            } else if (rule.equals("disallow")) {
                robotRule = new RobotRule.Disallow(value);
            } else if (rule.equals("crawl-delay")) {
                try {
                    double timeDelay = Double.parseDouble(value);
                    robotRule = new RobotRule.CrawlDelay(timeDelay);
                } catch (Exception e) {}
            }
            
            if (robotRule == null) {
                continue;
            }
            
            if (userAgentContext.equals("*")) {
                defaultRules.add(robotRule);
            } else {
                agentSpecificRules.add(robotRule);
            }
        }
        
        return agentSpecificRules.size() > 0 ? agentSpecificRules : defaultRules;
    }
    
    public static byte[] serializeRobotRules(List<RobotRule> rules) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            oos.writeObject(rules);
            byte[] data = baos.toByteArray();
            return data;
        }
    }
    
    public static List<RobotRule> deserializeRobotRules(byte[] rules) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(rules))) {
            @SuppressWarnings("unchecked")
            List<RobotRule> newRules = (List<RobotRule>) ois.readObject();
            return newRules;
        }
    }
    
    public static double getHostCrawlDelay(List<RobotRule> robotRules) {
        double timeDelay = 1.0;
        
        for (RobotRule robotRule : robotRules) {
            if (robotRule instanceof RobotRule.CrawlDelay crawlDelay) {
                timeDelay = crawlDelay.crawlDelay();
            }
        }
        
        return timeDelay;
    }
    
    public static boolean urlAllowed(List<RobotRule> robotRules, String urlPath) {
        for (RobotRule robotRule : robotRules) {
            if (robotRule instanceof RobotRule.Allow allow) {
                if (urlPath.startsWith(allow.urlPrefix())) {
                    return true;
                }
            } else if (robotRule instanceof RobotRule.Disallow disallow) {
                if (urlPath.startsWith(disallow.urlPrefix())) {
                    return false;
                }
            }
        }
        return true;
    }
}
