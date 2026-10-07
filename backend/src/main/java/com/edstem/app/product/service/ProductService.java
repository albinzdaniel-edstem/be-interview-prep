package com.edstem.app.product.service;

import com.edstem.app.product.dto.request.ProductFilter;
import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.dto.response.ProductResponse;
import com.edstem.app.product.entity.Product;
import com.edstem.app.product.exception.InvalidPriceRangeException;
import com.edstem.app.product.exception.ProductNotFoundException;
import com.edstem.app.product.mapper.ProductMapper;
import com.edstem.app.product.repository.ProductRepository;
import com.edstem.app.product.repository.ProductSpecifications;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

  public static final String CACHE_NAME = "products";

  private final ProductRepository productRepository;
  private final ProductMapper productMapper;
  private final CacheManager cacheManager;

  @Transactional
  public ProductResponse create(ProductRequest request) {
    Product saved = productRepository.saveAndFlush(productMapper.toEntity(request));
    log.info("Created product {}", saved.getId());
    return productMapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<ProductResponse> list(ProductFilter filter, Pageable pageable) {
    checkPriceRange(filter);
    return productRepository
        .findAll(ProductSpecifications.from(filter), withStableOrder(pageable))
        .map(productMapper::toResponse);
  }

  /**
   * The first call for an id reads the database. Later calls are answered from the cache until the
   * product is updated or deleted. With {@code sync = true} only one thread loads a missing entry,
   * and a concurrent eviction waits for that load to finish and then removes the entry.
   */
  @Cacheable(cacheNames = CACHE_NAME, key = "#id", sync = true)
  @Transactional(readOnly = true)
  public ProductResponse get(UUID id) {
    return productMapper.toResponse(findOrThrow(id));
  }

  @Transactional
  public ProductResponse update(UUID id, ProductRequest request) {
    Product product = findOrThrow(id);
    productMapper.update(product, request);
    ProductResponse response = productMapper.toResponse(productRepository.saveAndFlush(product));
    evictAfterCommit(id);
    log.info("Updated product {}", id);
    return response;
  }

  @Transactional
  public void delete(UUID id) {
    productRepository.delete(findOrThrow(id));
    evictAfterCommit(id);
    log.info("Deleted product {}", id);
  }

  /**
   * The cache entry is removed only after the database change is committed. If it were removed
   * earlier, a reader could load the old row, put it back in the cache, and keep it there after the
   * commit. An annotation such as @CacheEvict can run before or after the commit depending on how
   * the proxies are ordered, so the timing is set here and does not depend on that.
   */
  private void evictAfterCommit(UUID id) {
    Cache cache = cacheManager.getCache(CACHE_NAME);
    if (cache == null) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      cache.evict(id);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            cache.evict(id);
          }
        });
  }

  private static void checkPriceRange(ProductFilter filter) {
    Long min = filter.minPriceCents();
    Long max = filter.maxPriceCents();
    if (min != null && max != null && min > max) {
      throw new InvalidPriceRangeException();
    }
  }

  /**
   * Adds the id as the last sort key. Many products share a rating, a category or a creation time.
   * Without a unique last key the database may order those ties differently from one page to the
   * next, so a product could appear on two pages or on none.
   */
  private static Pageable withStableOrder(Pageable pageable) {
    Sort sort = pageable.getSort();
    if (sort.getOrderFor("id") == null) {
      sort = sort.and(Sort.by("id"));
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }

  private Product findOrThrow(UUID id) {
    return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }
}
