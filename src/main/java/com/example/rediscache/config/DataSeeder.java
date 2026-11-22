package com.example.rediscache.config;

import com.example.rediscache.model.Product;
import com.example.rediscache.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/** Inserts a few rows on startup so there is something to cache. */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Product("Laptop", "14-inch developer laptop", new BigDecimal("999.99")));
                repository.save(new Product("Keyboard", "Mechanical keyboard", new BigDecimal("79.50")));
                repository.save(new Product("Monitor", "27-inch 4K monitor", new BigDecimal("329.00")));
            }
        };
    }
}
