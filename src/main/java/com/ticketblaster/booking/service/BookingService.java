package com.ticketblaster.booking.service;

import com.ticketblaster.booking.dto.request.BookingRequest;
import com.ticketblaster.booking.dto.response.BookingResponse;

import java.util.List;

public interface BookingService {

    // 1. MySQL Pessimistic Lock (SELECT FOR UPDATE)
    BookingResponse bookTicketsWithLock(BookingRequest request);

    // 2. MySQL Atomic Decrement (UPDATE WHERE available >= count)
    BookingResponse bookTicketsOptimized(BookingRequest request);

    // 3. Redis In-Memory Gatekeeper via Lua Script
    BookingResponse bookTicketsFastPath(BookingRequest request);

    // 4. Cross-Node Distributed Lock via Redisson
    BookingResponse bookTicketsWithDistributedLock(BookingRequest request);

    // 5. Asynchronous Decoupled Processing via RabbitMQ
    BookingResponse bookTicketsAsync(BookingRequest request);

    BookingResponse getBookingByReference(String bookingReference);

    List<BookingResponse> getUserBookings(Long userId);
}