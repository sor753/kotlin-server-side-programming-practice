package com.shou.demo.infrastructure.book

// Domain層の型（BookRepositoryが返すべき型）、jOOQが生成したテーブル参照、
// jOOQのクエリ発行口(DSLContext)をimport
import com.shou.demo.domain.book.Book
import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.book.BookWithRental
import com.shou.demo.domain.rental.Rental
import com.shou.demo.jooq.tables.references.BOOK
import com.shou.demo.jooq.tables.references.RENTAL
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

// InfrastructureのBean。Domainの BookRepository インターフェースの実装
@Repository
class JooqBookRepositoryImpl(
    // コンストラクタインジェクションでDSLContextを受け取る
    private val dsl: DSLContext,
) : BookRepository {
    override fun findAllWithRental(): List<BookWithRental> =
        dsl
            .select()
            // BOOKテーブルを起点に
            .from(BOOK)
            // RENTALをLEFT JOIN：貸出中でない本もRENTAL側がNULLの状態で結果に残る
            .leftJoin(RENTAL)
            .on(BOOK.ID.eq(RENTAL.BOOK_ID))
            // 1行(Record)ずつ、任意の型(ここではBookWithRental)に変換する
            .fetch(::toBookWithRental)

    override fun findByIdWithRental(id: Long): BookWithRental? =
        dsl
            .select()
            .from(BOOK)
            .leftJoin(RENTAL)
            .on(BOOK.ID.eq(RENTAL.BOOK_ID))
            .where(BOOK.ID.eq(id))
            .fetchOne(::toBookWithRental)

    private fun toBookWithRental(record: Record): BookWithRental =
        BookWithRental(
            book =
                Book(
                    id = record[BOOK.ID]!!,
                    title = record[BOOK.TITLE]!!,
                    author = record[BOOK.AUTHOR]!!,
                    releaseDate = record[BOOK.RELEASE_DATE]!!,
                ),
            rental =
                record[RENTAL.ID]?.let { rentalId ->
                    Rental(
                        id = rentalId,
                        bookId = record[RENTAL.BOOK_ID]!!,
                        userId = record[RENTAL.USER_ID]!!,
                        rentalDatetime = record[RENTAL.RENTAL_DATETIME]!!,
                        returnDeadline = record[RENTAL.RETURN_DEADLINE]!!,
                    )
                },
        )

    override fun save(book: Book) {
        dsl
            .insertInto(BOOK)
            .set(BOOK.ID, book.id)
            .set(BOOK.TITLE, book.title)
            .set(BOOK.AUTHOR, book.author)
            .set(BOOK.RELEASE_DATE, book.releaseDate)
            .execute()
    }

    override fun update(book: Book) {
        dsl
            .update(BOOK)
            .set(BOOK.TITLE, book.title)
            .set(BOOK.AUTHOR, book.author)
            .set(BOOK.RELEASE_DATE, book.releaseDate)
            .where(BOOK.ID.eq(book.id))
            .execute()
    }

    override fun deleteById(id: Long) {
        dsl
            .deleteFrom(BOOK)
            .where(BOOK.ID.eq(id))
            .execute()
    }
}
