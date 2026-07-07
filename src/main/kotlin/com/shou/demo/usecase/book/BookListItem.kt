package com.shou.demo.usecase.book

data class BookListItem(
    val id: Long,
    val title: String,
    val author: String,
    val isRental: Boolean,
)
