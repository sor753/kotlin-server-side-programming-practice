package com.shou.demo.domain.rental

import java.time.LocalDateTime

data class Rental(
    val id: Long,
    val bookId: Long,
    val userId: Long,
    val rentalDatetime: LocalDateTime,
    val returnDeadline: LocalDateTime,
)
