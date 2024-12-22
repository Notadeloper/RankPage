package cis5550.frontend;

import java.util.List;
import java.util.Map;

public class SearchResultData {
    private List<UrlSimilarityDiagnostics> topUrls;
    private Map<String, Double> queryIdfs;
    private Map<String, Map<String, Double>> documentTfIdfs;
    private Map<String, Double> documentSimilarities;

    public SearchResultData(List<UrlSimilarityDiagnostics> topUrls, Map<String, Double> queryIdfs,
            Map<String, Map<String, Double>> documentTfIdfs,
            Map<String, Double> documentSimilarities) {
        this.topUrls = topUrls;
        this.queryIdfs = queryIdfs;
        this.documentTfIdfs = documentTfIdfs;
        this.documentSimilarities = documentSimilarities;
    }

    public List<UrlSimilarityDiagnostics> getTopUrls() {
        return topUrls;
    }

    public Map<String, Double> getQueryIdfs() {
        return queryIdfs;
    }

    public Map<String, Map<String, Double>> getDocumentTfIdfs() {
        return documentTfIdfs;
    }

    public Map<String, Double> getDocumentSimilarities() {
        return documentSimilarities;
    }
}