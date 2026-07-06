package com.shou.demo.domain.book

import com.shou.demo.domain.rental.Rental

data class BookWithRental(
    val book: Book,
    val rental: Rental?,
) {
    val isRental: Boolean
        get() = rental != null
}
