package com.edstem.app.product.dto.request;

public record ProductFilter(
    String category, Long minPriceCents, Long maxPriceCents, Boolean inStock, String nameContains) {

  public static ProductFilter none() {
    return new ProductFilter(null, null, null, null, null);
  }
}
