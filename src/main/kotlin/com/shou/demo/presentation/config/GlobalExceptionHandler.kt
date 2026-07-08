package com.shou.demo.presentation.config

import com.shou.demo.domain.exception.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

// @RestControllerAdvice: 全ての@RestControllerに横断的に適用される例外ハンドラーであることを示す。
// 各Controllerに個別の@ExceptionHandlerを書かずに済み、CORS設定をWebConfigに集約したのと
// 同じ考え方で、例外→HTTPレスポンス変換のルールをここに一本化する
@RestControllerAdvice
class GlobalExceptionHandler {
    // @ExceptionHandler: 引数の型(NotFoundException)の例外がController内で投げられたとき、
    // このメソッドが代わりにレスポンスを組み立てる。
    // @ResponseStatus: このハンドラーが処理した場合のHTTPステータスコードを404(Not Found)に固定する。
    // 例外のメッセージ(例: "Book not found with id: 1")をそのままJSONの message フィールドとして返す。
    // Throwable.messageはnullableなためMapの値の型もString?にしている
    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NotFoundException): Map<String, String?> = mapOf("message" to e.message)
}
