package com.edstem.app.product.service;

import com.edstem.app.product.entity.Product;
import com.edstem.app.product.exception.InsufficientStockException;
import com.edstem.app.product.exception.ProductNotFoundException;
import com.edstem.app.product.repository.ProductRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way other features change product stock. Both methods require a running transaction
 * (MANDATORY), so the caller decides what is committed or rolled back together. The order service
 * uses this to reserve every item of an order in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStockService {

  private final ProductRepository productRepository;
  private final ProductCache productCache;

  @Transactional(propagation = Propagation.MANDATORY)
  public void reserve(UUID productId, int quantity) {
    if (productRepository.decreaseStock(productId, quantity) == 0) {
      Product product =
          productRepository
              .findById(productId)
              .orElseThrow(() -> new ProductNotFoundException(productId));
      throw new InsufficientStockException(product.getName(), quantity, product.getStock());
    }
    productCache.evictAfterCommit(productId);
  }

  /** Gives stock back. Does nothing if the product has been deleted since. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void release(UUID productId, int quantity) {
    if (productRepository.increaseStock(productId, quantity) == 0) {
      log.warn("Could not return stock: product {} no longer exists", productId);
      return;
    }
    productCache.evictAfterCommit(productId);
  }
}
