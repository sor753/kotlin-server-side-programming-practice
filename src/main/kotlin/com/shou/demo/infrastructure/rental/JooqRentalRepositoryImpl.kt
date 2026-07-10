package com.shou.demo.infrastructure.rental

import com.shou.demo.domain.rental.Rental
import com.shou.demo.domain.rental.RentalRepository
import com.shou.demo.jooq.tables.references.RENTAL
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqRentalRepositoryImpl(
    private val dsl: DSLContext,
) : RentalRepository {
    override fun startRental(rental: Rental) {
        dsl
            .insertInto(RENTAL)
            .set(RENTAL.BOOK_ID, rental.bookId)
            .set(RENTAL.USER_ID, rental.userId)
            .set(RENTAL.RENTAL_DATETIME, rental.rentalDatetime)
            .set(RENTAL.RETURN_DEADLINE, rental.returnDeadline)
            .execute()
    }
}
