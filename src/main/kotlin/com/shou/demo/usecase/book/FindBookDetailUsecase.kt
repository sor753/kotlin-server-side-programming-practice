package com.shou.demo.usecase.book

import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.book.BookWithRental
import com.shou.demo.domain.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class FindBookDetailUsecase(
    private val bookRepository: BookRepository,
) {
    fun execute(bookId: Long): BookWithRental =
        bookRepository.findByIdWithRental(bookId)
            ?: throw NotFoundException("Book not found with id: $bookId")
}
