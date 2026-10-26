package com.ticketblaster.booking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Event {
	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	private Long id;
	
	@Column (nullable=false)
	private String title;
	
	@Column (nullable=false)
	private String venue;
	
	@Column (name= "event_time", nullable= false)
	private LocalDateTime eventTime;
	
	@Column (nullable = false)
	private BigDecimal ticketPrice;
	
	@OneToOne(mappedBy="event", cascade= CascadeType.ALL, fetch = FetchType.LAZY)
	private TicketInventory inventory;
	
}




















