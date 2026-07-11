package com.shou.demo.presentation.rental

import com.shou.demo.infrastructure.security.BookManagerUserDetails
import com.shou.demo.usecase.rental.RentalEndUsecase
import com.shou.demo.usecase.rental.RentalStartUsecase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("rental")
class RentalController(
    private val rentalStartUsecase: RentalStartUsecase,
    private val rentalEndUsecase: RentalEndUsecase,
) {
    @PostMapping("/start")
    fun startRental(
        @RequestBody request: RentalStartRequest,
        // SecurityContextHolder が保持する Authentication#principal を自動で解決し、引数にバインドしてくれるアノテーション
        // principal の実体はログイン時に BookManagerUserDetailsService#loadUserByUsername が返した BookManagerUserDetails なので、
        // 手動で SecurityContextHolder を取得してキャストする必要がない
        @AuthenticationPrincipal user: BookManagerUserDetails,
    ) {
        rentalStartUsecase.execute(request.bookId, user.id)
    }

    @DeleteMapping("/end/{book_id}")
    fun endRental(
        @PathVariable("book_id") bookId: Long,
        @AuthenticationPrincipal user: BookManagerUserDetails,
    ) {
        rentalEndUsecase.execute(bookId, user.id)
    }
}
