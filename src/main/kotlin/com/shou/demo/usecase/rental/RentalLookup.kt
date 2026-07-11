package com.shou.demo.usecase.rental

import com.shou.demo.domain.book.BookRepository
import com.shou.demo.domain.book.BookWithRental
import com.shou.demo.domain.exception.NotFoundException
import com.shou.demo.domain.user.User
import com.shou.demo.domain.user.UserRepository

internal fun UserRepository.findUserOrThrow(userId: Long): User =
    findById(userId) ?: throw NotFoundException("該当するユーザーが存在しません：$userId")

internal fun BookRepository.findBookOrThrow(bookId: Long): BookWithRental =
    findByIdWithRental(bookId) ?: throw NotFoundException("該当する書籍が存在しません：$bookId")
