package com.shou.demo.presentation.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// アプリ全体のCORS設定。各Controllerに@CrossOriginを個別に付ける代わりに、
// ここで一括設定することで、Controllerを追加するたびの設定漏れを防ぐ
//
// [注意] Spring Security導入後は、このクラスは実質的に参照されない。
// Spring Securityのフィルターチェーンが全リクエストを先に処理するため、
// CORSは SecurityConfig.kt の HttpSecurity.cors()（corsConfigurationSource()）が
// 唯一有効な設定になっている。このクラスは削除せず参考として残しているだけなので、
// CORSの挙動を変えたいときは SecurityConfig.kt 側を修正すること。
//
// @Configuration: このクラスがSpringのBean定義クラスであることを示す。
// コンポーネントスキャン対象になり、Springが起動時にこのクラスをBeanとして登録する
//
// WebMvcConfigurer: Spring MVCの各種設定（CORS、インターセプター、フォーマッター等）を
// カスタマイズするためのコールバックインターフェース。@EnableWebMvcで設定を丸ごと
// 置き換える必要はなく、必要なメソッドだけoverrideすれば、Springが自動でBeanを検出して
// 該当のコールバックを呼び出してくれる
@Configuration
class WebConfig : WebMvcConfigurer {
    // addCorsMappings: WebMvcConfigurerが提供するコールバックの1つ。
    // Spring起動時に呼び出され、引数のCorsRegistryにルールを登録するとCORS設定が反映される
    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            // 全エンドポイント("/**")を対象にする
            .addMapping("/**")
            // フロントエンド(Vue/webpack-dev-server)の実際のオリジンのみ許可
            .allowedOrigins("http://localhost:8081")
            // このAPIで使用するHTTPメソッドのみ許可
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            // フロントエンドのaxiosがwithCredentials:trueでCookie(セッションID)を
            // 送るため、Access-Control-Allow-Credentials: true を返す必要がある。
            // これが無いとブラウザがレスポンスをCORSエラーとしてブロックする
            .allowCredentials(true)
    }
}
