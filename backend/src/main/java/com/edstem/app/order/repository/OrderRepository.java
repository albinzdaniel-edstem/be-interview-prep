package com.edstem.app.order.repository;

import com.edstem.app.order.entity.OrderStatus;
import com.edstem.app.order.entity.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {

  @EntityGraph(attributePaths = "items")
  Optional<PurchaseOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

  @EntityGraph(attributePaths = "items")
  Optional<PurchaseOrder> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Changes the status only if the order still has the expected one. When two cancel requests
   * arrive at the same moment, only one of them gets 1 back, so the stock is returned once.
   */
  @Modifying(clearAutomatically = true)
  @Query("update PurchaseOrder o set o.status = :to where o.id = :id and o.status = :from")
  int changeStatus(
      @Param("id") UUID id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}
