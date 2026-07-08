package com.shou.demo.presentation.book

import com.shou.demo.usecase.book.FindBookDetailUsecase
import com.shou.demo.usecase.book.FindBookListUsecase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/book")
class BookController(
    private val findBookListUsecase: FindBookListUsecase,
    private val findBookDetailUsecase: FindBookDetailUsecase,
) {
    @GetMapping("/list")
    fun getBookList(): GetBookListResponse {
        val bookList = findBookListUsecase.execute()
        return GetBookListResponse(bookList = bookList)
    }

    @GetMapping("/detail/{book_id}")
    fun getDetail(
        @PathVariable("book_id") bookId: Long,
    ): GetBookDetailResponse {
        val book = findBookDetailUsecase.execute(bookId)
        return GetBookDetailResponse(book)
    }
}
