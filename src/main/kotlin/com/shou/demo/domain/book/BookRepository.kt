package com.shou.demo.domain.book

interface BookRepository {
    fun findAllWithRental(): List<BookWithRental>

    fun findByIdWithRental(id: Long): BookWithRental?

    fun save(book: Book)

    fun update(book: Book)

    fun deleteById(id: Long)
}
