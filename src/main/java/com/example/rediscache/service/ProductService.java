package com.example.rediscache.service;

import com.example.rediscache.config.RedisConfig;
import com.example.rediscache.exception.ProductNotFoundException;
import com.example.rediscache.model.Product;
import com.example.rediscache.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The log lines "DB HIT ..." only print when the method body actually runs,
 * i.e. on a cache MISS. If you call the same GET twice and see the log once,
 * the second response came from Redis.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** Cached by id. Runs the method only on a cache miss. */
    @Cacheable(cacheNames = RedisConfig.PRODUCT_CACHE, key = "#id")
    public Product getProduct(Long id) {
        log.debug("DB HIT -> loading product {} from database", id);
        simulateSlowQuery();
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /** Cached list (short TTL, evicted on any write). */
    @Cacheable(cacheNames = RedisConfig.PRODUCT_LIST_CACHE, key = "'all'")
    public List<Product> getAllProducts() {
        log.debug("DB HIT -> loading all products from database");
        simulateSlowQuery();
        return repository.findAll();
    }

    /** New product: the cached list is now stale, so evict it. */
    @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_CACHE, allEntries = true)
    public Product createProduct(Product product) {
        product.setId(null);
        return repository.save(product);
    }

    /**
     * Update: always runs, refreshes the single-product entry (@CachePut)
     * and evicts the list cache.
     */
    @Caching(
            put = @CachePut(cacheNames = RedisConfig.PRODUCT_CACHE, key = "#id"),
            evict = @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_CACHE, allEntries = true)
    )
    public Product updateProduct(Long id, Product updated) {
        Product existing = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setPrice(updated.getPrice());
        return repository.save(existing);
    }

    /** Delete: remove the single entry and the list. */
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_CACHE, key = "#id"),
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void deleteProduct(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
    }

    /** Fake latency so the cache speed-up is obvious when you time the requests. */
    private void simulateSlowQuery() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
