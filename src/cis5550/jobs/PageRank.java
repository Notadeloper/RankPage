package cis5550.jobs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.webserver.Server;
import cis5550.kvs.KVSClient;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;

public class PageRank {
    private static final Logger logger = Logger.getLogger(Server.class);
    private static final Pattern ASCIIPattern = Pattern.compile("^[\\x20-\\x7E]+$");

    public static void run(FlameContext flameContext, String[] args) {
        double pageRankThreshold = 0.0001;
        if (args.length >= 1) {
            pageRankThreshold = Double.parseDouble(args[0]);
        }
        double percentage = 100.0;
        if (args.length >= 2) {
            percentage = Double.parseDouble(args[1]);
        }

        FlamePairRDD originalRanks;
        try {
            originalRanks = flameContext
                    .fromTable("pt-crawl", r -> {
                        String url = r.get("url");
                        Matcher urlASCIIMatcher = ASCIIPattern.matcher(url);
                        if (url.isBlank() || !urlASCIIMatcher.matches() || url.contains(",")) {
                            logger.info(url + "is non ascii");
                            return null;
                        }
                        return url + "," + r.get("outbound");
                    })
                    .mapToPair(s -> {
                        if (s == null) {
                            return null;
                        }

                        String[] data = s.split(",", 2);

                        if (data.length < 2) {
                            return null;
                        }
                        String url = data[0];

                        String outboundUrls = data[1].trim();

                        if (outboundUrls.isEmpty()) {
                            // No outbound links
                            String L = "";
                            return new FlamePair(Hasher.hash(url), "1.0,1.0," + L);
                        }

                        String[] outboundUrlsList = outboundUrls.split(";");

                        StringBuilder sb = new StringBuilder();
                        for (String normalizedURL : outboundUrlsList) {
                            logger.info("we have norm url" + normalizedURL);
                            if (sb.isEmpty()) {
                                sb.append(Hasher.hash(normalizedURL));
                            } else {
                                sb.append(",");
                                sb.append(Hasher.hash(normalizedURL));
                            }
                        }
                        // "L is a comma-separated list of the hashes of the normalized links you found
                        // on the page with URL hash u"
                        String L = sb.toString();

                        return new FlamePair(Hasher.hash(url), "1.0,1.0," + L);
                    }).filter(pair -> pair != null);

            originalRanks.saveAsTable("initial");
        } catch (Exception e) {
            logger.error("Ran into an exception while building the initial ranks table for page rank.");
            e.printStackTrace();
            flameContext.output("Failed to build the initial ranks table for page rank.");
            return;
        }

        int iterations = 0;
        while (true) {
            try {
                FlamePairRDD transferTable = originalRanks
                        .flatMapToPair(pair -> {
                            String[] data = pair._2().split(",", 3);

                            if (data[2].isEmpty() || data[2].isBlank()) {
                                // The page doesn't have any out-bound links.
                                return List.of();
                            }

                            String[] urlHashes = data[2].split(",");
                            int n = urlHashes.length;
                            double rc = Double.parseDouble(data[0]);

                            List<FlamePair> transferPairs = new ArrayList<>();
                            for (String urlHash : urlHashes) {
                                double v = ((0.85 * rc) / n);
                                transferPairs.add(new FlamePair(urlHash, "" + v));
                            }

                            String thisURL = pair._1();
                            transferPairs.add(new FlamePair(thisURL, "0.0"));

                            return transferPairs;
                        })
                        .foldByKey("", (a, b) -> {
                            if (a.isEmpty()) {
                                return b;
                            } else if (b.isEmpty()) {
                                return a;
                            } else {
                                return "" + (Double.parseDouble(a) + Double.parseDouble(b));
                            }
                        });

                FlamePairRDD joined = originalRanks.join(transferTable)
                        .flatMapToPair(pair -> {
                            String hashedURL = pair._1();
                            String[] joinResult = pair._2().split(",");

                            // the original current rank
                            String newPreviousRank = joinResult[0];

                            // The new current rank is at the end of the join
                            String newCurrentRank = "" + (0.15 + Double.parseDouble(joinResult[joinResult.length - 1]));

                            StringBuilder sb = new StringBuilder();
                            for (int i = 2; i < joinResult.length - 1; i++) {
                                if (sb.isEmpty()) {
                                    sb.append(joinResult[i]);
                                } else {
                                    sb.append(",");
                                    sb.append(joinResult[i]);
                                }
                            }
                            String L = sb.toString();

                            return List.of(new FlamePair(hashedURL, newCurrentRank + "," + newPreviousRank + "," + L));
                        });

                originalRanks = joined;
                iterations += 1;

                FlameRDD differenceTable = originalRanks.flatMap(pair -> {
                    String[] data = pair._2().split(",");
                    double difference = Double.parseDouble(data[0]) - Double.parseDouble(data[1]);
                    difference = Math.abs(difference);
                    return List.of("" + difference);
                });
                int numPages = differenceTable.count();
                double threshold = pageRankThreshold;
                int numBelowThreshold = originalRanks
                        .flatMap(pair -> {
                            String[] data = pair._2().split(",");
                            double difference = Double.parseDouble(data[0]) - Double.parseDouble(data[1]);
                            difference = Math.abs(difference);
                            return List.of("" + difference);
                        })
                        .filter(s -> {
                            double difference = Double.parseDouble(s);
                            if (difference < threshold) {
                                return true;
                            }
                            return false;
                        }).count();
                logger.info(numPages + " " + numBelowThreshold);
                double convergence = (numBelowThreshold * 100.0) / numPages;
                logger.info("" + convergence);

                double maxChange = Double.parseDouble(originalRanks
                        .flatMap(pair -> {
                            String[] data = pair._2().split(",");
                            double difference = Double.parseDouble(data[0]) - Double.parseDouble(data[1]);
                            difference = Math.abs(difference);
                            return List.of("" + difference);
                        })
                        .fold("", (a, b) -> {
                            if (a.isBlank()) {
                                return b;
                            } else if (b.isBlank()) {
                                return a;
                            } else {
                                Double ad = Double.parseDouble(a);
                                Double bd = Double.parseDouble(b);
                                return ad > bd ? a : b;
                            }
                        }));

                logger.info("" + iterations + ": " + maxChange);

                if (convergence >= percentage) {
                    break;
                }

            } catch (Exception e) {
                logger.error("Ran into an exception when performing an iteration of page rank");
                e.printStackTrace();
                flameContext.output("Failed to finish page rank");
                return;
            }

        }

        String coordinatorArg = flameContext.getKVS().getCoordinator();
        try {
            originalRanks.flatMapToPair(pair -> {
                String urlHash = pair._1();
                String[] data = pair._2().split(",");

                // Create a kvs inside the lambda to avoid the serialization error.
                KVSClient kvs = new KVSClient(coordinatorArg);
                kvs.put("pt-invertedTfPageRank", urlHash, "&&rank&&", data[0]);
                return List.of();
            });
        } catch (Exception e) {
            logger.error(
                    "Ran into an exception when performing the conversion from the internal format to the required format");
            e.printStackTrace();
            flameContext.output("Failed to perform the conversion from the internal format to the required format");
            return;
        }

        flameContext.output("Finished pagerank in " + iterations + " iterations");
    }
}