// このファイルは今後複数のForm/Responseクラスを追加していく想定のため、
// ファイル名とクラス名の一致を求めるルールを抑制している。
// 複数のトップレベル宣言が存在する状態になれば両ルールとも対象外になるため、
// クラスを追加したタイミングでこのSuppressは削除してよい。
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.shou.demo.presentation.book

import com.shou.demo.usecase.book.BookListItem

data class GetBookListResponse(
    val bookList: List<BookListItem>,
)
