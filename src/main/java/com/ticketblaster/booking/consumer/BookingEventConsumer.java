package com.ticketblaster.booking.consumer;

import com.ticketblaster.booking.config.RabbitMQConfig;
import com.ticketblaster.booking.dto.message.BookingEventMessage;
import com.ticketblaster.booking.model.Booking;
import com.ticketblaster.booking.model.BookingStatus;
import com.ticketblaster.booking.model.Event;
import com.ticketblaster.booking.repository.BookingRepository;
import com.ticketblaster.booking.repository.EventRepository;
import com.ticketblaster.booking.repository.TicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final TicketInventoryRepository inventoryRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOKING)
    @Transactional
    public void processBookingEvent(BookingEventMessage message) {
        log.info("RabbitMQ Worker processing booking token: {}", message.getBookingReference());

        Event event = eventRepository.findById(message.getEventId())
                .orElseThrow(() -> new IllegalStateException("Event record missing: " + message.getEventId()));

        // Sync MySQL Inventory
        int updated = inventoryRepository.decrementAvailableTickets(message.getEventId(), message.getSeatCount());
        if (updated == 0) {
            log.error("Failed to decrement DB inventory for async booking: {}", message.getBookingReference());
            return;
        }

        BigDecimal totalAmount = event.getTicketPrice().multiply(BigDecimal.valueOf(message.getSeatCount()));

        Booking booking = Booking.builder()
                .bookingReference(message.getBookingReference())
                .userId(message.getUserId())
                .event(event)
                .seatCount(message.getSeatCount())
                .totalAmount(totalAmount)
                .status(BookingStatus.CONFIRMED)
                .build();

        bookingRepository.save(booking);
        log.info("Asynchronous persistence complete for [{}]", message.getBookingReference());
    }
}