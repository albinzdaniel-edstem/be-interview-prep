package com.edstem.app.product.mapper;

import com.edstem.app.product.dto.request.ProductRequest;
import com.edstem.app.product.dto.response.ProductResponse;
import com.edstem.app.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

  public Product toEntity(ProductRequest request) {
    Product product = new Product();
    update(product, request);
    return product;
  }

  public void update(Product product, ProductRequest request) {
    product.setName(request.name().trim());
    product.setCategory(request.category().trim());
    product.setPriceCents(request.priceCents());
    product.setStock(request.stock());
    product.setRating(request.rating());
  }

  public ProductResponse toResponse(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getCategory(),
        product.getPriceCents(),
        product.getStock(),
        product.getRating(),
        product.getCreatedAt());
  }
}
