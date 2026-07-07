package com.shou.demo.presentation.book

import com.shou.demo.usecase.book.FindBookListUsecase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/book")
class BookController(
    private val findBookListUsecase: FindBookListUsecase,
) {
    @GetMapping("/list")
    fun getBookList(): GetBookListResponse {
        val bookList = findBookListUsecase.execute()
        return GetBookListResponse(bookList = bookList)
    }
}
