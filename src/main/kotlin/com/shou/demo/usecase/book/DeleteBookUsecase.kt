package com.shou.demo.usecase.book

import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteBookUsecase(
    private val bookRepository: BookRepository,
) {
    @Transactional
    fun execute(id: Long) {
        bookRepository.findByIdWithRental(id)?.let {
            bookRepository.deleteById(id)
        } ?: throw NotFoundException("存在しない書籍ID：$id")
    }
}
