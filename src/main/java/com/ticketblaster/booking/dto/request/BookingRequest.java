package com.ticketblaster.booking.dto.request;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotNull(message = "Event ID cannot be null")
    private Long eventId;

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotNull(message = "Seat count is required")
    @Min(value = 1, message = "At least 1 ticket must be booked")
    private Integer seatCount;
}
