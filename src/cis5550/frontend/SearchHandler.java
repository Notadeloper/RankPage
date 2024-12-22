// ARCHIVED
/*
 * package cis5550.frontend;
 * 
 * import java.nio.charset.StandardCharsets;
 * import java.util.ArrayList;
 * import java.util.Arrays;
 * import java.util.Collections;
 * import java.util.HashSet;
 * import java.util.Iterator;
 * import java.util.List;
 * import java.util.Map;
 * import java.util.PriorityQueue;
 * import java.util.Set;
 * import java.util.LinkedHashMap;
 * 
 * import cis5550.external.PorterStemmer;
 * import cis5550.kvs.KVSClient;
 * import cis5550.kvs.Tables;
 * import cis5550.kvs.Tables.GetRowResult;
 * import cis5550.kvs.Row;
 * import cis5550.tools.Logger;
 * import cis5550.tools.Hasher;
 * import cis5550.webserver.*;
 * 
 * import static cis5550.webserver.Server.*;
 * 
 * public class SearchHandler {
 * private static final Logger logger = Logger.getLogger(Server.class);
 * private static KVSClient kvsClient;
 * private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
 * "a", "about", "actually", "almost", "also", "although", "always", "am", "an",
 * "and",
 * "any", "are", "as", "at", "be", "became", "become", "but", "by", "can",
 * "could", "did",
 * "do", "does", "each", "either", "else", "for", "from", "had", "has", "have",
 * "hence",
 * "how", "i", "if", "in", "is", "it", "its", "just", "may", "maybe", "me",
 * "might", "mine",
 * "must", "my", "neither", "nor", "not", "of", "oh", "ok", "when", "where",
 * "whereas",
 * "wherever", "whenever", "whether", "which", "while", "who", "whom",
 * "whoever", "whose",
 * "why", "will", "with", "within", "without", "would", "yes", "yet", "you",
 * "your"));
 * 
 * public static void main(String[] args) {
 * if (args.length != 2) {
 * System.out.println(
 * "Usage: java -cp bin cis5550.frontend.SearchHandler <port> <kvsCoordinatorIP:port>"
 * );
 * System.exit(1);
 * }
 * 
 * int port = Integer.parseInt(args[0]);
 * String kvsCoordinator = args[1];
 * kvsClient = new KVSClient(kvsCoordinator);
 * 
 * logger.info("Frontend webserver starting on port " + port);
 * 
 * port(port);
 * 
 * // Redirect to html file if at root
 * get("/", (request, response) -> {
 * response.redirect("/search.html", 303);
 * return null;
 * });
 * 
 * Server.get("/search", (req, res) -> {
 * String query = req.queryParams("q");
 * logger.info("query was" + query);
 * 
 * // Implement your actual search logic here
 * List<String> results = retrieveSearchResults(query, kvsClient);
 * 
 * // Build HTML content
 * StringBuilder html = new StringBuilder();
 * html.append("<h2>Search Results for '").append(query).append("'</h2>");
 * 
 * if (results.isEmpty()) {
 * html.append("<p>No results found.</p>");
 * } else {
 * html.append("<ul>");
 * for (String result : results) {
 * html.append("<li>").append(result).append("</li>");
 * }
 * html.append("</ul>");
 * }
 * 
 * res.type("text/html");
 * res.status(200, "OK");
 * return html.toString();
 * });
 * 
 * Server.setFrontendFilesLocation();
 * }
 * 
 * // f it lets just do it sequentially first tune it later
 * 
 * public static List<String> retrieveSearchResults(String query, KVSClient kvs)
 * {
 * try {
 * List<String> results = new ArrayList<>();
 * String[] queries = query.trim().toLowerCase().split("\\s+");
 * 
 * List<String> filteredQueries = new ArrayList<>();
 * PorterStemmer stemmer = new PorterStemmer();
 * 
 * for (String word : queries) {
 * stemmer.add(word.toCharArray(), word.length());
 * stemmer.stem();
 * String stemmedWord = new String(
 * Arrays.copyOf(stemmer.getResultBuffer(), stemmer.getResultLength()));
 * 
 * // If it doesnt contain original word, add stemmed word (how to deal with
 * // queries such as "a" then?)
 * if (!STOP_WORDS.contains(word)) {
 * filteredQueries.add(stemmedWord);
 * }
 * }
 * 
 * Map<String, Double> orderedIdfs = new LinkedHashMap<>();
 * double idfMagnitudeSq = 0.0;
 * 
 * Set<String> potentialUrls = new HashSet<>();
 * for (String queryWord : filteredQueries) {
 * byte[] idf = kvs.get("pt-index", queryWord, "IDF");
 * if (idf != null) {
 * double idfValue = Double.parseDouble(new String(idf,
 * StandardCharsets.UTF_8));
 * idfMagnitudeSq += idfValue * idfValue;
 * orderedIdfs.put(queryWord, idfValue);
 * } else {
 * // This value doesnt really matter? cos tf is 0 anyways (for now)
 * orderedIdfs.put(queryWord, 0.0);
 * // Don't need to also check for urls for the word as its null
 * continue;
 * }
 * 
 * byte[] associatedUrls = kvs.get("pt-index", queryWord, "accumulatedValue");
 * if (associatedUrls != null) {
 * String[] urlsWithCount = new String(associatedUrls,
 * StandardCharsets.UTF_8).split(",");
 * for (String urlWithCount : urlsWithCount) {
 * try {
 * int lastColonIndex = urlWithCount.lastIndexOf(':');
 * urlWithCount.substring(0, lastColonIndex);
 * potentialUrls.add(urlWithCount.substring(0, lastColonIndex));
 * } catch (Exception e) {
 * 
 * }
 * }
 * }
 * }
 * 
 * double idfMagnitude = Math.sqrt(idfMagnitudeSq);
 * 
 * // Hardcoded for now, can just be query parameter from search or something
 * maybe
 * int topN = 10;
 * 
 * PriorityQueue<UrlSimilarity> topUrls = new PriorityQueue<>(topN);
 * 
 * // THIS IS THE BOTTLENECK
 * // For all potential documents
 * for (String potentialUrl : potentialUrls) {
 * double crossProduct = 0.0;
 * double tfMagnitudeSq = 0.0;
 * String hashedUrl = Hasher.hash(potentialUrl);
 * Double pageRank = 0.0;
 * try {
 * pageRank = Double.parseDouble(
 * new String(kvs.get("pt-pageranks", hashedUrl, "rank"),
 * StandardCharsets.UTF_8));
 * } catch (Exception e) {
 * logger.info("Error parsing pageRank" + e);
 * }
 * 
 * for (Map.Entry<String, Double> entry : orderedIdfs.entrySet()) {
 * String queryWord = entry.getKey();
 * byte[] tfBytes = kvs.get("pt-tf", queryWord, potentialUrl);
 * if (tfBytes != null) {
 * Double documentTf = Double.parseDouble(new String(tfBytes,
 * StandardCharsets.UTF_8));
 * Double queryIdf = entry.getValue();
 * crossProduct += documentTf * queryIdf;
 * tfMagnitudeSq += documentTf * documentTf;
 * }
 * }
 * 
 * double tfMagnitude = Math.sqrt(tfMagnitudeSq);
 * if (tfMagnitude != 0.0 && idfMagnitude != 0.0) {
 * double similarity = crossProduct / (tfMagnitude * idfMagnitude);
 * double ranking = similarity * pageRank;
 * 
 * UrlSimilarity urlSim = new UrlSimilarity(potentialUrl, ranking);
 * 
 * if (topUrls.size() < topN) {
 * topUrls.offer(urlSim);
 * } else if (urlSim.getSimilarity() > topUrls.peek().getSimilarity()) {
 * topUrls.poll();
 * topUrls.offer(urlSim);
 * }
 * }
 * 
 * }
 * List<UrlSimilarity> sortedTopUrls = new ArrayList<>(topUrls);
 * Collections.sort(sortedTopUrls);
 * 
 * for (UrlSimilarity urlSim : sortedTopUrls) {
 * results.add(urlSim.getUrl());
 * }
 * 
 * logger.info("TIME FOR PART 2");
 * 
 * return results;
 * } catch (Exception e) {
 * logger.info("Error while retrieving search results" + e);
 * return new ArrayList<String>();
 * }
 * }
 * 
 * }
 */
