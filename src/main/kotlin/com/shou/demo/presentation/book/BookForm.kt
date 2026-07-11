@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.shou.demo.presentation.book

import com.shou.demo.domain.book.BookWithRental
import com.shou.demo.domain.rental.Rental
import com.shou.demo.usecase.book.BookListItem
import java.time.LocalDate
import java.time.LocalDateTime

data class GetBookListResponse(
    val bookList: List<BookListItem>,
)

data class GetBookDetailResponse(
    val id: Long,
    val title: String,
    val author: String,
    val releaseDate: LocalDate,
    val rentalInfo: RentalInfo?,
) {
    constructor(model: BookWithRental) : this(
        model.book.id,
        model.book.title,
        model.book.author,
        model.book.releaseDate,
        model.rental?.let { RentalInfo(it) },
    )
}

data class RentalInfo(
    val userId: Long,
    val rentalDatetime: LocalDateTime,
    val returnDeadline: LocalDateTime,
) {
    constructor(rental: Rental) : this(rental.userId, rental.rentalDatetime, rental.returnDeadline)
}

data class RegisterBookRequest(
    val id: Long,
    val title: String,
    val author: String,
    val releaseDate: LocalDate,
)

data class UpdateBookRequest(
    val id: Long,
    val title: String?,
    val author: String?,
    val releaseDate: LocalDate?,
)
