package com.edstem.app.product.repository;

import com.edstem.app.product.dto.request.ProductFilter;
import com.edstem.app.product.entity.Product;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Each filter is its own small specification that does nothing when its value is missing. They are
 * joined with AND, so any combination works in one query and one count query.
 */
public final class ProductSpecifications {

  private static final char ESCAPE = '\\';

  private ProductSpecifications() {}

  public static Specification<Product> from(ProductFilter filter) {
    return Specification.where(categoryIs(filter.category()))
        .and(priceAtLeast(filter.minPriceCents()))
        .and(priceAtMost(filter.maxPriceCents()))
        .and(inStockOnly(filter.inStock()))
        .and(nameContains(filter.nameContains()));
  }

  private static Specification<Product> categoryIs(String category) {
    if (category == null || category.isBlank()) {
      return null;
    }
    String wanted = category.trim().toLowerCase(Locale.ROOT);
    return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), wanted);
  }

  private static Specification<Product> priceAtLeast(Long minPriceCents) {
    if (minPriceCents == null) {
      return null;
    }
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("priceCents"), minPriceCents);
  }

  private static Specification<Product> priceAtMost(Long maxPriceCents) {
    if (maxPriceCents == null) {
      return null;
    }
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("priceCents"), maxPriceCents);
  }

  private static Specification<Product> inStockOnly(Boolean inStock) {
    if (!Boolean.TRUE.equals(inStock)) {
      return null;
    }
    return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
  }

  private static Specification<Product> nameContains(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
  }

  /** A typed % or _ must match itself, not act as a wildcard. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
