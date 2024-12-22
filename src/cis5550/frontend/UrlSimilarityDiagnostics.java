package cis5550.frontend;

import java.util.Map;

public class UrlSimilarityDiagnostics implements Comparable<UrlSimilarityDiagnostics> {
    private String url;
    private double similarity;
    private Map<String, Double> tfIdfMap; // TF-IDF scores for each query term
    private double pageRank;

    public UrlSimilarityDiagnostics(String url, double similarity, Map<String, Double> tfIdfMap, double pageRank) {
        this.url = url;
        this.similarity = similarity;
        this.tfIdfMap = tfIdfMap;
        this.pageRank = pageRank;
    }

    public String getUrl() {
        return url;
    }

    public double getSimilarity() {
        return similarity;
    }

    public Map<String, Double> getTfIdfMap() {
        return tfIdfMap;
    }

    public double getPageRank() {
        return pageRank;
    }

    @Override
    public int compareTo(UrlSimilarityDiagnostics other) {
        return Double.compare(this.similarity, other.similarity);
    }
}