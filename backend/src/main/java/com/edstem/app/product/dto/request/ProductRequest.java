package com.edstem.app.product.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
    @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,
    @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,
    @NotNull(message = "Price is required") @Min(value = 0, message = "Price must not be negative")
        Long priceCents,
    @NotNull(message = "Stock is required") @Min(value = 0, message = "Stock must not be negative")
        Integer stock,
    @NotNull(message = "Rating is required")
        @DecimalMin(value = "0.0", message = "Rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "Rating must be between 0 and 5")
        Double rating) {}
