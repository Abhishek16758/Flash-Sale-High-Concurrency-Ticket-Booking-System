package com.ticketblaster.booking.repository;

import com.ticketblaster.booking.model.TicketInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TicketInventoryRepository extends JpaRepository<TicketInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ti FROM TicketInventory ti WHERE ti.event.id = :eventId")
    Optional<TicketInventory> findByEventIdWithLock(@Param("eventId") Long eventId);

    @Modifying
    @Transactional
    @Query("UPDATE TicketInventory ti SET ti.availableTickets = ti.availableTickets - :count, " +
           "ti.reservedTickets = ti.reservedTickets + :count " +
           "WHERE ti.event.id = :eventId AND ti.availableTickets >= :count")
    int decrementAvailableTickets(@Param("eventId") Long eventId, @Param("count") Integer count);
}