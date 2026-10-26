package com.ticketblaster.booking.controller;

import com.ticketblaster.booking.dto.request.BookingRequest;
import com.ticketblaster.booking.dto.response.ApiResponse;
import com.ticketblaster.booking.dto.response.BookingResponse;
import com.ticketblaster.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // Strategy 1: Pessimistic Lock
    @PostMapping("/pessimistic-lock")
    public ResponseEntity<ApiResponse<BookingResponse>> bookWithLock(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.bookTicketsWithLock(request);
        return new ResponseEntity<>(ApiResponse.success(response, "Booked via Pessimistic Lock"), HttpStatus.CREATED);
    }

    // Strategy 2: Atomic Decrement
    @PostMapping("/optimized")
    public ResponseEntity<ApiResponse<BookingResponse>> bookOptimized(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.bookTicketsOptimized(request);
        return new ResponseEntity<>(ApiResponse.success(response, "Booked via Atomic Decrement"), HttpStatus.CREATED);
    }

    // Strategy 3: Redis Cache Gatekeeper
    @PostMapping("/fast-path")
    public ResponseEntity<ApiResponse<BookingResponse>> bookFastPath(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.bookTicketsFastPath(request);
        return new ResponseEntity<>(ApiResponse.success(response, "Booked via Redis Cache Gatekeeper"), HttpStatus.CREATED);
    }

    // Strategy 4: Redisson Distributed Lock
    @PostMapping("/distributed-lock")
    public ResponseEntity<ApiResponse<BookingResponse>> bookDistributed(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.bookTicketsWithDistributedLock(request);
        return new ResponseEntity<>(ApiResponse.success(response, "Booked via Distributed Lock"), HttpStatus.CREATED);
    }

    // Strategy 5: RabbitMQ Decoupled Asynchronous
    @PostMapping("/async")
    public ResponseEntity<ApiResponse<BookingResponse>> bookAsync(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.bookTicketsAsync(request);
        return new ResponseEntity<>(ApiResponse.success(response, "Queued via RabbitMQ"), HttpStatus.ACCEPTED);
    }

    @GetMapping("/{bookingReference}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(@PathVariable String bookingReference) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingByReference(bookingReference), "Booking found"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getUserBookings(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getUserBookings(userId), "User bookings retrieved"));
    }
}