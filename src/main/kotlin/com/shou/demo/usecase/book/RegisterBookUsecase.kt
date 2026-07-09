package com.shou.demo.usecase.book

import com.shou.demo.domain.book.Book
import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.exception.ConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterBookUsecase(
    private val bookRepository: BookRepository,
) {
    @Transactional
    fun execute(book: Book) {
        bookRepository.findByIdWithRental(book.id)?.let {
            throw ConflictException("すでに存在する書籍ID：${book.id}")
        }
        bookRepository.save(book)
    }
}
