package cis5550.kvs;

import cis5550.tools.Logger;
import cis5550.webserver.Server;

public class Coordinator extends cis5550.generic.Coordinator {
    private static final Logger logger = Logger.getLogger(Coordinator.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.error("Incorrect number of command line arguments");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);
        logger.info("Starting KVS Coordinator on port: " + portNumber);
        Server.port(portNumber);
        Coordinator.registerRoutes();
        String pageTemplate = "<!DOCTYPE html><html><head><title>KVS Coordinator</title></head><body><h1>%s</h1></body></html>";
        Server.get("/", (req, res) -> {
            return String.format(pageTemplate, Coordinator.workerTable());
        });

    }

}
