# redis-cache-demo

A small Spring Boot (3.3, Java 17) project that shows Redis caching on a simple Product CRUD API.

## What it demonstrates

| Feature | Where |
|---|---|
| `@Cacheable` (cache on read) | `ProductService.getProduct`, `getAllProducts` |
| `@CachePut` (refresh on update) | `ProductService.updateProduct` |
| `@CacheEvict` (remove on delete / invalidate list) | `createProduct`, `updateProduct`, `deleteProduct` |
| JSON serialization (readable values in Redis) | `RedisConfig` |
| Per-cache TTL (`products` 30 min, `productList` 2 min) | `RedisConfig` |
| Graceful fallback to DB when Redis is down | `RedisConfig#errorHandler` |
| Manual access with `RedisTemplate` | `RedisConfig#redisTemplate` |

The service sleeps 1.5 s inside each DB method so the speed-up is obvious: the first request is slow, repeated requests are instant.

## Requirements

- Java 17+
- Maven 3.9+
- Docker (for Redis)

## Run

```bash
# 1. start Redis
docker compose up -d

# 2. start the app
mvn spring-boot:run
```

The app runs on http://localhost:8080 and seeds 3 products (ids 1–3) into an in-memory H2 database.

## Try it

```bash
# First call: ~1.5s (cache MISS, "DB HIT" appears in the app log)
time curl -s localhost:8080/api/products/1

# Second call: instant (cache HIT, no "DB HIT" log line)
time curl -s localhost:8080/api/products/1

# List (cached separately)
curl -s localhost:8080/api/products

# Create (evicts the list cache)
curl -s -X POST localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Mouse","description":"Wireless mouse","price":25.00}'

# Update (refreshes products::1, evicts the list cache)
curl -s -X PUT localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop Pro","description":"16-inch","price":1499.00}'

# Delete (evicts products::1 and the list cache)
curl -s -X DELETE localhost:8080/api/products/1
```

## Look inside Redis

```bash
docker exec -it redis-cache-demo redis-cli
> KEYS *
1) "demo::products::1"
2) "demo::productList::all"
> GET "demo::products::1"
> TTL "demo::products::1"
```

## Test the Redis-down fallback

Stop Redis (`docker compose stop`) and call the API again. You will see `Redis GET failed...` warnings in the log, but requests still succeed from the database.

## Run the tests

Redis must be running (`docker compose up -d`):

```bash
mvn test
```

## Switch from H2 to MySQL

1. Replace the H2 dependency in `pom.xml` with:

   ```xml
   <dependency>
       <groupId>com.mysql</groupId>
       <artifactId>mysql-connector-j</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```

2. Change the datasource in `application.yml`:

   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/demo
       username: root
       password: yourpassword
     jpa:
       database-platform: org.hibernate.dialect.MySQLDialect
   ```

## Project layout

```
src/main/java/com/example/rediscache
├── RedisCacheDemoApplication.java
├── config
│   ├── RedisConfig.java        # cache manager, TTLs, serializer, error handler
│   └── DataSeeder.java         # sample data
├── controller/ProductController.java
├── service/ProductService.java # @Cacheable / @CachePut / @CacheEvict
├── repository/ProductRepository.java
├── model/Product.java
└── exception/ProductNotFoundException.java
```

## Notes

- Caching works through Spring proxies, so calling a `@Cacheable` method from another method in the **same class** bypasses the cache. That is why the cached methods live in `ProductService` and are called from the controller.
- Always set a TTL as a safety net, and pair every write with `@CachePut` or `@CacheEvict`.
