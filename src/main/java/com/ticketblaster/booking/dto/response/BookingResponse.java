package com.ticketblaster.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ticketblaster.booking.model.BookingStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private String bookingReference;
    private Long eventId;
    private String eventTitle;
    private Integer seatCount;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private LocalDateTime bookingTime;
    private String message;

}