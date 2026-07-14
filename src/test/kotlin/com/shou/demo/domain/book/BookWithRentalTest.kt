package com.shou.demo.domain.book

import com.shou.demo.domain.rental.Rental
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class BookWithRentalTest {
    // @Test アノテーションをつけた関数が、テスト関数として実行される
    @Test
    fun `rentalがnullのときisRentalはfalse`() {
        val book = Book(1, "title", "author", LocalDate.now())
        val bookWithRental = BookWithRental(book, null)
        assertThat(bookWithRental.isRental).isFalse()
    }

    @Test
    fun `rentalがnullでないときisRentalはtrue`() {
        val book = Book(1, "title", "author", LocalDate.now())
        val rental = Rental(1, book.id, 1, LocalDateTime.now(), LocalDateTime.MAX)
        val bookWithRental = BookWithRental(book, rental)
        assertThat(bookWithRental.isRental).isTrue()
    }
}
