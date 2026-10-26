package com.ticketblaster.booking.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEventMessage implements Serializable {
    private String bookingReference;
    private Long eventId;
    private Long userId;
    private Integer seatCount;
}