package com.shou.demo.presentation.book

import com.shou.demo.domain.book.Book
import com.shou.demo.usecase.book.DeleteBookUsecase
import com.shou.demo.usecase.book.RegisterBookUsecase
import com.shou.demo.usecase.book.UpdateBookUsecase
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/book")
class AdminBookController(
    private val registerBookUsecase: RegisterBookUsecase,
    private val updateBookUsecase: UpdateBookUsecase,
    private val deleteBookUsecase: DeleteBookUsecase,
) {
    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterBookRequest,
    ) {
        registerBookUsecase.execute(
            Book(
                id = request.id,
                title = request.title,
                author = request.author,
                releaseDate = request.releaseDate,
            ),
        )
    }

    @PutMapping("/update")
    fun update(
        @RequestBody request: UpdateBookRequest,
    ) {
        updateBookUsecase.execute(
            request.id,
            title = request.title,
            author = request.author,
            releaseDate = request.releaseDate,
        )
    }

    @DeleteMapping("/delete/{book_id}")
    fun delete(
        @PathVariable("book_id") bookId: Long,
    ) {
        deleteBookUsecase.execute(bookId)
    }
}
