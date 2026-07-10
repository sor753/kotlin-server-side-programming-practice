package com.shou.demo.presentation.config
import com.shou.demo.domain.user.RoleType
import com.shou.demo.domain.user.UserRepository
import com.shou.demo.infrastructure.security.BookManagerUserDetailsService
import com.shou.demo.presentation.handler.BookManagerAccessDeniedHandler
import com.shou.demo.presentation.handler.BookManagerAuthenticationEntryPoint
import com.shou.demo.presentation.handler.BookManagerAuthenticationFailureHandler
import com.shou.demo.presentation.handler.BookManagerAuthenticationSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository,
) {
    // 認可（アクセス権限）・認証（ログイン方式）の設定をまとめて定義し、
    // SecurityFilterChain としてSpring Securityに登録する
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // 認可（アクセス権限の設定）
            .authorizeHttpRequests { auth ->
                // /login で未認証ユーザーを含むすべてのアクセスを許可
                auth.requestMatchers("/login").permitAll()
                // /admin/** で始まるパスは ADMIN 権限を持つユーザーのみアクセスを許可
                auth.requestMatchers("/admin/**").hasAuthority(RoleType.ADMIN.toString())
                // 上記以外の API は認証済みユーザー（全権限）のみアクセスを許可
                auth.anyRequest().authenticated()
            }.csrf { it.disable() }
            // 認証の設定（フォームログイン：ユーザー名、パスワードでのログイン）
            .formLogin { form ->
                // ログイン API のパスを指定。Spring Security が自動でログイン処理を行う
                form.loginProcessingUrl("/login")
                // ログイン API に渡すユーザー名/パスワードのパラメータ名を指定（デフォルトは username/password）
                form.usernameParameter("email")
                form.passwordParameter("pass")
                // successHandler: 認証成功時の処理を定義するハンドラーを指定
                form.successHandler(BookManagerAuthenticationSuccessHandler())
                // failureHandler: 認証失敗時の処理を定義するハンドラーを指定
                form.failureHandler(BookManagerAuthenticationFailureHandler())
            }
            // 認証、認可時の各種ハンドラーの設定
            .exceptionHandling { ex ->
                // authenticationEntryPoint: 未認証ユーザーがアクセスした場合の処理を定義するハンドラーを指定
                ex.authenticationEntryPoint(BookManagerAuthenticationEntryPoint())
                // accessDeniedHandler: 認可（権限不足）時の処理を定義するハンドラーを指定
                ex.accessDeniedHandler(BookManagerAccessDeniedHandler())
            }
            // CORS の設定を有効化するために、cors() を呼び出す必要がある。呼び出さないとSpring SecurityがCORS設定を無視してしまう
            .cors { it.configurationSource(corsConfigurationSource()) }

        return http.build()
    }

    // 認証処理を実行するクラスの指定。Spring BootがこのBeanとpasswordEncoder()のBeanを
    // 自動で組み合わせてAuthenticationManagerを構築する
    @Bean
    fun userDetailsService() = BookManagerUserDetailsService(userRepository)

    // パスワードのハッシュ化に使用するエンコーダー。BCryptPasswordEncoder はパスワードを BCrypt でハッシュ化する
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private fun corsConfigurationSource(): CorsConfigurationSource {
        // CorsConfiguration: CORS 設定を保持するクラス。CORS 設定の各種プロパティ（許可するオリジン、HTTP メソッド、ヘッダーなど）を設定する
        val corsConfiguration = CorsConfiguration()
        // addAllowedMethod: 許可する HTTP メソッドを追加する。CSRF保護を無効化している分、
        // CORSのメソッド制限が数少ない防御層になるため、実際に使用している4つだけに絞る
        corsConfiguration.addAllowedMethod("GET")
        corsConfiguration.addAllowedMethod("POST")
        corsConfiguration.addAllowedMethod("PUT")
        corsConfiguration.addAllowedMethod("DELETE")
        // addAllowedHeader: 許可する HTTP ヘッダーを追加する。CorsConfiguration.ALL を指定すると、すべての HTTP ヘッダーを許可する
        @Suppress("ForbiddenComment")
        // TODO: メソッドと同様、将来的には実際に使用するヘッダー（例: Content-Type）だけに絞ることを検討する
        corsConfiguration.addAllowedHeader(CorsConfiguration.ALL)
        // addAllowedOrigin: 許可するオリジンを追加する。ここでは、フロントエンドのオリジン（http://localhost:8081）を許可する
        corsConfiguration.addAllowedOrigin("http://localhost:8081")
        corsConfiguration.allowCredentials = true

        val corsConfigurationSource = UrlBasedCorsConfigurationSource()
        corsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration)

        return corsConfigurationSource
    }
}
