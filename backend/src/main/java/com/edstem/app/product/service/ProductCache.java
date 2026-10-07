package com.edstem.app.product.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ProductCache {

  public static final String NAME = "products";

  private final CacheManager cacheManager;

  /**
   * The cache entry is removed only after the database change is committed. If it were removed
   * earlier, a reader could load the old row, put it back in the cache, and keep it there after the
   * commit. An annotation such as @CacheEvict can run before or after the commit depending on how
   * the proxies are ordered, so the timing is set here and does not depend on that.
   */
  public void evictAfterCommit(UUID productId) {
    Cache cache = cacheManager.getCache(NAME);
    if (cache == null) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      cache.evict(productId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            cache.evict(productId);
          }
        });
  }
}
