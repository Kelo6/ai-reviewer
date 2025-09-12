package com.ai.reviewer.backend.domain.adapter.scm;

import com.ai.reviewer.shared.model.RepoRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Router for SCM adapters based on repository provider.
 * 
 * <p>This component manages multiple SCM adapter implementations and
 * routes requests to the appropriate adapter based on the repository's
 * provider (e.g., "github", "gitlab", "bitbucket").
 * 
 * <p>The router automatically discovers all available adapters through
 * Spring's dependency injection and builds a routing table based on
 * each adapter's provider identifier.
 */
@Component
public class ScmAdapterRouter {

    private static final Logger logger = LoggerFactory.getLogger(ScmAdapterRouter.class);

    private final Map<String, ScmAdapter> adaptersByProvider;

    /**
     * Constructor that initializes the router with all available adapters.
     *
     * @param adapters list of all SCM adapter implementations
     */
    @Autowired
    public ScmAdapterRouter(List<ScmAdapter> adapters) {
        this.adaptersByProvider = new ConcurrentHashMap<>();
        
        if (adapters != null) {
            // Build routing table
            Map<String, ScmAdapter> adapterMap = adapters.stream()
                    .collect(Collectors.toMap(
                        ScmAdapter::getProvider,
                        Function.identity(),
                        (existing, replacement) -> {
                            logger.warn("Duplicate adapter for provider '{}': {} vs {}. Using existing.",
                                existing.getProvider(),
                                existing.getClass().getSimpleName(),
                                replacement.getClass().getSimpleName());
                            return existing;
                        }
                    ));
            
            this.adaptersByProvider.putAll(adapterMap);
            
            logger.info("Initialized SCM adapter router with {} adapters: {}", 
                adapters.size(), 
                adaptersByProvider.keySet());
        } else {
            logger.warn("No SCM adapters found during initialization");
        }
    }

    /**
     * Get the appropriate adapter for a repository.
     *
     * @param repo repository reference
     * @return SCM adapter for the repository's provider
     * @throws ScmAdapterException if no adapter is found for the provider
     */
    public ScmAdapter getAdapter(RepoRef repo) {
        if (repo == null || repo.provider() == null) {
            throw new ScmAdapterException("unknown", "route", 
                "Repository or provider is null");
        }

        String provider = repo.provider().toLowerCase().trim();
        ScmAdapter adapter = adaptersByProvider.get(provider);

        if (adapter == null) {
            throw new ScmAdapterException(provider, "route",
                String.format("No adapter found for provider '%s'. Available providers: %s",
                    provider, getSupportedProviders()));
        }

        // Double-check that the adapter actually supports this repository
        if (!adapter.supports(repo)) {
            throw new ScmAdapterException(provider, "route",
                String.format("Adapter for provider '%s' does not support repository: %s",
                    provider, repo));
        }

        return adapter;
    }

    /**
     * Get adapter by provider name.
     *
     * @param provider provider name
     * @return optional adapter for the provider
     */
    public Optional<ScmAdapter> getAdapter(String provider) {
        if (provider == null) {
            return Optional.empty();
        }
        
        String normalizedProvider = provider.toLowerCase().trim();
        return Optional.ofNullable(adaptersByProvider.get(normalizedProvider));
    }

    /**
     * Check if a provider is supported.
     *
     * @param provider provider name to check
     * @return true if the provider is supported
     */
    public boolean isSupported(String provider) {
        if (provider == null) {
            return false;
        }
        return adaptersByProvider.containsKey(provider.toLowerCase().trim());
    }

    /**
     * Check if a repository is supported.
     *
     * @param repo repository reference
     * @return true if the repository's provider is supported
     */
    public boolean isSupported(RepoRef repo) {
        if (repo == null || repo.provider() == null) {
            return false;
        }
        
        try {
            ScmAdapter adapter = getAdapter(repo);
            return adapter.supports(repo);
        } catch (ScmAdapterException e) {
            return false;
        }
    }

    /**
     * Get list of supported provider names.
     *
     * @return set of supported provider names
     */
    public java.util.Set<String> getSupportedProviders() {
        return java.util.Set.copyOf(adaptersByProvider.keySet());
    }

    /**
     * Get count of registered adapters.
     *
     * @return number of registered adapters
     */
    public int getAdapterCount() {
        return adaptersByProvider.size();
    }

    /**
     * Get detailed information about registered adapters.
     *
     * @return map of provider to adapter class name
     */
    public Map<String, String> getAdapterInfo() {
        return adaptersByProvider.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getClass().getSimpleName()
                ));
    }

    /**
     * Register a new adapter (primarily for testing).
     *
     * @param adapter adapter to register
     * @return this router for method chaining
     */
    public ScmAdapterRouter registerAdapter(ScmAdapter adapter) {
        if (adapter != null && adapter.getProvider() != null) {
            String provider = adapter.getProvider().toLowerCase().trim();
            ScmAdapter existing = adaptersByProvider.put(provider, adapter);
            
            if (existing != null) {
                logger.info("Replaced adapter for provider '{}': {} -> {}",
                    provider, existing.getClass().getSimpleName(), adapter.getClass().getSimpleName());
            } else {
                logger.info("Registered new adapter for provider '{}': {}",
                    provider, adapter.getClass().getSimpleName());
            }
        }
        return this;
    }

    /**
     * Unregister an adapter (primarily for testing).
     *
     * @param provider provider name
     * @return removed adapter, or null if none was registered
     */
    public ScmAdapter unregisterAdapter(String provider) {
        if (provider == null) {
            return null;
        }
        
        String normalizedProvider = provider.toLowerCase().trim();
        ScmAdapter removed = adaptersByProvider.remove(normalizedProvider);
        
        if (removed != null) {
            logger.info("Unregistered adapter for provider '{}': {}",
                normalizedProvider, removed.getClass().getSimpleName());
        }
        
        return removed;
    }

    /**
     * Convenience method for webhook signature verification.
     *
     * @param repo repository reference
     * @param headers webhook headers
     * @param rawBody raw webhook body
     * @return true if signature is valid
     * @throws ScmAdapterException if no adapter found or verification fails
     */
    public boolean verifyWebhookSignature(RepoRef repo, Map<String, String> headers, byte[] rawBody) {
        return getAdapter(repo).verifyWebhookSignature(headers, rawBody);
    }

    /**
     * Convenience method for parsing webhook events.
     *
     * @param repo repository reference (used for adapter selection)
     * @param payload webhook payload
     * @param headers webhook headers
     * @return parsed event
     * @throws ScmAdapterException if no adapter found or parsing fails
     */
    public ParsedEvent parseEvent(RepoRef repo, byte[] payload, Map<String, String> headers) {
        return getAdapter(repo).parseEvent(payload, headers);
    }

    /**
     * Health check method to verify all adapters are functional.
     *
     * @return map of provider to health status
     */
    public Map<String, Boolean> healthCheck() {
        return adaptersByProvider.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        try {
                            // Simple health check - verify adapter can identify itself
                            String provider = entry.getValue().getProvider();
                            return provider != null && !provider.trim().isEmpty();
                        } catch (Exception e) {
                            logger.warn("Health check failed for adapter '{}': {}",
                                entry.getKey(), e.getMessage());
                            return false;
                        }
                    }
                ));
    }
}
