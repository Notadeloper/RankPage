package cis5550.kvs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;

import cis5550.tools.Logger;

public final class StaticKVSClient{
    private static final Logger logger = Logger.getLogger(StaticKVSClient.class);
    
    public static int workerIndexForKey(List<StaticKVSWorkerEntry> workers, String key) {
        int chosenWorker = workers.size() - 1;
        if (key != null) {
            for (int i = 0; i < workers.size() - 1; i++) {
                if ((key.compareTo(workers.get(i).id()) >= 0) && (key.compareTo(workers.get(i + 1).id()) < 0)) {
                    chosenWorker = i;
                }
            }
        }

        return chosenWorker;
    }
    
    public static String workerAddressForKey(StaticKVSConfig config, String rowName) {
        List<StaticKVSWorkerEntry> workers = config.workers();
        return workers.get(workerIndexForKey(workers, rowName)).address();
    }
    
    private record HTTPResult(int responseCode, byte[] responseBody) {}
    public static Optional<HTTPResult> makeKVSRequest(String requestMethod, String urlString, byte[] requestBody) {
        try {
            HttpURLConnection workerConnection = (HttpURLConnection) (new URL(urlString)).openConnection();
            workerConnection.setRequestMethod("PUT");
            workerConnection.setRequestProperty("User-Agent", "cis5550-StaticKVSClient");
            workerConnection.setConnectTimeout(1000);
            workerConnection.setReadTimeout(3000);
            
            if (requestBody != null) {
                workerConnection.setDoOutput(true);
            }
            
            workerConnection.connect();
            
            if (requestBody != null) {
                workerConnection.getOutputStream().write(requestBody);
            }
            
            int responseCode = workerConnection.getResponseCode();
            byte[] responseBody = null;
            if (responseCode >= 200 && responseCode < 300) {
                InputStream in = workerConnection.getInputStream();
                if (in != null) {
                    responseBody = in.readAllBytes();
                }
            } else {
                InputStream in = workerConnection.getErrorStream();
                if (in != null) {
                    responseBody = in.readAllBytes();
                }
            }
            
            return Optional.of(new HTTPResult(responseCode, responseBody));
        } catch (MalformedURLException e) {
            logger.error("MalformedURLException " + urlString);
            e.printStackTrace();
            return Optional.empty();
        } catch (ProtocolException e) {
            logger.error("Protocol exception. The requestMethod was " + requestMethod);
            e.printStackTrace();
            return Optional.empty();
        } catch (SocketTimeoutException e) {
            logger.error("Timed out while trying to talk to the kvs " + urlString);
            e.printStackTrace();
            return Optional.empty();
        } catch (IOException e) {
            logger.error("Encountered an exception while trying to talk to the kvs " + urlString);
            e.printStackTrace();
            return Optional.empty();
        }
    }
    
    public static boolean lockRow(StaticKVSConfig config, String tableName, String rowName) {
        try {
            String urlString = String.format("http://%s/lock/%s/%s", workerAddressForKey(config, rowName), tableName, URLEncoder.encode(rowName, "UTF-8"));
             
             Optional<HTTPResult> result = makeKVSRequest("PUT", urlString, null);
             if (result.isEmpty()) {
                 logger.error("Failed to lockRow for " + tableName + " " + rowName);
                 return false;
             } else {
                 HTTPResult httpSuccess = result.get();
                 return httpSuccess.responseCode == 200;
             }
        
        } catch (UnsupportedEncodingException e) {
            logger.error("UnsupportedEncodingException in lockRow");
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean unlockRow(StaticKVSConfig config, String tableName, String rowName) {
        try {
            String urlString = String.format("http://%s/unlock/%s/%s", workerAddressForKey(config, rowName), tableName, URLEncoder.encode(rowName, "UTF-8"));
            
            Optional<HTTPResult> result = makeKVSRequest("PUT", urlString, null);
            if (result.isEmpty()) {
                logger.error("Failed to lockRow for " + tableName + " " + rowName);
                return false;
            } else {
                HTTPResult httpSuccess = result.get();
                if (httpSuccess.responseCode != 200) {
                    logger.error("Failed to unlock the row. ResponseCode: " + httpSuccess.responseCode);
                    return false;
                } else {
                    return true;
                }
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("UnsupportedEncodingException in unlockRow");
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean putRow(StaticKVSConfig config, String tableName, Row row) {
        String workerAddress = workerAddressForKey(config, row.key());
        String urlString = String.format("http://%s/data/%s", workerAddress, tableName);
        
        Optional<HTTPResult> result = makeKVSRequest("PUT", urlString, row.toByteArray());
        if (result.isEmpty()) {
            logger.error(String.format("Failed to putRow %s into %s on %s", row.key(), tableName, workerAddress));
            return false;
        } else {
            HTTPResult httpResult = result.get();
            if (httpResult.responseBody != null && (new String(httpResult.responseBody).equals("OK"))) {
                return true;
            } else {
                logger.error(String.format("Bad response body for putRow %s into %s on %s", row.key(), tableName, workerAddress));
                return false;
            }
        }
    }
    
    public static Row getRow(StaticKVSConfig config, String tableName, String row) {
        String workerAddress = workerAddressForKey(config, row);
        String urlString;
        try {
            urlString = String.format("http://%s/data/%s/%s", workerAddress, tableName, URLEncoder.encode(row, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            logger.error("UnsupportedEncodingException in getRow");
            e.printStackTrace();
            return null;
        }
        
        Optional<HTTPResult> result = makeKVSRequest("GET", urlString, null);
        if (result.isEmpty()) {
            logger.error(String.format("Failed to getRow %s from %s on %s", row, tableName, workerAddress));
            return null;
        } else {
            HTTPResult httpResult = result.get();
           if (httpResult.responseCode != 200) {
               return null;
           } else {
               try {
                   return Row.readFrom(new ByteArrayInputStream(httpResult.responseBody));
               } catch (Exception e) {
                   logger.error("Decoding error while reading Row from getRow() URL");
                   return null;
               }
           }
        }
    }
    
    public static boolean existsRow(StaticKVSConfig config, String tableName, String row) {
        String workerAddress = workerAddressForKey(config, row);
        String urlString;
        try {
            urlString = String.format("http://%s/data/%s/%s", workerAddress, tableName, URLEncoder.encode(row, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            logger.error("UnsupportedEncodingException in existsRow");
            e.printStackTrace();
            return false;
        }
        
        Optional<HTTPResult> result = makeKVSRequest("GET", urlString, null);
        if (result.isEmpty()) {
            logger.error(String.format("Failed on existsRow %s from %s on %s", row, tableName, workerAddress));
            return false;
        } else {
            HTTPResult httpResult = result.get();
           return httpResult.responseCode == 200;
        }
    }
}
