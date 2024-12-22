package cis5550.jobs;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.Map;
import java.util.Base64;

import cis5550.external.PorterStemmer;
import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.tools.Logger;
import cis5550.tools.Hasher;
import cis5550.kvs.Row;

public class Indexer {
    private static final Logger logger = Logger.getLogger(Indexer.class);
    private static final Pattern ASCIIPattern = Pattern.compile("^[\\x20-\\x7E]+$");
    private static final Pattern AlnumPattern = Pattern.compile("^[A-Za-z0-9]+$");

    private static final Pattern htmlTagPattern = Pattern.compile("<[^>]*>");
    private static final Pattern punctuationPattern = Pattern.compile("[.,:;!?'\"()\\-]");
    // Percentage of docsize
    private static final double falloffParam = 20;
    private static final double bodyScaleParam = 0.4;
    private static final double titleScaleParam = 12;
    private static final double nonAlnumDeranker = 10;

    public static void run(FlameContext flameContext, String[] args) {
        String coordinatorArg = flameContext.getKVS().getCoordinator();

        try {
            FlameRDD intermediate = flameContext.fromTable("pt-crawl", r -> {
                String url = r.get("url");
                if (url.contains(",")) {
                    return null;
                }
                return url + "," + r.get("page");
            });
            int documentCount = intermediate.count();
            FlamePairRDD ptIndex = intermediate.mapToPair(s -> {
                String[] urlWordCountAndPage = s.split(",", 2);
                return new FlamePair(urlWordCountAndPage[0], urlWordCountAndPage[1]);
            }).flatMapToPair(pair -> {
                KVSClient kvs = new KVSClient(coordinatorArg);

                String url = pair._1();
                Matcher urlASCIIMatcher = ASCIIPattern.matcher(url);
                if (!urlASCIIMatcher.matches()) {
                    logger.info(url + "is non ascii");
                    return new HashSet<FlamePair>();
                }

                String page = pair._2();

                String removeHTML = htmlTagPattern.matcher(page).replaceAll(" ");

                // Note: o'clock is expected to split to "o" and "clock", not "oclock".
                String removePunctuation = punctuationPattern.matcher(removeHTML).replaceAll(" ");
                String toLowerCase = removePunctuation.toLowerCase();
                String[] words = toLowerCase.split("\\s+");
                String hashedUrl = Hasher.hash(url);
                if (words != null && words.length > 1) {
                    logger.info("words length is " + words.length + " for this url " + url + " word is " + words[0]
                            + "hashedurl is " + hashedUrl);
                } else {
                    logger.info(url + " has no words or words is null or words[0] is null?" + hashedUrl);
                    return new HashSet<FlamePair>();
                }
                Map<String, StringBuilder> wordPositionsMap = new HashMap<>();
                PorterStemmer stemmer = new PorterStemmer();
                int documentWordCount = words.length;
                for (int i = 0; i < words.length; i++) {
                    String word = words[i];

                    if (word.isBlank() || !isValid(word)) {
                        continue;
                    }

                    wordPositionsMap.computeIfAbsent(word, k -> new StringBuilder()).append(i).append(" ");
                    stemmer.add(word.toCharArray(), word.length());
                    stemmer.stem();
                    String stemmedWord = new String(
                            Arrays.copyOf(stemmer.getResultBuffer(), stemmer.getResultLength()));
                    if (!stemmedWord.equals(word)) {
                        wordPositionsMap.computeIfAbsent(stemmedWord, k -> new StringBuilder()).append(i).append(" ");
                    }
                }

                // For heavy tf weighting towards titles
                byte[] pageTitleBytes = kvs.get("pt-crawl", hashedUrl, "title");
                String encodedTitle = "";
                if (pageTitleBytes != null) {
                    encodedTitle = Base64.getEncoder().encodeToString(pageTitleBytes);
                }

                Set<FlamePair> wordUrlAndPositionPairs = new HashSet<>();
                for (Map.Entry<String, StringBuilder> e : wordPositionsMap.entrySet()) {
                    String word = e.getKey();
                    StringBuilder positions = e.getValue();

                    // Trim trailing space if present
                    int length = positions.length();
                    if (length > 0 && positions.charAt(length - 1) == ' ') {
                        positions.setLength(length - 1);
                    }

                    // url : total number of words in document : positions

                    String finalValue = url + ":" + documentWordCount + ":" + positions.toString() + ":" + encodedTitle;
                    wordUrlAndPositionPairs.add(new FlamePair(word, finalValue));
                }

                return wordUrlAndPositionPairs;
            }).foldByKey("", (a, b) -> {
                if (a.isEmpty()) {
                    return b;
                } else if (b.isEmpty()) {
                    return a;
                } else {
                    return a + "," + b;
                }
            });

            // Create idf with flatmaptopair
            // Create tf table with ??

            // TF AND IDF COMPUTATION

            FlamePairRDD ptIdf = ptIndex.mapToPair(p -> {
                String word = p._1();
                String urls = p._2();
                KVSClient kvs = new KVSClient(coordinatorArg);

                String[] urlList = urls.split(",");
                int wordFreq = urlList.length;

                double idf = Math.log((double) documentCount / wordFreq);

                // TF CALCULATION
                Row tfRow = new Row(word);
                for (String url : urlList) {
                    // Extract the title
                    int lastColonIndex = url.lastIndexOf(':');
                    String urlWordCountPositionsPart = url.substring(0, lastColonIndex);
                    String titleEncoded = url.substring(lastColonIndex + 1).trim();
                    String title = new String(Base64.getDecoder().decode(titleEncoded), StandardCharsets.UTF_8);

                    // Extract count of that word
                    int secondLastColonIndex = urlWordCountPositionsPart.lastIndexOf(':');
                    String urlWordCountPart = urlWordCountPositionsPart.substring(0, secondLastColonIndex);
                    String positionsPart = urlWordCountPositionsPart.substring(secondLastColonIndex + 1).trim();

                    // extract document count
                    int thirdLastColonIndex = urlWordCountPart.lastIndexOf(':');
                    String urlPart = urlWordCountPart.substring(0, thirdLastColonIndex);
                    int documentWordCount = Integer
                            .parseInt(urlWordCountPart.substring(thirdLastColonIndex + 1).trim());

                    // Calculate tf as a function of word positions

                    String[] positionsArr = positionsPart.split(" ");
                    double tf = 0;
                    for (String position : positionsArr) {
                        tf += bodyScaleParam * (1.0
                                / (1.0 + falloffParam * ((double) Integer.parseInt(position) / documentWordCount)));
                    }

                    Matcher alnumMatcher = AlnumPattern.matcher(word);
                    // Derank non alnum words (symbols, e.t.c)
                    if (!alnumMatcher.matches()) {
                        tf /= nonAlnumDeranker;
                    }

                    // Extra tf score for title
                    String[] titleParts = title.split(" ");
                    int titleLength = titleParts.length;
                    for (int i = 0; i < titleLength; i++) {
                        Matcher alnumTitleMatcher = AlnumPattern.matcher(title);
                        if (!alnumTitleMatcher.matches()) {
                            tf += bodyScaleParam * (1.0
                                    / (1.0 + falloffParam * ((double) i / titleLength)));
                        } else {
                            // Higher scaling for title
                            tf += titleScaleParam * (1.0
                                    / (1.0 + falloffParam * ((double) i / titleLength)));
                        }
                    }

                    // Column is word, row is hash
                    tfRow.put(Hasher.hash(urlPart), String.valueOf(tf));
                }
                kvs.putRow("pt-tf", tfRow);
                return new FlamePair(word, String.valueOf(idf));
            });

            FlameRDD ptInvertedTf = intermediate.invert("pt-tf");
            // Page rank also gets added to this table
            ptInvertedTf.saveAsTable("pt-invertedTfPageRank");

            ptIndex.saveAsTable("pt-index");
            ptIdf.saveAsTable("pt-idf");

            // ptInvertedTf.saveAsTable("pt-tf");

        } catch (Exception e) {
            logger.error("Ran into an exception while building the inverted index");
            e.printStackTrace();
            flameContext.output("Failed to build the inverted index");
        }
    }

    public static boolean isValid(String word) {
        if (word == null || word.length() > 50) {
            return false;
        }

        // Returns false for characters that have one symbols
        // That is not just the symbol itself (heuristic)
        Matcher alnumMatcher = AlnumPattern.matcher(word);
        return alnumMatcher.matches() || (word.length() == 1 && word.charAt(0) <= 127);
    }

}
