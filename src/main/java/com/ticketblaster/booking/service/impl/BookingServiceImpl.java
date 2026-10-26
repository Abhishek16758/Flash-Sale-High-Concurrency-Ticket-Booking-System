package com.ticketblaster.booking.service.impl;

import com.ticketblaster.booking.config.RabbitMQConfig;
import com.ticketblaster.booking.dto.message.BookingEventMessage;
import com.ticketblaster.booking.dto.request.BookingRequest;
import com.ticketblaster.booking.dto.response.BookingResponse;
import com.ticketblaster.booking.exception.InsufficientInventoryException;
import com.ticketblaster.booking.exception.LockAcquisitionException;
import com.ticketblaster.booking.exception.ResourceNotFoundException;
import com.ticketblaster.booking.model.Booking;
import com.ticketblaster.booking.model.BookingStatus;
import com.ticketblaster.booking.model.Event;
import com.ticketblaster.booking.model.TicketInventory;
import com.ticketblaster.booking.repository.BookingRepository;
import com.ticketblaster.booking.repository.EventRepository;
import com.ticketblaster.booking.repository.TicketInventoryRepository;
import com.ticketblaster.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final TicketInventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    private static final String LUA_DECREMENT_SCRIPT =
            "local current = tonumber(redis.call('get', KEYS[1]) or '0') " +
            "local requested = tonumber(ARGV[1]) " +
            "if current >= requested then " +
            "    redis.call('decrby', KEYS[1], requested) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    @Override
    @Transactional
    public BookingResponse bookTicketsWithLock(BookingRequest request) {
        log.info("Executing Strategy 1: Pessimistic Lock for eventId: {}", request.getEventId());

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.getEventId()));

        TicketInventory inventory = inventoryRepository.findByEventIdWithLock(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for event: " + request.getEventId()));

        if (inventory.getAvailableTickets() < request.getSeatCount()) {
            throw new InsufficientInventoryException("Sold out! Requested: " + request.getSeatCount() 
                    + ", Available: " + inventory.getAvailableTickets());
        }

        inventory.setAvailableTickets(inventory.getAvailableTickets() - request.getSeatCount());
        inventory.setReservedTickets(inventory.getReservedTickets() + request.getSeatCount());
        inventoryRepository.save(inventory);

        return createBookingRecord(event, request.getUserId(), request.getSeatCount(), 
                BookingStatus.CONFIRMED, "Booked via MySQL Pessimistic Lock");
    }

    @Override
    @Transactional
    public BookingResponse bookTicketsOptimized(BookingRequest request) {
        log.info("Executing Strategy 2: Atomic Decrement for eventId: {}", request.getEventId());

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.getEventId()));

        int rowsUpdated = inventoryRepository.decrementAvailableTickets(request.getEventId(), request.getSeatCount());
        if (rowsUpdated == 0) {
            throw new InsufficientInventoryException("Tickets unavailable or already booked.");
        }

        return createBookingRecord(event, request.getUserId(), request.getSeatCount(), 
                BookingStatus.CONFIRMED, "Booked via MySQL Atomic Decrement");
    }

    @Override
    public BookingResponse bookTicketsFastPath(BookingRequest request) {
        log.info("Executing Strategy 3: Redis In-Memory Lua Gatekeeper for eventId: {}", request.getEventId());
        executeRedisAtomicDecrement(request.getEventId(), request.getSeatCount());
        return bookTicketsOptimized(request);
    }

    @Override
    public BookingResponse bookTicketsWithDistributedLock(BookingRequest request) {
        log.info("Executing Strategy 4: Redisson Distributed Lock for eventId: {}", request.getEventId());
        String lockKey = "lock:event:" + request.getEventId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new LockAcquisitionException("High traffic detected. Unable to acquire distributed lock.");
            }
            return bookTicketsOptimized(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while waiting for distributed lock");
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public BookingResponse bookTicketsAsync(BookingRequest request) {
        log.info("Executing Strategy 5: Redis Lua + RabbitMQ Async Worker for eventId: {}", request.getEventId());

        // 1. In-memory atomic gatekeeper
        executeRedisAtomicDecrement(request.getEventId(), request.getSeatCount());

        // 2. Generate tracking token
        String reference = "TB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 3. Dispatch to queue
        BookingEventMessage message = BookingEventMessage.builder()
                .bookingReference(reference)
                .eventId(request.getEventId())
                .userId(request.getUserId())
                .seatCount(request.getSeatCount())
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_BOOKING, RabbitMQConfig.ROUTING_KEY_BOOKING, message);
        log.info("Stock deducted in Redis RAM. Dispatched task [{}] to RabbitMQ.", reference);

        return BookingResponse.builder()
                .bookingReference(reference)
                .eventId(request.getEventId())
                .seatCount(request.getSeatCount())
                .status(BookingStatus.PENDING)
                .message("Request queued successfully. Seat reserved in cache.")
                .build();
    }

    private void executeRedisAtomicDecrement(Long eventId, Integer seatCount) {
        String inventoryKey = "event:inventory:" + eventId;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(LUA_DECREMENT_SCRIPT);
        redisScript.setResultType(Long.class);

        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(inventoryKey),
                String.valueOf(seatCount)
        );

        if (result == null || result == 0) {
            throw new InsufficientInventoryException("Sold out in cache! Tickets unavailable.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingReference));
        return mapToResponse(booking, "Booking retrieved");
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(b -> mapToResponse(b, "User booking record"))
                .collect(Collectors.toList());
    }

    private BookingResponse createBookingRecord(Event event, Long userId, Integer seatCount, 
                                                BookingStatus status, String message) {
        BigDecimal totalAmount = event.getTicketPrice().multiply(BigDecimal.valueOf(seatCount));
        String reference = "TB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Booking booking = Booking.builder()
                .bookingReference(reference)
                .userId(userId)
                .event(event)
                .seatCount(seatCount)
                .totalAmount(totalAmount)
                .status(status)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        return mapToResponse(savedBooking, message);
    }

    private BookingResponse mapToResponse(Booking booking, String message) {
        return BookingResponse.builder()
                .bookingReference(booking.getBookingReference())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .seatCount(booking.getSeatCount())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .bookingTime(booking.getCreatedAt())
                .message(message)
                .build();
    }
}