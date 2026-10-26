package com.ticketblaster.booking.config;

import com.ticketblaster.booking.model.Event;
import com.ticketblaster.booking.model.TicketInventory;
import com.ticketblaster.booking.repository.EventRepository;
import com.ticketblaster.booking.repository.TicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EventRepository eventRepository;
    private final TicketInventoryRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        if (eventRepository.count() == 0) {
            log.info("Seeding initial event and inventory data...");

            Event event = Event.builder()
                    .title("Coldplay Music of the Spheres Tour")
                    .venue("DY Patil Stadium, Mumbai")
                    .eventTime(LocalDateTime.now().plusDays(30))
                    .ticketPrice(new BigDecimal("4500.00"))
                    .build();

            Event savedEvent = eventRepository.save(event);

            TicketInventory inventory = TicketInventory.builder()
                    .event(savedEvent)
                    .totalCapacity(100)
                    .availableTickets(100)
                    .reservedTickets(0)
                    .build();

            inventoryRepository.save(inventory);

            // Pre-warm Redis Inventory Cache
            String inventoryKey = "event:inventory:" + savedEvent.getId();
            redisTemplate.opsForValue().set(inventoryKey, String.valueOf(100));

            log.info("Sample event ID: {} pre-loaded into Redis key [{}] with 100 tickets.", 
                    savedEvent.getId(), inventoryKey);
        }
    }
}