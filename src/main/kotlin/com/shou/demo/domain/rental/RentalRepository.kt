package com.shou.demo.domain.rental

import java.time.LocalDateTime

interface RentalRepository {
    fun startRental(
        bookId: Long,
        userId: Long,
        rentalDatetime: LocalDateTime,
        returnDeadline: LocalDateTime,
    )
}
