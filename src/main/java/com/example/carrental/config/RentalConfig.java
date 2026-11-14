package com.example.carrental.config;

import com.example.carrental.model.CarType;
import com.example.carrental.model.Fleet;
import com.example.carrental.repository.entity.FleetCapacityEntity;
import com.example.carrental.repository.FleetCapacityRepository;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumMap;

/**
 * Registers values Spring cannot discover by stereotype scan:
 * {@link Clock}, {@link Fleet} (loaded from DB once at startup),
 * and {@link TransactionTemplate}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RentalProperties.class)
public class RentalConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactions) {
        return new TransactionTemplate(transactions);
    }

    /**
     * Loads fleet sizes from the database once at startup.
     * Seeds {@code fleet_capacity} from {@code rental.fleet.*} when the table is empty.
     */
    @Bean
    Fleet fleet(FleetCapacityRepository capacities, RentalProperties properties, TransactionTemplate transactions) {
        return transactions.execute(status -> {
            if (capacities.count() == 0) {
                seed(capacities, properties);
            }
            return load(capacities);
        });
    }

    private static void seed(FleetCapacityRepository capacities, RentalProperties properties) {
        for (CarType type : CarType.values()) {
            int size = properties.fleet().get(type);
            if (size < 0) {
                throw new IllegalArgumentException(
                        "Fleet size for %s must be non-negative".formatted(type.label()));
            }
            var row = new FleetCapacityEntity();
            row.setCarType(type);
            row.setSize(size);
            capacities.save(row);
        }
    }

    private static Fleet load(FleetCapacityRepository capacities) {
        var sizes = new EnumMap<CarType, Integer>(CarType.class);
        for (FleetCapacityEntity row : capacities.findAll()) {
            sizes.put(row.getCarType(), row.getSize());
        }
        return Fleet.from(sizes);
    }
}
