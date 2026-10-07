package com.edstem.app.product.repository;

import com.edstem.app.product.entity.Product;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
    extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

  /**
   * Checks the stock and lowers it in one SQL statement. The database locks the row for the
   * statement, so two orders at the same moment cannot both take the last item. Returns 0 when the
   * product does not exist or has too little stock.
   */
  @Modifying
  @Query(
      "update Product p set p.stock = p.stock - :quantity"
          + " where p.id = :id and p.stock >= :quantity")
  int decreaseStock(@Param("id") UUID id, @Param("quantity") int quantity);

  @Modifying
  @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
  int increaseStock(@Param("id") UUID id, @Param("quantity") int quantity);
}
