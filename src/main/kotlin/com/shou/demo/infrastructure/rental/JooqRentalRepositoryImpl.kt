package com.shou.demo.infrastructure.rental

import com.shou.demo.domain.rental.RentalRepository
import com.shou.demo.jooq.tables.references.RENTAL
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqRentalRepositoryImpl(
    private val dsl: DSLContext,
) : RentalRepository {
    override fun startRental(
        bookId: Long,
        userId: Long,
        rentalDatetime: LocalDateTime,
        returnDeadline: LocalDateTime,
    ) {
        dsl
            .insertInto(RENTAL)
            .set(RENTAL.BOOK_ID, bookId)
            .set(RENTAL.USER_ID, userId)
            .set(RENTAL.RENTAL_DATETIME, rentalDatetime)
            .set(RENTAL.RETURN_DEADLINE, returnDeadline)
            .execute()
    }

    override fun endRental(bookId: Long) {
        dsl
            .deleteFrom(RENTAL)
            .where(RENTAL.BOOK_ID.eq(bookId))
            .execute()
    }
}
