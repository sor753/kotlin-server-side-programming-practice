# 共通認証基盤（SSO）連携プラン

現在の認証（`AUTH.md`参照）は、このアプリ自身がメール/パスワードを検証する`formLogin`方式。本ドキュメントは、それを「共通認証基盤が認証を代行し、このアプリはCookie経由でユーザーIDを受け取るだけ」という方式に置き換える場合の設計案をまとめたもの。**まだ実装はしていない、設計プランの段階。**

## 1. 前提となるシナリオ

- クライアント（ブラウザ）が共通認証基盤で認証に成功すると、共通認証基盤が発行したセッショントークンがCookieにセットされる
- このアプリと共通認証基盤は同一ドメインのため、ブラウザは自動的にそのCookieをこのアプリへのリクエストにも付与する
- このアプリは、受け取ったCookieの値（トークン）を使って共通認証基盤に問い合わせ、ユーザーIDを取得する（＝トークン自体にユーザーIDが埋め込まれているわけではなく、都度の問い合わせが必要な「不透明トークン」という前提）
- 取得したユーザーIDをもとに、このアプリの`user`テーブルからロール等のアプリ固有情報を引く

この前提は、Spring Securityの既存の仕組みでいうと以下のどちらにも完全には一致しない。

| 方式 | 識別子の届き方 | 検証方法 |
| --- | --- | --- |
| Pre-Authentication | ヘッダー等で**すでに検証済みの識別子**が渡る | 検証不要（ゲートウェイを信頼） |
| OAuth2 Resource Server | JWT等の**自己完結したトークン**が渡る | 署名検証のみ（自分で完結、外部問い合わせ不要） |
| **今回のケース** | Cookieに**不透明なトークン**が入っている | **外部（共通認証基盤）への問い合わせが必須** |

そのため、Filter（トークンの抽出）とAuthenticationProvider（トークンの検証・ユーザー実体化）を自作する。

## 2. 全体フロー

```
クライアント
  │  Cookie: SSO_SESSION=xxxxx（共通認証基盤が発行）
  ▼
SecurityContextHolderFilter
  │  このアプリ自身のSpring Session（SESSION Cookie）がすでにあれば、
  │  Redisから復元してSecurityContextにセットする（今までと同じ）
  ▼
SsoCookieAuthenticationFilter（新規） ※すでに認証済みならスキップ
  │  1. リクエストから Cookie "SSO_SESSION" の値を取り出す（I/Oなし、抽出のみ）
  │  2. トークンを積んだ未認証の Authentication を組み立て、AuthenticationManager に渡す
  ▼
SsoTokenAuthenticationProvider（新規）
  │  3. 共通認証基盤にトークンを渡してユーザーIDを問い合わせる（ここでHTTP通信が発生）
  │     → 無効なトークンなら BadCredentialsException
  │  4. 取得したユーザーIDで UserRepository.findById() を呼び、
  │     このアプリ側のロール等を取得（＝BookManagerUserDetailsを再利用）
  ▼
SecurityContextHolder に Authentication をセット
  │  Spring Session が自動的にこの内容をこのアプリ自身のセッション（Redis）に保存
  │  → 以降のリクエストは SsoCookieAuthenticationFilter に到達する前にスキップされる
  ▼
AuthorizationFilter（既存のまま）
  │  authorizeHttpRequests のルールで認可判定（/admin/** は ADMIN のみ、等）
  ▼
Controller（既存のまま。@AuthenticationPrincipal でユーザー取得）
```

**ポイント**: 共通認証基盤への問い合わせが発生するのは、このアプリ側にまだSpring Session（Redis上のセッション）が存在しない最初のリクエストのときだけ。2回目以降は今まで通りCookie（`SESSION`）+ Redisの仕組みがそのまま効くため、毎リクエストで外部問い合わせは発生しない。

## 3. パッケージ構成（追加分）

```
com.shou.demo.infrastructure.security.sso
├── SsoTokenAuthenticationToken.kt        認証前後のトークンを表すAuthenticationの実装
├── SsoCookieAuthenticationFilter.kt       Cookieからトークンを抽出するFilter
├── SsoTokenAuthenticationProvider.kt      トークンを検証しUserDetailsを組み立てるProvider
├── SsoClient.kt                           共通認証基盤への問い合わせのインターフェース
└── SsoClientImpl.kt                       RestClientによる実装（infrastructure層）
```

`SsoClient`をインターフェースにして`infrastructure.security.sso`配下にまとめて置くのは、既存の`UserRepository`（domain）/`JooqUserRepositoryImpl`（infrastructure）の関係と同様、「外部システムとの通信の詳細」をInfrastructure層に閉じ込めるため。ただしこれは認証機構そのものの一部であり業務ドメインの関心事ではないため、`domain`層にインターフェースを置く必要はなく、`infrastructure`層内で完結させてよい。

## 4. 詳細コード

### 4-1. `SsoTokenAuthenticationToken.kt`

認証前（トークンのみ保持）と認証後（UserDetails+権限を保持）の両方を表す、Spring Securityの`Authentication`実装。

```kotlin
package com.shou.demo.infrastructure.security.sso

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

// 認証前: SsoCookieAuthenticationFilter が生成する。principal は空、credentials にトークンだけを持つ
class SsoTokenAuthenticationToken private constructor(
    private val principal: Any?,
    private val token: String,
    authorities: Collection<GrantedAuthority> = emptyList(),
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = principal != null
    }

    override fun getCredentials(): String = token

    override fun getPrincipal(): Any? = principal

    companion object {
        // Filterが未認証状態のトークンを作るときに使う
        fun unauthenticated(token: String) = SsoTokenAuthenticationToken(principal = null, token = token)

        // Providerが検証成功後、認証済みトークンを作るときに使う
        fun authenticated(userDetails: UserDetails, token: String) =
            SsoTokenAuthenticationToken(
                principal = userDetails,
                token = token,
                authorities = userDetails.authorities,
            )
    }
}
```

### 4-2. `SsoCookieAuthenticationFilter.kt`

「抽出だけ」を担当し、実際の検証（外部通信）はしない。既存の`UsernamePasswordAuthenticationFilter`がリクエストボディからemail/passを取り出すだけなのと同じ役割分担。

```kotlin
package com.shou.demo.infrastructure.security.sso

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

private const val SSO_COOKIE_NAME = "SSO_SESSION"

class SsoCookieAuthenticationFilter(
    private val authenticationManager: AuthenticationManager,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // このアプリ自身のSpring Sessionで既に認証済みなら何もしない（Redisセッションを優先）
        val alreadyAuthenticated = SecurityContextHolder.getContext().authentication != null
        val token = request.cookies?.firstOrNull { it.name == SSO_COOKIE_NAME }?.value

        if (!alreadyAuthenticated && token != null) {
            val authResult = authenticationManager.authenticate(SsoTokenAuthenticationToken.unauthenticated(token))
            SecurityContextHolder.getContext().authentication = authResult
        }

        filterChain.doFilter(request, response)
    }
}
```

### 4-3. `SsoClient.kt` / `SsoClientImpl.kt`

共通認証基盤への問い合わせ部分。

```kotlin
package com.shou.demo.infrastructure.security.sso

interface SsoClient {
    // トークンが無効・期限切れの場合は null を返す
    fun resolveUserId(token: String): Long?
}
```

```kotlin
package com.shou.demo.infrastructure.security.sso

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class SsoClientImpl(
    private val restClient: RestClient,
) : SsoClient {
    override fun resolveUserId(token: String): Long? =
        try {
            restClient
                .get()
                .uri("/introspect")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(SsoIntrospectResponse::class.java)
                ?.userId
        } catch (e: RestClientException) {
            // 共通認証基盤が無効なトークンに対して4xxを返す場合はここで捕捉してnull扱いにする
            null
        }
}

data class SsoIntrospectResponse(
    val userId: Long,
)
```

> `RestClient`の接続先URL・タイムアウト等は`application.yaml`に切り出し、`RestClient.Builder`から`baseUrl`を設定したBeanとして注入する想定（詳細は実装時に検討）。

### 4-4. `SsoTokenAuthenticationProvider.kt`

実際の検証（外部通信＋ローカルDB照合）を担当。既存の`DaoAuthenticationProvider`相当の役割。

```kotlin
package com.shou.demo.infrastructure.security.sso

import com.shou.demo.domain.user.UserRepository
import com.shou.demo.infrastructure.security.BookManagerUserDetails
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication

class SsoTokenAuthenticationProvider(
    private val ssoClient: SsoClient,
    private val userRepository: UserRepository,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication {
        val token = authentication.credentials as String

        val userId = ssoClient.resolveUserId(token)
            ?: throw BadCredentialsException("無効なセッショントークンです")
        val user = userRepository.findById(userId)
            ?: throw BadCredentialsException("該当するユーザーが存在しません：$userId")

        return SsoTokenAuthenticationToken.authenticated(BookManagerUserDetails(user), token)
    }

    override fun supports(authentication: Class<*>): Boolean =
        SsoTokenAuthenticationToken::class.java.isAssignableFrom(authentication)
}
```

既存の`BookManagerUserDetails(user)`コンストラクタ、`UserRepository.findById()`をそのまま再利用できる。

### 4-5. `SecurityConfig.kt` の変更点

`formLogin`ブロックを`SsoCookieAuthenticationFilter`の追加に置き換える。カスタムの`AuthenticationProvider`を使う場合、これまで暗黙に自動構成されていた`AuthenticationManager`を明示的に組み立てる必要がある。

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository,
    private val ssoClient: SsoClient,
) {
    @Bean
    fun filterChain(
        http: HttpSecurity,
        authenticationManager: AuthenticationManager,
    ): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/admin/**").hasAuthority(RoleType.ADMIN.toString())
                auth.anyRequest().authenticated()
                // 「/login」の permitAll は不要になる（ログインという独立したエンドポイントが無くなるため）
            }.csrf { it.disable() }
            .addFilterBefore(
                SsoCookieAuthenticationFilter(authenticationManager),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .exceptionHandling { ex ->
                // 未認証・権限不足時のハンドリングは今までと同じものを流用できる
                ex.authenticationEntryPoint(BookManagerAuthenticationEntryPoint())
                ex.accessDeniedHandler(BookManagerAccessDeniedHandler())
            }
            .cors { it.configurationSource(corsConfigurationSource()) }

        return http.build()
    }

    @Bean
    fun authenticationManager(http: HttpSecurity): AuthenticationManager {
        val builder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
        builder.authenticationProvider(SsoTokenAuthenticationProvider(ssoClient, userRepository))
        return builder.build()
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        // 変更なし（既存のまま）
        ...
    }
}
```

**削除されるもの**:
- `formLogin { ... }`ブロック一式（`loginProcessingUrl`, `usernameParameter`, `passwordParameter`）
- `userDetailsService()` Bean、`passwordEncoder()` Bean（パスワード照合自体が不要になるため）
- `BookManagerAuthenticationSuccessHandler`/`BookManagerAuthenticationFailureHandler`（ログインという離散的なユーザー操作が無くなり、認証は各リクエストに対して透過的に行われるようになるため、成功/失敗ハンドラーという概念自体が当てはまらない）
- `/login`エンドポイント自体、および`V4__hash_user_passwords.sql`のようなローカルパスワード管理

**変更なしで流用できるもの**:
- `authorizeHttpRequests`によるパス単位の認可ルール（`/admin/**`等）
- `BookManagerAuthenticationEntryPoint`（401）/`BookManagerAccessDeniedHandler`（403）
- `@AuthenticationPrincipal`によるController側でのユーザー取得
- `BookManagerUserDetails`（`UserDetails`実装）
- Spring Session + Redisによるこのアプリ自身のセッション管理の仕組み

## 5. セッションの運搬方式: Cookie → カスタムヘッダー

Cookieによるセッション運搬は、ブラウザが自動的に付与してしまうためCSRFの懸念がある（このアプリが`csrf { it.disable() }`にしているのもこの弱点をCORSの制限だけでカバーしている状態）。この懸念を無くすため、セッションIDの運搬先をCookieからカスタムヘッダー（`X-Session-Token`）に変更する。

### 5-1. `HeaderHttpSessionIdResolver` への切り替え

Spring Sessionには「セッションIDをどこに載せるか」を差し替えられる`HttpSessionIdResolver`という仕組みがあり、標準で`CookieHttpSessionIdResolver`（デフォルト）と`HeaderHttpSessionIdResolver`が用意されている。Bean登録するだけで、`SecurityConfig`側のコードは一切変更不要。

```kotlin
package com.shou.demo.presentation.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver

@Configuration
class SessionConfig {
    @Bean
    fun httpSessionIdResolver(): HttpSessionIdResolver =
        HeaderHttpSessionIdResolver("X-Session-Token")
}
```

これにより、Spring Sessionの`SessionRepositoryFilter`が以下を自動で行うようになる。

- 認証成功でセッションが新規作成されたタイミングで、レスポンスヘッダー`X-Session-Token`にセッションIDを設定
- 以降のリクエストでは、Cookieではなくリクエストヘッダー`X-Session-Token`からセッションIDを読んでRedisから復元

### 5-2. CORS設定の追加

カスタムヘッダーに変えたことで、ブラウザのJSがレスポンスヘッダーを読めるように`addExposedHeader`の追加が必要（デフォルトでは`Cache-Control`等の一部ヘッダーしかJSに公開されない）。

```kotlin
private fun corsConfigurationSource(): CorsConfigurationSource {
    val corsConfiguration = CorsConfiguration()
    corsConfiguration.addAllowedMethod("GET")
    corsConfiguration.addAllowedMethod("POST")
    corsConfiguration.addAllowedMethod("PUT")
    corsConfiguration.addAllowedMethod("DELETE")
    corsConfiguration.addAllowedHeader(CorsConfiguration.ALL)
    corsConfiguration.addExposedHeader("X-Session-Token")   // 追加: JS側でレスポンスヘッダーを読めるようにする
    corsConfiguration.addAllowedOrigin("http://localhost:8081")
    corsConfiguration.allowCredentials = true

    val corsConfigurationSource = UrlBasedCorsConfigurationSource()
    corsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration)
    return corsConfigurationSource
}
```

> Cookieを使わなくなるため`allowCredentials = true`が本当に必要かは実装時に再確認する（他にCookieを使う予定が無ければ`false`にできる可能性がある）。

### 5-3. フロントエンド側の対応

Cookieのようにブラウザが自動送信してくれないため、クライアント側で明示的に管理する必要がある。

1. ログインAPIのレスポンスから`X-Session-Token`ヘッダーを読み取る
2. `sessionStorage`に保存する
3. 以降の全APIリクエストに`X-Session-Token`ヘッダーとして付与する（axiosの`interceptors`等で一元化）

**保存先を`sessionStorage`にする理由**: `sessionStorage`はタブ（ブラウジングコンテキスト）ごとに独立しており、新しいタブを開くと空の状態から始まり、タブを閉じると破棄される。これにより、同じトークンを複数タブ間でコピー流用することを防げる。`localStorage`は全タブ・全ウィンドウで共有され消えないため、クライアント側でのトークン使い回しを防ぎたい場合には不向き。

ただし、`sessionStorage`が防ぐのは**クライアント側での使い回し**のみで、サーバー側で「同じユーザーの複数セッションが同時に有効であること」自体は防げない（別タブで再度ログインすれば、Redis上には旧セッションと新セッションが並行して存在する）。この制限に対応するのが次節。

## 6. 同時セッション数の制限（1ユーザー1セッションまで）

新しいログインが成立したら、同じユーザーの既存セッションをサーバー側（Redis）で失効させ、常に「1ユーザーにつき有効なセッションは1つまで」を強制する。

### 案A（推奨）: Spring Securityの同時セッション制御 + Spring SessionのIndexed Redis Repository

Spring Securityには`maximumSessions()`という同時セッション数を制限する標準機能があるが、デフォルトの`SessionRegistryImpl`はインメモリ実装でありRedisセッションと連動しない。Spring Sessionが提供する`SpringSessionBackedSessionRegistry`を使うと、Redis上のセッション情報をSpring Securityの`SessionRegistry`として扱えるようになる。

```yaml
# application.yaml
spring:
  session:
    redis:
      repository-type: indexed   # principalNameでの検索に対応したRedisIndexedSessionRepositoryを使う
```

```kotlin
@Configuration
class ConcurrentSessionConfig(
    private val sessionRepository: FindByIndexNameSessionRepository<out Session>,
) {
    @Bean
    fun sessionRegistry(): SpringSessionBackedSessionRegistry<*> =
        SpringSessionBackedSessionRegistry(sessionRepository)
}
```

```kotlin
// SecurityConfig.kt の filterChain 内に追加
.sessionManagement { session ->
    session
        .maximumSessions(1)
        // false: 新しいログインを優先し、既存の古いセッションを失効させる
        // true : 逆に、既存セッションがある場合は新しいログイン自体を拒否する
        .maxSessionsPreventsLogin(false)
        .sessionRegistry(sessionRegistry)
}
```

**挙動**: 2箇所目でログインすると、`ConcurrentSessionControlAuthenticationStrategy`が同一ユーザーの既存セッションを検知し、古いセッションを「失効」としてマークする。古いセッション（＝古い`X-Session-Token`）を使った次のリクエストは、`ConcurrentSessionFilter`が失効を検知してセッションを無効化するため、結果的に未認証（401、`BookManagerAuthenticationEntryPoint`）として扱われる。

> このプロジェクトのSpring Bootバージョンは技術ごとに自動構成モジュールが細分化されている実績があるため（Redis関連で過去に実際にハマった経緯あり）、`spring.session.redis.repository-type`のプロパティ名やクラス名は実装時に必ず実物で確認すること。

### 案B（代替）: 明示的にRedisから旧セッションを削除する

フレームワークの同時セッション制御機能に頼らず、「新しいセッションが作られたら、同じユーザーの他のセッションをこちらで明示的に削除する」処理を自分で書く方法。挙動が読みやすく、このプロジェクトの他のコード（`RentalLookup.kt`等）と同様に「フレームワークの機能に頼りすぎず、明示的なコードで完結させる」方針に近い。

```kotlin
@Component
class SingleSessionEnforcer(
    private val sessionRepository: FindByIndexNameSessionRepository<out Session>,
) : ApplicationListener<SessionCreatedEvent> {
    override fun onApplicationEvent(event: SessionCreatedEvent) {
        val session = sessionRepository.findById(event.sessionId) ?: return
        val principalName =
            session.getAttribute<String>(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME) ?: return

        sessionRepository
            .findByPrincipalName(principalName)
            .keys
            .filter { it != event.sessionId }
            .forEach { sessionRepository.deleteById(it) }
    }
}
```

新しいセッションが作られるたびに、同じprincipal（ユーザー）の他のセッションをRedisから直接削除する。案Aと違って「失効マークを検知して次のリクエストで蹴る」という間接的な流れが無く、即座にRedisから消えるのでより厳密。

### 案A・案Bどちらを選ぶか

| | 案A（`maximumSessions`） | 案B（明示的削除） |
| --- | --- | --- |
| 実装量 | 少ない（設定中心） | やや多い（Listenerを自作） |
| 古いセッションの失効タイミング | 次にそのセッションでリクエストが来たとき | 新しいセッションが作られた瞬間（即座） |
| フレームワークへの依存 | 大きい（内部動作を把握する必要） | 小さい（自分のコードで完結） |

即座に締め出したい（例: 「別の場所でログインされました」を旧セッション側にすぐ反映したい）なら案B、実装コストを抑えたいなら案Aが向く。

## 7. 検討事項（実装前に決めるべきこと）

- **ローカルログインとの共存**: 開発環境や管理用バックドアとして、既存の`formLogin`（メール/パスワード）を残す必要があるか。残す場合、`SsoTokenAuthenticationProvider`と`DaoAuthenticationProvider`の両方を`AuthenticationManagerBuilder`に登録し、`SsoCookieAuthenticationFilter`と`UsernamePasswordAuthenticationFilter`を両方チェーンに残す構成になる
- **共通認証基盤が落ちている場合の挙動**: `SsoClientImpl.resolveUserId()`がタイムアウト・5xxを返した場合、即座に未認証扱い（401）にするか、リトライやフォールバックを挟むか
- **ロール・権限のマッピング**: 共通認証基盤側にロール情報がある場合、それを使うか、このアプリの`user.role_type`を正とするか（現在の設計は後者を前提にしている）
- **Cookie名・共通認証基盤のAPI仕様**: `SSO_SESSION`という名前や`/introspect`エンドポイントの形は仮のもの。実際の共通認証基盤の仕様に合わせる必要がある
- **タイムアウト/リトライ設定**: `SsoClientImpl`が使う`RestClient`のタイムアウト値。認証のたびに呼ばれる可能性があるため（初回リクエスト時）、遅いと全体のレスポンスタイムに直結する
- **ログアウト**: 共通認証基盤側のログアウトとこのアプリのセッション（Redis）の破棄をどう連動させるか（現状は未検討）
- **同時セッション制限の方式**: 6章の案A・案Bのどちらを採用するか
- **`allowCredentials`の要否**: Cookieを使わなくなった後も`true`のままにする必要があるか

## 8. 参考: 認証方式の比較まとめ

| | 現行（`formLogin`） | Pre-Auth（ヘッダー） | OAuth2 Resource Server（JWT） | 今回（SSO Cookie問い合わせ） |
| --- | --- | --- | --- | --- |
| 識別子の届き方 | リクエストボディ（email/pass） | HTTPヘッダー | Authorizationヘッダー（JWT） | Cookie（不透明トークン） |
| 検証方法 | ローカルDBのBCrypt照合 | 検証不要（ゲートウェイを信頼） | 署名検証（自己完結） | 外部への問い合わせ（毎回ではなく初回のみ） |
| 主なSpring Securityの部品 | `UsernamePasswordAuthenticationFilter` + `DaoAuthenticationProvider` | `AbstractPreAuthenticatedProcessingFilter` + `PreAuthenticatedAuthenticationProvider` | `oauth2ResourceServer { jwt {} }` | 自作`OncePerRequestFilter` + 自作`AuthenticationProvider` |
| このプロジェクトでの再利用箇所 | - | `BookManagerUserDetailsService` | - | `BookManagerUserDetails`, `UserRepository.findById` |
