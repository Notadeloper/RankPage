package cis5550.frontend;

public class UrlSimilarity implements Comparable<UrlSimilarity> {
    private String url;
    private double similarity;

    public UrlSimilarity(String url, double similarity) {
        this.url = url;
        this.similarity = similarity;
    }

    public String getUrl() {
        return url;
    }

    public double getSimilarity() {
        return similarity;
    }

    @Override
    public int compareTo(UrlSimilarity other) {
        // For descending order (higher similarity first)
        return Double.compare(other.similarity, this.similarity);
    }
}