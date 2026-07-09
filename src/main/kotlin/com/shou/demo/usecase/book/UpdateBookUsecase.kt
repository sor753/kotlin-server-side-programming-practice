package com.shou.demo.usecase.book

import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class UpdateBookUsecase(
    private val bookRepository: BookRepository,
) {
    @Transactional
    fun execute(
        id: Long,
        title: String?,
        author: String?,
        releaseDate: LocalDate?,
    ) {
        bookRepository.findByIdWithRental(id)?.let {
            bookRepository.update(id, title, author, releaseDate)
        } ?: throw NotFoundException("存在しない書籍ID：$id")
    }
}
