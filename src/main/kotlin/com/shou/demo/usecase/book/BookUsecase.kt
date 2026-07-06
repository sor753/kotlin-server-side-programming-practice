package com.shou.demo.usecase.book

import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.book.BookWithRental
import org.springframework.stereotype.Service

@Service
class BookUsecase(
    private val bookRepository: BookRepository,
) {
    fun getList(): List<BookWithRental> = bookRepository.findAllWithRental()
}
