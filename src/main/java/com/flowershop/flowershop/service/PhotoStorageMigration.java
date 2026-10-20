package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.util.ImageHelper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Runs once at startup: makes sure product.image_data exists, then copies any photo that still
 * lives only in this PC's product-images folder into the database so other PCs can see it.
 */
@Component
public class PhotoStorageMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ProductRepository productRepository;

    public PhotoStorageMigration(JdbcTemplate jdbc, ProductRepository productRepository) {
        this.jdbc = jdbc;
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'image_data'",
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbc.execute("ALTER TABLE product ADD COLUMN image_data LONGBLOB NULL");
                System.out.println("[photos] Added product.image_data column");
            }

            int moved = 0;
            for (Product p : productRepository.findAll()) {
                boolean hasData = p.getImageData() != null && p.getImageData().length > 0;
                if (hasData || p.getImagePath() == null || p.getImagePath().isBlank()) {
                    continue;
                }
                File file = ImageHelper.legacyFile(p.getImagePath());
                if (file == null) {
                    continue; // this PC doesn't have the file; another PC may migrate it
                }
                p.setImageData(ImageHelper.toStoredBytes(file));
                productRepository.save(p);
                moved++;
            }
            if (moved > 0) {
                System.out.println("[photos] Moved " + moved + " product photo(s) into the database");
            }
        } catch (Exception ex) {
            System.err.println("[photos] Photo migration skipped: " + ex.getMessage());
        }
    }
}