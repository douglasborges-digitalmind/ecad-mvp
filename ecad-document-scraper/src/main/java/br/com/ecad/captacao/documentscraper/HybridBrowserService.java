package br.com.ecad.captacao.documentscraper;

import java.util.List;

interface HybridBrowserService {
    String getMarkdownHttp(String url) throws Exception;

    String getMarkdownHttp(String url, String postBody) throws Exception;

    String getMarkdown(String url, String waitForSelector, String fillSelector, String fillText, String expandSelector,
        String iframeSelector, String submitSelector, String evaluateJs) throws Exception;

    byte[] download(String url) throws Exception;

    byte[] captureScreenshot(String url) throws Exception;

    List<String> discoverPncpDetailLinks(String url) throws Exception;

    PncpDetailResult fetchPncpDetail(String detailUrl) throws Exception;
}
