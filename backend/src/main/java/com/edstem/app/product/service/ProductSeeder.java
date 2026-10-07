package com.edstem.app.product.service;

import com.edstem.app.product.entity.Product;
import com.edstem.app.product.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adds 100 sample products at startup, but only when the table is empty, so restarting the app does
 * not add them again. A fixed random seed makes the data the same on every run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.catalog.seed",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProductSeeder implements ApplicationRunner {

  public static final int SEED_COUNT = 100;

  private static final long RANDOM_SEED = 42;

  private static final String[] ADJECTIVES = {"Compact", "Classic", "Rugged", "Sleek", "Smart"};

  private static final String[][] ITEMS = {
    {"Electronics", "Headphones"},
    {"Electronics", "Speaker"},
    {"Electronics", "Charger"},
    {"Electronics", "Keyboard"},
    {"Home", "Lamp"},
    {"Home", "Kettle"},
    {"Home", "Blender"},
    {"Home", "Clock"},
    {"Kitchen", "Pan"},
    {"Kitchen", "Knife Set"},
    {"Kitchen", "Cutting Board"},
    {"Sports", "Yoga Mat"},
    {"Sports", "Water Bottle"},
    {"Sports", "Backpack"},
    {"Books", "Notebook"},
    {"Books", "Planner"},
    {"Toys", "Puzzle"},
    {"Toys", "Board Game"},
    {"Garden", "Watering Can"},
    {"Garden", "Plant Pot"}
  };

  private final ProductRepository productRepository;

  @Override
  public void run(ApplicationArguments args) {
    if (productRepository.count() > 0) {
      log.info("Products already exist, nothing to seed");
      return;
    }
    productRepository.saveAll(generate(SEED_COUNT));
    log.info("Seeded {} products", SEED_COUNT);
  }

  public static List<Product> generate(int count) {
    Random random = new Random(RANDOM_SEED);
    List<Product> products = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      String[] item = ITEMS[i % ITEMS.length];
      String adjective = ADJECTIVES[(i / ITEMS.length) % ADJECTIVES.length];
      boolean outOfStock = random.nextInt(100) < 15;
      products.add(
          Product.builder()
              .name(adjective + " " + item[1])
              .category(item[0])
              .priceCents(199 + random.nextInt(49_801))
              .stock(outOfStock ? 0 : 1 + random.nextInt(200))
              .rating(Math.round((1 + random.nextDouble() * 4) * 10) / 10.0)
              .build());
    }
    return products;
  }
}
