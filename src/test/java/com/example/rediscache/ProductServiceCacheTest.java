package com.example.rediscache;

import com.example.rediscache.model.Product;
import com.example.rediscache.repository.ProductRepository;
import com.example.rediscache.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration test: needs Redis running on localhost:6379
 * (docker compose up -d).
 */
@SpringBootTest
class ProductServiceCacheTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private ProductRepository repository;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void secondCallIsServedFromCache() {
        Product saved = productService.createProduct(new Product("Mouse", "Wireless mouse", new BigDecimal("25.00")));

        productService.getProduct(saved.getId());
        productService.getProduct(saved.getId());

        // DB queried only once; second call came from Redis
        verify(repository, times(1)).findById(saved.getId());
    }

    @Test
    void updateRefreshesCache() {
        Product saved = productService.createProduct(new Product("Webcam", "HD webcam", new BigDecimal("40.00")));
        productService.getProduct(saved.getId());

        productService.updateProduct(saved.getId(), new Product("Webcam", "4K webcam", new BigDecimal("90.00")));

        Product cached = productService.getProduct(saved.getId());
        assertThat(cached.getDescription()).isEqualTo("4K webcam");
        assertThat(cached.getPrice()).isEqualByComparingTo("90.00");
    }
}
