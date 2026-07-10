package com.shou.demo.presentation.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler

class BookManagerAuthenticationSuccessHandler : AuthenticationSuccessHandler {
    // Spring Security のハンドラーは、フレームワーク側で用意されたインターフェースを実装し、メソッドをオーバーライドすることで処理を定義する
    // オーバーライドした onAuthenticationSuccess メソッドにログイン API で認証成功した場合の処理を記述する
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        // HttpServletResponse 型の引数の status プロパティで、返却時の HTTP ステータスコードを設定できる
        response.status = HttpServletResponse.SC_OK
    }
}
