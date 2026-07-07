package com.shou.demo.usecase.book

import com.shou.demo.domain.book.BookRepository
import org.springframework.stereotype.Service

@Service
class FindBookListUsecase(
    private val bookRepository: BookRepository,
) {
    fun execute(): List<BookListItem> =
        bookRepository.findAllWithRental().map { bookWithRental ->
            BookListItem(
                id = bookWithRental.book.id,
                title = bookWithRental.book.title,
                author = bookWithRental.book.author,
                isRental = bookWithRental.isRental,
            )
        }
}
