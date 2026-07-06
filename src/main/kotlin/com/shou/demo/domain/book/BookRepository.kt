package com.shou.demo.domain.book

interface BookRepository {
    fun findAllWithRental(): List<BookWithRental>
}