package com.shou.demo.domain.book
import java.time.LocalDate

interface BookRepository {
    fun findAllWithRental(): List<BookWithRental>

    fun findByIdWithRental(id: Long): BookWithRental?

    fun save(book: Book)

    fun update(
        id: Long,
        title: String?,
        author: String?,
        releaseDate: LocalDate?,
    )

    fun deleteById(id: Long)
}
