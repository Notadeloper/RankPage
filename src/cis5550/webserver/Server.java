package cis5550.webserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.*;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.security.*;

import cis5550.tools.Logger;

public class Server {
    private static int port = 80;
    private static int defaultSecurePort = 443;
    private static String directory;
    private static final Logger logger = Logger.getLogger(Server.class);
    private static final int NUM_WORKERS = 100;
    private static Server serverInstance = null;
    private static boolean flag = false;
    private static Map<String, Session> sessionsMap = new ConcurrentHashMap<>();

    // host -> method -> route map
    private static Map<String, Map<String, Map<String, Route>>> hostRoutes = new ConcurrentHashMap<>();
    private static String currentHost = "";
    private static ServerSocket serverSocketTLS = null;

    private Server() {

    }

    public static synchronized Server getInstance() {
        if (serverInstance == null) {
            serverInstance = new Server();
        }
        return serverInstance;
    }

    public static void run() {
        singletonCheck();
        serverInstance.startServer();
    }

    private void startServer() {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_WORKERS);

        new Thread(() -> {
            try (ServerSocket httpServerSocket = new ServerSocket(port)) {
                logger.info("HTTP server started on port " + port);
                serverLoop(httpServerSocket, executor, false);
            } catch (IOException e) {
                logger.error("Error starting HTTP server: " + e.getMessage(), e);
            }
        }).start();

        if (serverSocketTLS != null) {
            new Thread(() -> {
                try {
                    logger.info("HTTPS server started on secure port");
                    serverLoop(serverSocketTLS, executor, true);
                } catch (Exception e) {
                    logger.error("Error starting HTTPS server: " + e.getMessage(), e);
                }
            }).start();
        }

        new Thread(() -> {
            try {
                while (true) {
                    TimeUnit.SECONDS.sleep(5);
                    Iterator<Map.Entry<String, Session>> iterator = sessionsMap.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Session> entry = iterator.next();
                        Session session = entry.getValue();

                        long currentTime = System.currentTimeMillis();
                        if ((currentTime - session.lastAccessedTime()) > session.getMaxActiveInterval() * 1000
                                || !session.isValid()) {
                            iterator.remove();
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error during session map iteration and deletion: " + e.getMessage(), e);
            }
        }).start();

    }

    public static void setFrontendFilesLocation() {
        // Determine the absolute path to the static files directory
        String userDir = System.getProperty("user.dir");
        String staticDirPath = userDir + File.separator + "src" + File.separator + "resources" + File.separator
                + "static";
        staticFiles.location(staticDirPath);
        logger.info("Static files directory set to: " + staticDirPath);
    }

    private void serverLoop(ServerSocket serverSocket, ExecutorService executor, boolean isHttps) {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Incoming connection from " + clientSocket.getRemoteSocketAddress());
                executor.submit(new ClientInputHandler(clientSocket, directory, hostRoutes, sessionsMap, isHttps));
            }
        } catch (IOException e) {
            logger.error("Error in server loop: " + e.getMessage(), e);
        }
    }

    public static void get(String path, Route route) {
        singletonCheck();
        checkAndStartServer();
        addRoute("GET", path, route);
    }

    public static void post(String path, Route route) {
        singletonCheck();
        checkAndStartServer();
        addRoute("POST", path, route);
    }

    public static void put(String path, Route route) {
        singletonCheck();
        checkAndStartServer();
        addRoute("PUT", path, route);
    }

    public static void port(int port) {
        singletonCheck();
        Server.port = port;
    }

    private static void singletonCheck() {
        if (serverInstance == null) {
            serverInstance = new Server();
        }
    }

    private static void checkAndStartServer() {
        if (!flag) {
            flag = true;
            new Thread(() -> Server.run()).start();
        }
    }

    private static void addRoute(String method, String path, Route route) {
        if (!hostRoutes.containsKey(currentHost)) {
            hostRoutes.put(currentHost, new HashMap<>());
        }
        hostRoutes.get(currentHost).computeIfAbsent(method, k -> new HashMap<>()).put(path, route);
    }

    public static void host(String host, String keyStoreFilen, String keyStorePwd) {
        try {
            currentHost = host;
            if (!hostRoutes.containsKey(host)) {
                hostRoutes.put(host, new HashMap<>());
            }
            // loadSSLContext(host, keyStoreFilen, keyStorePwd);
        } catch (Exception e) {
            logger.error("Error setting multiple hosts " + e.getMessage(), e);
        }
    }

    // private static void loadSSLContext(String host, String keyStoreFilen, String
    // keyStorePwd) {
    // try {
    // KeyStore keyStore = KeyStore.getInstance("JKS");
    // keyStore.load(new FileInputStream(keyStoreFilen), keyStorePwd.toCharArray());
    // KeyManagerFactory keyManagerFactory = KeyManagerFactory
    // .getInstance(KeyManagerFactory.getDefaultAlgorithm());
    // keyManagerFactory.init(keyStore, keyStorePwd.toCharArray());

    // } catch (Exception e) {
    // logger.error("Error creating SSL for multiple hosts " + e.getMessage(), e);
    // }
    // }

    public static void securePort(int securePortNo) {
        try {
            String pwd = "secret";
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream("keystore.jks"), pwd.toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, pwd.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            ServerSocketFactory factory = sslContext.getServerSocketFactory();
            serverSocketTLS = factory.createServerSocket(securePortNo);
        } catch (Exception e) {
            logger.error("Error setting TLS server: " + e.getMessage(), e);
        }
    }

    public static class staticFiles {
        public static void location(String location) {
            directory = location;
        }
    }
}

// javac -d bin $(find src -name "*.java")