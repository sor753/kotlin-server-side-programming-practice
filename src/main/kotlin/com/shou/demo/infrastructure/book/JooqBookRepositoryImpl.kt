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
            .fetch { record ->
                BookWithRental(
                    // book側のカラムはNOT NULL制約があるためnullになり得ない → !! で非null型に変換
                    book =
                        Book(
                            id = record[BOOK.ID]!!,
                            title = record[BOOK.TITLE]!!,
                            author = record[BOOK.AUTHOR]!!,
                            releaseDate = record[BOOK.RELEASE_DATE]!!,
                        ),
                    // RENTAL.IDがnull（LEFT JOINでマッチする貸出レコードが無い）なら
                    // rentalはnullのまま。存在する場合だけRentalを組み立てる
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
            }
}
