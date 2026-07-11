package com.shou.demo.usecase.rental

import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.exception.NotFoundException
import com.shou.demo.domain.rental.RentalRepository
import com.shou.demo.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RentalEndUsecase(
    private val userRepository: UserRepository,
    private val bookRepository: BookRepository,
    private val rentalRepository: RentalRepository,
) {
    @Transactional
    fun execute(
        bookId: Long,
        userId: Long,
    ) {
        userRepository.findUserOrThrow(userId)
        val book = bookRepository.findBookOrThrow(bookId)

        if (!book.isRental) {
            throw NotFoundException("書籍は貸出中ではありません：$bookId")
        }
        if (book.rental!!.userId != userId) {
            throw NotFoundException("書籍はこのユーザーによって貸出中ではありません：$bookId")
        }

        rentalRepository.endRental(bookId)
    }
}
