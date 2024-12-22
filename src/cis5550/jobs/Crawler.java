package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;
import cis5550.flame.FlameRDD.StringToIterable;
import cis5550.jobs.CrawlerHelper.SetupURLQueueResult;
import cis5550.kvs.KVSClient;
import cis5550.kvs.StaticKVSConfig;
import cis5550.kvs.StaticKVSWorkerEntry;
import cis5550.tools.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Crawler {
    private static final Logger logger = Logger.getLogger(Crawler.class);

    public static void run(FlameContext flameContext, String[] args) {
        if (args.length < 1 || args.length > 2) {
            flameContext.output("Incorrect arguments. The first argument should be a string of semicolon separated urls like url1;url2;url3");
            return;
        }

        String[] seedURLs = args[0].split(";");
        FlameRDD urlQueue;

        {
            SetupURLQueueResult result = CrawlerHelper.setupURLQueue(flameContext, seedURLs);
            if (result instanceof SetupURLQueueResult.Ok ok) {
                urlQueue = ok.flameRDD();
            } else if (result instanceof SetupURLQueueResult.InvalidURLSyntax e) {
                logger.error("Encountered a URISyntaxException when trying to normalized the seed url");
                e.e().printStackTrace();
                flameContext.output("Bad seed url syntax");
                return;
            } else if (result instanceof SetupURLQueueResult.InvalidURL invalidURL) {
                logger.error("Encountered a problem with the seed url: " + invalidURL.problem());
                flameContext.output("Bad seed url: " + invalidURL.problem());
                return;
            } else if (result instanceof SetupURLQueueResult.OtherError e) {
                logger.error("Encountered an exception when trying to setup the url queue");
                e.e().printStackTrace();
                flameContext.output("Encountered an exception when trying to setup the url queue");
                return;
            } else {
                throw new IllegalStateException();
            }
        }

        List<Pattern> blacklistedURLPatterns;
        if (args.length > 1) {
            String blacklistTable = args[1];
            blacklistedURLPatterns = CrawlerHelper.loadBlacklist(flameContext, blacklistTable);
        } else {
            blacklistedURLPatterns = List.of();
        }
        
        try {
            // Pass a string instead of flameContext to avoid a serialization error.
            KVSClient kvs = flameContext.getKVS();
            String coordinatorArg = kvs.getCoordinator();
            List<StaticKVSWorkerEntry> workerEntries = new ArrayList<>();
            for (int i = 0; i < kvs.numWorkers(); i++) {
                workerEntries.add(new StaticKVSWorkerEntry(kvs.getWorkerAddress(i), kvs.getWorkerID(i)));
            }
            StaticKVSConfig config = new StaticKVSConfig(coordinatorArg, workerEntries);
            StringToIterable urlProcessor = CrawlerHelper.generateURLProcessor(config, blacklistedURLPatterns);
            
            while (urlQueue.count() != 0) {
                try {
                    FlameRDD newURLQueue = urlQueue.flatMap(urlProcessor);
                    urlQueue.destroy();
                    urlQueue = newURLQueue;
                } catch (Exception e) {
                    logger.error("A operation within the crawler loop has failed");
                    e.printStackTrace();
                    logger.error("Main loop continuing");
                }
            }
        } catch (Exception e) {
            logger.error("Encountered an exception in the urlQueue.count(). Main loop terminated");
            e.printStackTrace();
            flameContext.output("Main loop terminated since urlQueue.count() failed");
            return;
        }

        flameContext.output("Crawl finished");
    }
}