package cis5550.generic;

import cis5550.tools.Logger;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Objects;

public class Worker {
    private static final Logger logger = Logger.getLogger(Worker.class);

    protected static String workerID;
    protected static String coordinator;
    protected static int portNumber;

    public static void startPingThread() {
        Objects.requireNonNull(coordinator, "The coordinator field needs to be set");
        Objects.requireNonNull(portNumber, "The port field needs to be set");

        (new Thread(() -> pingThread(coordinator, workerID, portNumber))).start();
    }

    private static void pingThread(String coordinator, String workerID, int port) {
        String stringURL = String.format("http://%s/ping?id=%s&port=%s", coordinator, workerID, port);
        try {
            URL pingURL = new URL(stringURL);
            while (true) {
                HttpURLConnection pingConnection = (HttpURLConnection) pingURL.openConnection();
                // Originall 1000, 3000
                pingConnection.setConnectTimeout(5000);
                pingConnection.setReadTimeout(10000);
                pingConnection.connect();
                pingConnection.getContent();
                pingConnection.disconnect();

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (MalformedURLException e) {
            logger.error("Encountered a MalformedURLException. url: " + stringURL);
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            logger.error("Timed out while trying to connect to coordinator");
            e.printStackTrace();
        } catch (IOException e) {
            logger.error("Encountered an IOException while trying to ping the coordinator");
            e.printStackTrace();
        }

        logger.error("Ending worker thread: " + workerID);
    }
}