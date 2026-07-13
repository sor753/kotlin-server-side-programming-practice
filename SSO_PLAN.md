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

## 7. ユーザー（スタッフ）・顧客情報のセッションへの同梱

スタッフが特定の顧客の代理としてログインするケースに対応するため、`BookManagerUserDetails`に顧客情報を持たせられるようにする。

```kotlin
data class BookManagerUserDetails(
    val id: Long,
    val email: String,
    val pass: String,
    val roleType: RoleType,
    val customer: Customer?,   // 追加: このスタッフが現在担当している顧客（居ない場合はnull）
) : UserDetails {
    constructor(user: User, customer: Customer? = null) :
        this(user.id, user.email, user.password, user.roleType, customer)
    ...
}
```

`UserDetails`/`principal`はSpring Securityが必須で要求する項目（`username`/`password`/`authorities`等）を満たしてさえいれば、任意のフィールドを自由に追加できる。ログイン中に担当顧客が変わらない前提（8章のフローはこれに該当）であれば、ここに含めてしまって問題ない。

- 途中で担当顧客が切り替わり得る場合は、`UserDetails`に含めず`HttpSession`の別属性として持たせる方が良い（`Authentication`の`principal`はログイン時に1度作られ以降不変という前提で扱われるため）
- Redis（Spring Session）にシリアライズされる対象になるため、`Customer`（や関連する型）もシリアライズ可能である必要がある

## 8. 外部顧客管理システム連携によるログイン紐付け

外部の顧客管理システムから「スタッフ＋顧客」の組をこのアプリに渡し、その組み合わせのままログインを完了させるフロー。HUB基盤経由でのやりとりのため、この経路自体に対してこのアプリはSpring Securityレベルの認証・認可を行わない（＝実際の保護はHUB基盤側のネットワーク境界に委ねる前提）。

### 8-1. 全体フロー

```
外部の顧客管理システム                当システム                      共通認証基盤
       │                                │                               │
       │ 1. POST /external/sso/link     │                               │
       │   { staffCommonId, customer }  │                               │
       │ ──────────────────────────────▶│                               │
       │                                │ ワンタイムトークンを発行       │
       │                                │ Redisに保存(TTL=15秒)         │
       │ ◀────────────────────────────── │                               │
       │   { token }                     │                               │
       │                                │                               │
       │ 2. ブラウザを landing page へ   │                               │
       │    リダイレクト（tokenをpathに埋込）                            │
       │ ─────────────────────────────▶│                               │
       │                                │ PENDING_SSO_TOKEN Cookieに     │
       │                                │ tokenをセット（Max-Age=15秒）  │
       │                                │ 共通認証基盤のログインURLへ302  │
       │                                │──────────────────────────────▶│
       │                                │                               │ 認証
       │                                │◀──────────────────────────────│
       │                                │ SSO_SESSIONクッキー付きで復帰  │
       │                                │                               │
       │                                │ 3. SsoTokenAuthenticationProvider
       │                                │    が発火（4-4を拡張）:        │
       │                                │    a. SSO_SESSIONからstaffの   │
       │                                │       共通識別子を解決          │
       │                                │    b. PENDING_SSO_TOKEN Cookie │
       │                                │       からRedisの保留情報を取得 │
       │                                │       (取得と同時に削除=使い捨て)│
       │                                │    c. 両者のstaffCommonIdが     │
       │                                │       一致するか検証            │
       │                                │    d. 一致 → customer情報も含めて
       │                                │       BookManagerUserDetailsを │
       │                                │       構築しセッション確立      │
```

**Cookieが無い場合**（外部システム経由のフローを通っていない、直接アクセス等）は、フロント側が共通認証基盤のログインURLへ遷移させる制御を行う想定。バックエンド側はこのケースを異常とはみなさず、`customer = null`の通常ログインとして扱う。

### 8-2. ワンタイムトークン発行エンドポイント（認証・認可なし）

```kotlin
package com.shou.demo.presentation.sso

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/external/sso")
class ExternalSsoLinkController(
    private val pendingSsoLinkStore: PendingSsoLinkStore,
) {
    @PostMapping("/link")
    fun createLink(
        @RequestBody request: CreateSsoLinkRequest,
    ): CreateSsoLinkResponse {
        val token = UUID.randomUUID().toString()
        pendingSsoLinkStore.save(token, PendingSsoLink(request.staffCommonId, request.customer))
        return CreateSsoLinkResponse(token)
    }
}
```

`SecurityConfig`側は`/login`と同様に`permitAll()`にする。

```kotlin
auth.requestMatchers("/external/sso/**").permitAll()
```

> **重要**: `permitAll()`は「Spring Securityとしては誰でも通す」という意味であり「保護不要」という意味ではない。実際の保護はHUB基盤側（ネットワーク境界、mTLS、IP制限等）にある前提。このエンドポイントはstaffCommonIdと顧客情報を誰でも紐付けられる入口になるため、HUB以外からの到達を防ぐネットワーク制御が別途必須。

### 8-3. 保留情報のRedisストア（TTL=15秒・使い捨て）

Spring Sessionが使うRedisとは別の名前空間（`sso:pending-link:*`）で、シンプルな`StringRedisTemplate`ベースのストアとして実装する。

```kotlin
package com.shou.demo.infrastructure.security.sso

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class PendingSsoLinkStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private fun key(token: String) = "sso:pending-link:$token"

    fun save(token: String, link: PendingSsoLink) {
        redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(link), Duration.ofSeconds(15))
    }

    // 取得と同時に削除する。TTLだけに頼ると同じトークンでのリプレイを許してしまうため、
    // 一度読んだら即座に使い捨てる
    fun consume(token: String): PendingSsoLink? {
        val json = redisTemplate.opsForValue().getAndDelete(key(token)) ?: return null
        return objectMapper.readValue(json, PendingSsoLink::class.java)
    }
}

data class PendingSsoLink(
    val staffCommonId: String,
    val customer: Customer,
)
```

### 8-4. ランディングページ（token→Cookie変換 + SSOへリダイレクト）

```kotlin
@GetMapping("/landing/{token}")
fun landing(
    @PathVariable token: String,
    response: HttpServletResponse,
): ResponseEntity<Void> {
    val cookie =
        Cookie("PENDING_SSO_TOKEN", token).apply {
            maxAge = 15
            isHttpOnly = true
            path = "/"
        }
    response.addCookie(cookie)
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("https://sso.example.com/login?returnTo=..."))
        .build()
}
```

同一ドメイン前提のため、`PENDING_SSO_TOKEN`は共通認証基盤側の画面を経由してブラウザに戻ってきても保持される。フロントエンドのJSがこの値を使う必要は無いため`HttpOnly`にできる（バックエンドの照合にのみ使う）。

### 8-5. `SsoTokenAuthenticationProvider`の拡張（4-4を差し替え）

```kotlin
package com.shou.demo.infrastructure.security.sso

import com.shou.demo.domain.user.UserRepository
import com.shou.demo.infrastructure.security.BookManagerUserDetails
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication

class SsoTokenAuthenticationProvider(
    private val ssoClient: SsoClient,
    private val userRepository: UserRepository,
    private val pendingSsoLinkStore: PendingSsoLinkStore,
    private val request: HttpServletRequest,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication {
        val token = authentication.credentials as String
        val staffCommonId = ssoClient.resolveUserId(token)
            ?: throw BadCredentialsException("無効なセッショントークンです")

        val pendingCookieValue = request.cookies?.firstOrNull { it.name == "PENDING_SSO_TOKEN" }?.value

        // Cookie が無い = 外部システム経由の連携フローを通っていない通常ログイン。エラーにはしない
        val customer =
            if (pendingCookieValue == null) {
                null
            } else {
                val pendingLink =
                    pendingSsoLinkStore.consume(pendingCookieValue)
                        ?: throw PendingSsoLinkExpiredException("連携情報の有効期限が切れています")
                if (pendingLink.staffCommonId != staffCommonId) {
                    throw PendingSsoLinkExpiredException("連携情報のユーザーが一致しません")
                }
                pendingLink.customer
            }

        val user = userRepository.findByCommonId(staffCommonId)
            ?: throw BadCredentialsException("該当するユーザーが存在しません")

        return SsoTokenAuthenticationToken.authenticated(BookManagerUserDetails(user, customer), token)
    }

    override fun supports(authentication: Class<*>): Boolean =
        SsoTokenAuthenticationToken::class.java.isAssignableFrom(authentication)
}
```

`UserRepository`には`staffCommonId`（共通認証基盤・外部システム間で共有される識別子）で検索する`findByCommonId`が新たに必要になる（現在は`findByEmail`/`findById`のみ）。

### 8-6. 期限切れ・不一致を専用の失敗として伝える

このフローには`formLogin`のような明示的なログインAPIが無く、SSO復帰後の最初のリクエストで`SsoCookieAuthenticationFilter`が透過的に認証を試みる構造（2章参照）のため、例外は`ExceptionTranslationFilter`経由で`AuthenticationEntryPoint`に流れる。専用の例外を用意し、既存の`BookManagerAuthenticationEntryPoint`で分岐させる。

```kotlin
class PendingSsoLinkExpiredException(message: String) : AuthenticationException(message)
```

```kotlin
class BookManagerAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        if (authException is PendingSsoLinkExpiredException) {
            response.contentType = "application/json"
            response.writer.write("""{"error":"PENDING_LINK_EXPIRED"}""")
        }
    }
}
```

`error: "PENDING_LINK_EXPIRED"`を見て、フロント側は「外部の顧客管理システムからやり直してください」という専用の案内を表示する想定。通常の未認証（401、ボディ無し）とは区別可能。staffCommonIdの不一致も同じ例外として扱っている（期限切れと同様「もう一度外部システムからやり直すべき」状況と判断したもので、区別してログ・レスポンスを分けたい場合は別途検討）。

### 8-7. 決定事項まとめ

| ケース | 挙動 |
| --- | --- |
| `PENDING_SSO_TOKEN` Cookieが無い | 通常ログインとして成立（`customer = null`）。フロント側が共通認証基盤ログインURLへの遷移を制御 |
| Cookieはあるが期限切れ／消費済み | `PendingSsoLinkExpiredException` → 401 + `{"error":"PENDING_LINK_EXPIRED"}`。外部システムからやり直す |
| Cookieの値とSSO解決結果のstaffCommonIdが不一致 | 同上（`PendingSsoLinkExpiredException`） |
| 一致 | `customer`情報を含めた`BookManagerUserDetails`でログイン成立 |

## 9. 開発環境用バックドアログイン（`formLogin`の存続）

開発環境・管理用バックドアとして、既存の`formLogin`（メール/パスワード）を残す。ただし本番・検証環境への漏洩を防ぐため、単一の仕組みに頼らず多層でガードする。開発環境限定でパスワード照合は行わず、メールのみで認証する。

### 9-1. 二重の有効化条件（プロファイル + 明示的なフラグ）

```yaml
# application-dev.yaml（新規）
app:
  dev-backdoor-login:
    enabled: true
```

```kotlin
@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "app.dev-backdoor-login", name = ["enabled"], havingValue = "true")
class DevBackdoorAuthConfig(
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(DevBackdoorAuthConfig::class.java)

    @PostConstruct
    fun warnOnStartup() {
        logger.warn("!".repeat(80))
        logger.warn("!!! DEV BACKDOOR LOGIN ENABLED (email-only, no password check) !!!")
        logger.warn("!!! This must NEVER be active outside local development !!!")
        logger.warn("!".repeat(80))
    }

    @Bean
    fun devBackdoorAuthenticationProvider(): DevBackdoorAuthenticationProvider =
        DevBackdoorAuthenticationProvider(userRepository)
}
```

`@Profile("dev")`だけでなく`@ConditionalOnProperty`も併用し、「プロファイルがdevであること」と「このバックドア専用の明示的なフラグがtrueであること」の2つの独立した条件が両方揃わないと有効化されないようにする。`dev`プロファイルは他の目的（ログレベル調整等）にも使われがちなので、どちらか一方だけ誤って本番に紛れ込んでも発動しない。起動時の`WARN`ログは、万一有効化されてしまった場合に気づくための最後の砦。

### 9-2. メールのみで認証するProvider

```kotlin
@Profile("dev")
class DevBackdoorAuthenticationProvider(
    private val userRepository: UserRepository,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication {
        val email = authentication.name
        val user = userRepository.findByEmail(email)
            ?: throw BadCredentialsException("該当するユーザーが存在しません：$email")

        val userDetails = BookManagerUserDetails(user)
        return UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
    }

    override fun supports(authentication: Class<*>): Boolean =
        UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
}
```

`formLogin`が組み立てる`UsernamePasswordAuthenticationToken`（email/passを含む）を受け取るが、パスワードは一切見ずメールだけで検証する。

### 9-3. `SecurityConfig`側の配線

`AuthenticationManagerBuilder`への登録をSpringの`ObjectProvider`経由にすることで、`dev`プロファイルが無効な環境ではこのProviderのBean自体が存在せず、登録しようがない状態にする。

```kotlin
@Bean
fun authenticationManager(
    http: HttpSecurity,
    ssoTokenAuthenticationProvider: SsoTokenAuthenticationProvider,
    devBackdoorAuthenticationProvider: ObjectProvider<DevBackdoorAuthenticationProvider>,
): AuthenticationManager {
    val builder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
    builder.authenticationProvider(ssoTokenAuthenticationProvider)
    devBackdoorAuthenticationProvider.ifAvailable { builder.authenticationProvider(it) }
    return builder.build()
}
```

`ObjectProvider`を使うことで、Beanが存在しない（dev以外の環境）場合でも`SecurityConfig`自体はエラーにならず、単に登録がスキップされる。

### 9-4. デプロイ前のチェックリスト（コードだけでは担保できない部分）

- 本番・検証環境のデプロイ設定で`SPRING_PROFILES_ACTIVE`に`dev`を含めていないことを確認する運用ルールが必要
- `application-dev.yaml`自体を本番用のビルド成果物・コンテナイメージに含めない（含まれていても`@Profile`で無効化はされるが、そもそも同梱しないのが望ましい）
- `dev`プロファイルを他の目的（ログレベル調整等）に使い回さない。使い回すと「他の理由でdevを有効にしたらバックドアも起動する」事故につながる

## 10. 検討事項（実装前に決めるべきこと）

- **共通認証基盤が落ちている場合の挙動**: `SsoClientImpl.resolveUserId()`がタイムアウト・5xxを返した場合、即座に未認証扱い（401）にするか、リトライやフォールバックを挟むか
- **ロール・権限のマッピング**: 共通認証基盤側にロール情報がある場合、それを使うか、このアプリの`user.role_type`を正とするか（現在の設計は後者を前提にしている）
- **Cookie名・共通認証基盤のAPI仕様**: `SSO_SESSION`という名前や`/introspect`エンドポイントの形は仮のもの。実際の共通認証基盤の仕様に合わせる必要がある
- **タイムアウト/リトライ設定**: `SsoClientImpl`が使う`RestClient`のタイムアウト値。認証のたびに呼ばれる可能性があるため（初回リクエスト時）、遅いと全体のレスポンスタイムに直結する
- **ログアウト**: 共通認証基盤側のログアウトとこのアプリのセッション（Redis）の破棄をどう連動させるか（現状は未検討）
- **同時セッション制限の方式**: 6章の案A・案Bのどちらを採用するか
- **`allowCredentials`の要否**: Cookieを使わなくなった後も`true`のままにする必要があるか
- **`/external/sso/**`のネットワーク境界**: HUB基盤以外からの到達をどう塞ぐか（このアプリのコードでは担保できない前提）
- **`UserRepository.findByCommonId`の追加**: staffCommonIdでの検索方法の実装（DBスキーマに共通識別子カラムが必要）
- **staffCommonId不一致時の扱い**: 期限切れと同一の`PendingSsoLinkExpiredException`で良いか、区別したセキュリティイベントとして扱うか

## 11. 参考: 認証方式の比較まとめ

| | 現行（`formLogin`） | Pre-Auth（ヘッダー） | OAuth2 Resource Server（JWT） | 今回（SSO Cookie問い合わせ） |
| --- | --- | --- | --- | --- |
| 識別子の届き方 | リクエストボディ（email/pass） | HTTPヘッダー | Authorizationヘッダー（JWT） | Cookie（不透明トークン） |
| 検証方法 | ローカルDBのBCrypt照合 | 検証不要（ゲートウェイを信頼） | 署名検証（自己完結） | 外部への問い合わせ（毎回ではなく初回のみ） |
| 主なSpring Securityの部品 | `UsernamePasswordAuthenticationFilter` + `DaoAuthenticationProvider` | `AbstractPreAuthenticatedProcessingFilter` + `PreAuthenticatedAuthenticationProvider` | `oauth2ResourceServer { jwt {} }` | 自作`OncePerRequestFilter` + 自作`AuthenticationProvider` |
| このプロジェクトでの再利用箇所 | - | `BookManagerUserDetailsService` | - | `BookManagerUserDetails`, `UserRepository.findById` |
