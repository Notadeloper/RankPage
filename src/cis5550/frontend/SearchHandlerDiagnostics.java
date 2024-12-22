package cis5550.frontend;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.LinkedHashMap;

import cis5550.external.PorterStemmer;
import cis5550.kvs.ColInsert;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Tables;
import cis5550.kvs.Tables.GetRowResult;
import cis5550.kvs.Row;
import cis5550.tools.Logger;
import cis5550.tools.Hasher;
import cis5550.webserver.*;

import static cis5550.webserver.Server.*;

public class SearchHandlerDiagnostics {
    private static final Logger logger = Logger.getLogger(Server.class);
    private static KVSClient kvsClient;
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "about", "actually", "almost", "also", "although", "always", "am", "an", "and",
            "any", "are", "as", "at", "be", "became", "become", "but", "by", "can", "could", "did",
            "do", "does", "each", "either", "else", "for", "from", "had", "has", "have", "hence",
            "how", "i", "if", "in", "is", "it", "its", "just", "may", "maybe", "me", "might", "mine",
            "must", "my", "neither", "nor", "not", "of", "oh", "ok", "when", "where", "whereas",
            "wherever", "whenever", "whether", "which", "while", "who", "whom", "whoever", "whose",
            "why", "will", "with", "within", "without", "would", "yes", "yet", "you", "your"));

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println(
                    "Usage: java -cp bin cis5550.frontend.SearchHandlerDiagnostics <port> <kvsCoordinatorIP:port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String kvsCoordinator = args[1];
        kvsClient = new KVSClient(kvsCoordinator);

        logger.info("Frontend webserver starting on port " + port);

        port(port);

        // Redirect to html file if at root
        get("/", (request, response) -> {
            response.redirect("/search.html", 303);
            return null;
        });

        Server.get("/search", (req, res) -> {
            String query = req.queryParams("q");
            logger.info("query was" + query);

            // Implement your actual search logic here
            SearchResultData searchData = retrieveSearchResults(query, kvsClient);

            StringBuilder html = new StringBuilder();
            html.append("<h2>Search Results for '").append(query).append("'</h2>");

            if (searchData == null || searchData.getTopUrls().isEmpty()) {
                html.append("<p>No results found.</p>");
                return html.toString();
            } else {
                html.append("<ul>");
                for (UrlSimilarityDiagnostics result : searchData.getTopUrls()) {
                    html.append("<li><a href=\"").append(result.getUrl()).append("\">")
                            .append(fetchTitle(result.getUrl(), kvsClient)).append(" | ") // comment this line to skip
                                                                                          // titles
                            .append(result.getUrl()).append("</a></li>");
                }
                html.append("</ul>");
            }

            // Add Diagnostic Data Section
            html.append("<h2>Diagnostic Data</h2>");

            // Display Query IDF Values
            html.append("<h3>Query IDF Values</h3>");
            html.append("<table border='1'>");
            html.append("<tr><th>Query Word</th><th>IDF Value</th></tr>");
            for (Map.Entry<String, Double> entry : searchData.getQueryIdfs().entrySet()) {
                html.append("<tr><td>").append(entry.getKey()).append("</td><td>")
                        .append(String.format("%.4f", entry.getValue())).append("</td></tr>");
            }
            html.append("</table>");

            // Display Document TF-IDF Values and Similarity Scores
            html.append("<h3>Document TF-IDF Values and Similarity Scores</h3>");
            html.append("<table border='1'>");
            html.append("<tr>");
            html.append("<th>URL</th>");
            for (String queryWord : searchData.getQueryIdfs().keySet()) {
                html.append("<th>").append(queryWord).append(" TF</th>");
            }
            html.append("<th>PageRank</th>");
            html.append("<th>Combined Score</th>");
            html.append("</tr>");

            for (UrlSimilarityDiagnostics result : searchData.getTopUrls()) {
                String url = result.getUrl();
                double pageRank = result.getPageRank();
                double combinedScore = result.getSimilarity();
                Map<String, Double> tfIdfMap = searchData.getDocumentTfIdfs().get(url);

                html.append("<tr>");
                html.append("<td><a href=\"").append(url).append("\">").append(url).append("</a></td>");
                for (String queryWord : searchData.getQueryIdfs().keySet()) {
                    double tf = tfIdfMap.getOrDefault(queryWord, 0.0);
                    html.append("<td>").append(String.format("%.4f", tf)).append("</td>");
                }
                html.append("<td>").append(String.format("%.8f", pageRank)).append("</td>");
                html.append("<td>").append(String.format("%.8f", combinedScore)).append("</td>");
                html.append("</tr>");
            }

            html.append("</table>");

            res.type("text/html");
            res.status(200, "OK");
            return html.toString();
        });

        Server.setFrontendFilesLocation();
    }

    // f it lets just do it sequentially first tune it later
    public static SearchResultData retrieveSearchResults(String query, KVSClient kvs) {
        Map<String, Double> queryIdfs = new LinkedHashMap<>();
        Map<String, Map<String, Double>> documentTfIdfs = new LinkedHashMap<>();
        Map<String, Double> documentSimilarities = new LinkedHashMap<>();

        try {
            long preprocessingStart = System.nanoTime();

            // Preprocess: tokenize, lowercase, remove stop words, stem
            String[] queries = query.trim().toLowerCase().split("\\s+");
            List<String> filteredQueries = new ArrayList<>();
            PorterStemmer stemmer = new PorterStemmer();

            for (String word : queries) {
                stemmer.add(word.toCharArray(), word.length());
                stemmer.stem();
                String stemmedWord = new String(
                        Arrays.copyOf(stemmer.getResultBuffer(), stemmer.getResultLength()));

                if (!STOP_WORDS.contains(word) || queries.length == 1) {
                    filteredQueries.add(stemmedWord);
                }
            }

            logger.info("Filtered query into " + filteredQueries.size() + " words: " + filteredQueries);

            long preprocessingEnd = System.nanoTime();
            logDuration("Query Preprocessing", preprocessingStart, preprocessingEnd);

            long idfFetchStart = System.nanoTime();

            // Populate queryIdfs and compute IDF magnitude
            for (String queryWord : filteredQueries) {
                kvs.getRow("pt-idf", queryWord);
                byte[] idfBytes = kvs.get("pt-idf", queryWord, "value");

                if (idfBytes != null) {
                    double idfValue = Double.parseDouble(new String(idfBytes, StandardCharsets.UTF_8));
                    // idfMagnitudeSq += idfValue * idfValue;
                    queryIdfs.put(queryWord, idfValue);
                    logger.info("QueryWord('" + queryWord + "') with IDF: " + idfValue);
                } else {
                    queryIdfs.put(queryWord, 0.0); // Handle missing IDF
                    logger.info("QueryWord('" + queryWord + "') has no IDF, set to 0.0");
                }
            }

            long idfFetchEnd = System.nanoTime();
            logDuration("Fetching Query IDFs", idfFetchStart, idfFetchEnd);

            long urlRetrievalStart = System.nanoTime();

            // For each query word, retrieve associated URLs
            Set<String> potentialUrls = new HashSet<>();

            for (String queryWord : filteredQueries) {
                byte[] urlsTfBytes = kvs.get("pt-index", queryWord, "accumulatedValue");
                if (urlsTfBytes != null) {
                    String urlsTfStr = new String(urlsTfBytes, StandardCharsets.UTF_8);
                    String[] urlTfPairs = urlsTfStr.split(",");
                    for (String urlWithCount : urlTfPairs) {
                        try {
                            // Extracting the potential url
                            String[] parts = urlWithCount.split(":");
                            int length = parts.length;

                            StringBuilder urlBuilder = new StringBuilder(parts[0]);
                            for (int i = 1; i < length - 3; i++) {
                                urlBuilder.append(":").append(parts[i]);
                            }
                            String potentialUrl = urlBuilder.toString();

                            potentialUrls.add(potentialUrl);
                        } catch (Exception e) {

                        }
                    }
                }
            }

            logger.info("we have " + potentialUrls.size() + "many potential urls");

            long urlRetrievalEnd = System.nanoTime();
            logDuration("Retrieving Potential URLs", urlRetrievalStart, urlRetrievalEnd);

            long parallelProcessingStart = System.nanoTime();

            int numThreads = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            // divide the potentialUrls into batches
            List<List<String>> batches = createBatches(new ArrayList<>(potentialUrls), numThreads);

            // create a list to hold future objects
            List<Future<PriorityQueue<UrlSimilarityDiagnostics>>> futures = new ArrayList<>();

            // submit tasks for each batch
            for (List<String> batch : batches) {
                Callable<PriorityQueue<UrlSimilarityDiagnostics>> task = () -> {
                    PriorityQueue<UrlSimilarityDiagnostics> localPq = new PriorityQueue<>(10);
                    for (String potentialUrl : batch) {
                        double similarityCP = 0.0;
                        Map<String, Double> tfIdfMap = new LinkedHashMap<>();

                        // Compute cross product and TF magnitude

                        String hashedUrl = Hasher.hash(potentialUrl);
                        Row tfRow = kvs.getRow("pt-invertedTfPageRank", hashedUrl);
                        if (tfRow != null) {
                            for (Map.Entry<String, Double> idfEntry : queryIdfs.entrySet()) {
                                String queryWord = idfEntry.getKey();
                                double idfValue = idfEntry.getValue();

                                String tf = tfRow.get(queryWord);
                                if (tf != null) {
                                    double tfValue = Double.parseDouble(tf);
                                    similarityCP += tfValue * idfValue;
                                    tfIdfMap.put(queryWord, tfValue); // Store TF for each query word
                                } else {
                                    tfIdfMap.put(queryWord, 0.0); // Handle missing TF
                                }
                            }
                        } else {
                            logger.info("could not find url: " + potentialUrl);
                            continue;
                        }

                        double pageRank = 0.0;
                        try {
                            String pr = tfRow.get("&&rank&&");
                            if (pr != null) {
                                pageRank = Double.parseDouble(pr);
                            }
                        } catch (Exception e) {
                            logger.info("Error parsing pageRank for URL " + potentialUrl + ": " + e);
                        }

                        // combine similarity with PageRank
                        double combinedScore = similarityCP * pageRank;

                        // create UrlSimilarityDiagnostics object
                        UrlSimilarityDiagnostics urlSim = new UrlSimilarityDiagnostics(potentialUrl, combinedScore,
                                tfIdfMap, pageRank);

                        // maintain local 10 in the local pq
                        if (localPq.size() < 10) {
                            localPq.offer(urlSim);
                        } else if (urlSim.getSimilarity() > localPq.peek().getSimilarity()) {
                            localPq.poll();
                            localPq.offer(urlSim);
                        }
                    }
                    return localPq;
                };
                futures.add(executor.submit(task));
            }

            long parallelProcessingEnd = System.nanoTime();
            logDuration("Parallel Processing", parallelProcessingStart, parallelProcessingEnd);

            long mergingStart = System.nanoTime();

            List<UrlSimilarityDiagnostics> allTopUrls = new ArrayList<>();

            // Collect all top URLs from each future
            for (Future<PriorityQueue<UrlSimilarityDiagnostics>> future : futures) {
                try {
                    PriorityQueue<UrlSimilarityDiagnostics> localPq = future.get();
                    allTopUrls.addAll(localPq);
                } catch (Exception e) {
                    logger.info("Error processing batch: " + e.getMessage());
                }
            }

            allTopUrls.sort(Collections.reverseOrder());

            // Select the top N results
            List<UrlSimilarityDiagnostics> topNResults = allTopUrls.stream()
                    .limit(10)
                    .collect(Collectors.toList());

            long mergingEnd = System.nanoTime();
            logDuration("Pqueue merging", mergingStart, mergingEnd);

            long aggregationStart = System.nanoTime();

            // Shutdown ExecutorService
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        logger.info("Executor did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            for (UrlSimilarityDiagnostics result : topNResults) {
                logger.info("Potential URL: " + result.getUrl());
                documentSimilarities.put(result.getUrl(), result.getSimilarity());
                documentTfIdfs.put(result.getUrl(), result.getTfIdfMap());
            }

            long aggregationEnd = System.nanoTime();
            logDuration("Final Aggregation", aggregationStart, aggregationEnd);

            return new SearchResultData(topNResults, queryIdfs, documentTfIdfs, documentSimilarities);

        } catch (Exception e) {
            logger.info("Error while retrieving search results" + e);
            return null;
        }
    }

    // Helper method to create batches
    private static List<List<String>> createBatches(List<String> urls, int numBatches) {
        List<List<String>> batches = new ArrayList<>();
        int totalUrls = urls.size();
        int batchSize = (int) Math.ceil((double) totalUrls / numBatches);

        for (int i = 0; i < totalUrls; i += batchSize) {
            int end = Math.min(i + batchSize, totalUrls);
            batches.add(urls.subList(i, end));
        }

        return batches;
    }

    public static String fetchTitle(String urlString, KVSClient kvs) {
        try {
            String hashedUrl = Hasher.hash(urlString);
            byte[] pageBytes = kvs.get("pt-crawl", hashedUrl, "page");
            if (pageBytes == null) {
                System.err.println("No page content found for URL: " + urlString);
                return getHostFromUrl(urlString);
            }
            String htmlContent = new String(pageBytes, StandardCharsets.UTF_8);

            String title = "";

            Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(htmlContent);

            if (matcher.find()) {
                title = matcher.group(1).trim();
            }

            if (title.isEmpty()) {
                pattern = Pattern.compile("<meta name=\"DC.title\" content=\"(.*?)\"\\s*/?>", Pattern.CASE_INSENSITIVE);
                matcher = pattern.matcher(htmlContent);
                if (matcher.find()) {
                    title = matcher.group(1).trim();
                }
            }

            if (title.isEmpty()) {
                pattern = Pattern.compile("<meta property=\"og:title\" content=\"(.*?)\"\\s*/?>",
                        Pattern.CASE_INSENSITIVE);
                matcher = pattern.matcher(htmlContent);
                if (matcher.find()) {
                    title = matcher.group(1).trim();
                }
            }

            // Fallback to the first h1 or h2 element
            if (title.isEmpty()) {
                pattern = Pattern.compile("<h1>(.*?)</h1>|<h2>(.*?)</h2>", Pattern.CASE_INSENSITIVE);
                matcher = pattern.matcher(htmlContent);
                if (matcher.find()) {
                    title = matcher.group(1).trim();
                }

            }

            if (title.isEmpty()) {
                return getHostFromUrl(urlString);
            }

            return title;
        } catch (Exception e) {
            System.err.println("Error fetching title from " + urlString + ": " + e.getMessage());
            return "Error fetching title";
        }
    }

    private static String getHostFromUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            return uri.getHost() != null ? uri.getHost() : urlString;
        } catch (URISyntaxException e) {
            System.err.println("Invalid URL: " + urlString);
            return urlString;
        }
    }

    private static void logDuration(String section, long startTime, long endTime) {
        long durationInMillis = (endTime - startTime) / 1_000_000;
        logger.info(section + " took " + durationInMillis + " ms");
    }
}
