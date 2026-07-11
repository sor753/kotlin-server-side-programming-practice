package com.shou.demo.usecase.rental
import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.exception.ConflictException
import com.shou.demo.domain.exception.NotFoundException
import com.shou.demo.domain.rental.RentalRepository
import com.shou.demo.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private const val RENTAL_TERM_DAYS = 14L

@Service
class RentalStartUsecase(
    private val userRepository: UserRepository,
    private val bookRepository: BookRepository,
    private val rentalRepository: RentalRepository,
) {
    @Transactional
    fun execute(
        bookId: Long,
        userId: Long,
    ) {
        findUserOrThrow(userId)
        val book = findBookOrThrow(bookId)

        if (book.isRental) {
            throw ConflictException("書籍はすでに貸出中です：$bookId")
        }

        val rentalDateTime = LocalDateTime.now()
        val returnDeadLine = rentalDateTime.plusDays(RENTAL_TERM_DAYS)
        rentalRepository.startRental(
            bookId = bookId,
            userId = userId,
            rentalDatetime = rentalDateTime,
            returnDeadline = returnDeadLine,
        )
    }

    private fun findUserOrThrow(userId: Long) =
        userRepository.findById(userId) ?: throw NotFoundException("該当するユーザーが存在しません：$userId")

    private fun findBookOrThrow(bookId: Long) =
        bookRepository.findByIdWithRental(bookId) ?: throw NotFoundException("該当する書籍が存在しません：$bookId")
}
