package com.ai.reviewer.backend.infra.adapter.bitbucket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Bitbucket REST API client.
 * 
 * Provides methods to interact with Bitbucket's REST API v2.0.
 * Uses app passwords for authentication.
 */
@Component
public class BitbucketApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(BitbucketApiClient.class);
    
    private final BitbucketConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public BitbucketApiClient(BitbucketConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        
        if (config.isConfigured()) {
            logger.info("Bitbucket API client initialized: {}", config);
        }
    }
    
    /**
     * Get repository information.
     */
    public JsonNode getRepository(String owner, String repoName) {
        String url = String.format("%s/repositories/%s/%s", config.getApiBase(), owner, repoName);
        return get(url);
    }
    
    /**
     * Get pull request information.
     */
    public JsonNode getPullRequest(String owner, String repoName, String pullNumber) {
        String url = String.format("%s/repositories/%s/%s/pullrequests/%s", 
                config.getApiBase(), owner, repoName, pullNumber);
        return get(url);
    }
    
    /**
     * Get pull request diff.
     */
    public String getPullRequestDiff(String owner, String repoName, String pullNumber) {
        String url = String.format("%s/repositories/%s/%s/pullrequests/%s/diff", 
                config.getApiBase(), owner, repoName, pullNumber);
        return getRaw(url);
    }
    
    /**
     * Get file content from a specific commit.
     */
    public String getFileContent(String owner, String repoName, String sha, String path) {
        String url = String.format("%s/repositories/%s/%s/src/%s/%s", 
                config.getApiBase(), owner, repoName, sha, path);
        return getRaw(url);
    }
    
    /**
     * Create pull request comment.
     */
    public JsonNode createPullRequestComment(String owner, String repoName, String pullNumber, String content) {
        String url = String.format("%s/repositories/%s/%s/pullrequests/%s/comments", 
                config.getApiBase(), owner, repoName, pullNumber);
        
        Map<String, Object> commentData = Map.of(
                "content", Map.of(
                        "raw", content,
                        "markup", "markdown"
                )
        );
        
        return post(url, commentData);
    }
    
    /**
     * Create inline comment on pull request.
     */
    public JsonNode createInlineComment(String owner, String repoName, String pullNumber, 
                                      String filename, int line, String content) {
        String url = String.format("%s/repositories/%s/%s/pullrequests/%s/comments", 
                config.getApiBase(), owner, repoName, pullNumber);
        
        Map<String, Object> commentData = Map.of(
                "content", Map.of(
                        "raw", content,
                        "markup", "markdown"
                ),
                "inline", Map.of(
                        "path", filename,
                        "to", line
                )
        );
        
        return post(url, commentData);
    }
    
    /**
     * Update pull request status/build status.
     */
    public JsonNode updateBuildStatus(String owner, String repoName, String sha, 
                                    String state, String key, String name, String url, String description) {
        String statusUrl = String.format("%s/repositories/%s/%s/commit/%s/statuses/build", 
                config.getApiBase(), owner, repoName, sha);
        
        Map<String, Object> statusData = Map.of(
                "state", state,  // SUCCESSFUL, FAILED, INPROGRESS, STOPPED
                "key", key,
                "name", name,
                "url", url,
                "description", description
        );
        
        return post(statusUrl, statusData);
    }
    
    /**
     * List pull request comments.
     */
    public JsonNode getPullRequestComments(String owner, String repoName, String pullNumber) {
        String url = String.format("%s/repositories/%s/%s/pullrequests/%s/comments", 
                config.getApiBase(), owner, repoName, pullNumber);
        return get(url);
    }
    
    /**
     * Delete a comment.
     */
    public void deleteComment(String owner, String repoName, String pullNumber, String commentId) {
        String url = String.format("%s/repositories/%s/%s/pullrequests/%s/comments/%s", 
                config.getApiBase(), owner, repoName, pullNumber, commentId);
        delete(url);
    }
    
    // HTTP helper methods
    
    private JsonNode get(String url) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new BitbucketException("get", 
                String.format("HTTP %d: %s", e.getStatusCode().value(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            throw new BitbucketException("get", "Failed to execute GET request", e);
        }
    }
    
    private String getRaw(String url) {
        try {
            HttpHeaders headers = createHeaders();
            headers.setAccept(List.of(MediaType.TEXT_PLAIN, MediaType.ALL));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new BitbucketException("getRaw", 
                String.format("HTTP %d: %s", e.getStatusCode().value(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            throw new BitbucketException("getRaw", "Failed to execute GET request", e);
        }
    }
    
    private JsonNode post(String url, Object data) {
        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(data, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return objectMapper.readTree(response.getBody());
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new BitbucketException("post", 
                String.format("HTTP %d: %s", e.getStatusCode().value(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            throw new BitbucketException("post", "Failed to execute POST request", e);
        }
    }
    
    private void delete(String url) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new BitbucketException("delete", 
                String.format("HTTP %d: %s", e.getStatusCode().value(), e.getResponseBodyAsString()));
        } catch (Exception e) {
            throw new BitbucketException("delete", "Failed to execute DELETE request", e);
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        if (config.isConfigured()) {
            // Use Basic Auth with username and app password
            String auth = config.getUsername() + ":" + config.getAppPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        
        headers.set("User-Agent", "AI-Reviewer/1.0.0");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        
        return headers;
    }
}
