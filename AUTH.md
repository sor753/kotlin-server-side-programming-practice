# 認証・認可の仕様とコード解説

このドキュメントは、本プロジェクトの認証・認可の仕組みを仕様・実装コード・Spring内部の処理フローの3つの観点でまとめたものです。

## 1. 仕様

[AGENTS.md](AGENTS.md) より抜粋。

- ID（メールアドレス）・パスワードを入力してのログイン
- ログインしているユーザーの権限に応じて機能を制限する
- 書籍の一覧取得・詳細取得、貸出・返却: **全ての認証ユーザー**が実行できる
- 書籍情報の登録・更新・削除（`/admin/**`）: **管理者権限（ADMIN）のユーザーのみ**が実行できる
- 認証方式: Cookie + セッションIDによるセッション認証（JWT等のトークン方式ではない）。セッションの実体はRedisに保存する

### エンドポイントと権限の対応

| パス | 権限 |
| --- | --- |
| `POST /login` | 誰でも（未認証含む） |
| `/admin/**` | `ADMIN` ロールを持つ認証済みユーザーのみ |
| 上記以外すべて（`/book/**`, `/rental/**` など） | 認証済みユーザーであれば誰でも |

### ステータスコードの使い分け

| 状況 | ステータス | 担当ハンドラー |
| --- | --- | --- |
| ログイン成功 | 200 | `BookManagerAuthenticationSuccessHandler` |
| ログイン失敗（メール/パスワード不一致） | 401 | `BookManagerAuthenticationFailureHandler` |
| 未認証でリソースにアクセス | 401 | `BookManagerAuthenticationEntryPoint` |
| 認証済みだが権限不足（例: `USER`が`/admin/**`にアクセス） | 403 | `BookManagerAccessDeniedHandler` |

401（未認証）と403（権限不足）を明確に分けているのがポイント。「ログインすれば直るのか（401）」「ログインしても無理なのか（403）」をクライアント側で判別できる。

## 2. 認証・認可のコード

### ディレクトリ構成

```
com.shou.demo
├── presentation/
│   ├── config/
│   │   └── SecurityConfig.kt          … Spring Securityの中心設定
│   └── handler/
│       ├── BookManagerAuthenticationSuccessHandler.kt
│       ├── BookManagerAuthenticationFailureHandler.kt
│       ├── BookManagerAuthenticationEntryPoint.kt
│       └── BookManagerAccessDeniedHandler.kt
├── infrastructure/
│   └── security/
│       └── BookManagerUserDetailsService.kt   … UserDetailsService実装 + UserDetails実装
└── domain/
    └── user/
        ├── User.kt                    … ドメインモデル
        ├── RoleType.kt                … ADMIN / USER
        └── UserRepository.kt          … findByEmail, findById
```

`LoginController`/`LoginUsecase`のようなクラスは**存在しない**。`POST /login`はSpring Securityの`formLogin()`がフィルターレベルで直接処理するため、Presentation/Usecase層に「ログイン」という独立した処理は書かれていない。

### 2-1. `SecurityConfig.kt` — 設定の中心

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/login").permitAll()
                auth.requestMatchers("/admin/**").hasAuthority(RoleType.ADMIN.toString())
                auth.anyRequest().authenticated()
            }.csrf { it.disable() }
            .formLogin { form ->
                form.loginProcessingUrl("/login")
                form.usernameParameter("email")
                form.passwordParameter("pass")
                form.successHandler(BookManagerAuthenticationSuccessHandler())
                form.failureHandler(BookManagerAuthenticationFailureHandler())
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint(BookManagerAuthenticationEntryPoint())
                ex.accessDeniedHandler(BookManagerAccessDeniedHandler())
            }
            .cors { it.configurationSource(corsConfigurationSource()) }
        return http.build()
    }

    @Bean
    fun userDetailsService() = BookManagerUserDetailsService(userRepository)

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

- **`authorizeHttpRequests`**: パスごとの認可ルール。上から順に評価され、最初にマッチしたルールが適用される（`/admin/**`は`/login`のあとに書かれているが、パスが重複しないので順序影響なし。`anyRequest()`は必ず最後に書く）
- **`csrf { it.disable() }`**: CSRF保護を無効化。SPAフロントエンド + Cookie認証の構成でCSRFトークンのやり取りを実装していないための判断。その代わりCORSの許可メソッドを絞ることで最低限の防御にしている（`corsConfigurationSource()`参照）
- **`formLogin`**: Spring Securityの標準ログイン機構。`loginProcessingUrl`で受け付けるパスを指定するだけで、リクエストボディのパース・パスワード照合・セッション生成まで**フレームワークが自動で行う**（後述のフローを参照）
- **`exceptionHandling`**: 認証・認可で例外が起きた際にどのハンドラーを呼ぶかの指定
- **`userDetailsService()`/`passwordEncoder()`**: この2つのBeanをSpring Bootが自動的に組み合わせて`AuthenticationManager`を構築する。アプリケーションコード側で`AuthenticationManager`を明示的に組み立てる必要はない

### 2-2. `BookManagerUserDetailsService.kt` — ユーザー検索とパスワード照合の橋渡し

```kotlin
class BookManagerUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("User not found: $username")
        return BookManagerUserDetails(user)
    }
}

data class BookManagerUserDetails(
    val id: Long,
    val email: String,
    val pass: String,
    val roleType: RoleType,
) : UserDetails {
    constructor(user: User) : this(user.id, user.email, user.password, user.roleType)

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        AuthorityUtils.createAuthorityList(this.roleType.toString())
    override fun getUsername(): String = this.email
    override fun getPassword(): String = this.pass
    // isEnabled / isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired は常にtrue
}
```

- `loadUserByUsername`の引数`username`には、`formLogin`の`usernameParameter("email")`で指定した`email`パラメータの値が渡ってくる（Spring Securityの用語で「username」と呼んでいるだけで、実体はメールアドレス）
- 返す`UserDetails`（`BookManagerUserDetails`）はログイン成功後、**セッションに保存される**。以降のリクエストでは、このオブジェクトが`Authentication#principal`として復元される
- パスワードの照合自体（`getPassword()`で返した値と、入力された生パスワードのBCrypt比較）は、Spring Securityの`DaoAuthenticationProvider`が`passwordEncoder()` Beanを使って自動的に行う。アプリケーションコードでは比較ロジックを一切書いていない

### 2-3. 4つのハンドラー — 各シナリオでのレスポンス制御

```kotlin
class BookManagerAuthenticationSuccessHandler : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(request, response, authentication) {
        response.status = HttpServletResponse.SC_OK   // 200
    }
}
class BookManagerAuthenticationFailureHandler : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(request, response, exception) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED   // 401
    }
}
class BookManagerAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(request, response, authException) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED   // 401
    }
}
class BookManagerAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(request, response, accessDeniedException) {
        response.status = HttpServletResponse.SC_FORBIDDEN   // 403
    }
}
```

いずれもステータスコードを設定するだけのシンプルな実装。フロントエンド側がレスポンスボディを見ていない（成否をステータスコードのみで判断している）ため、ボディの内容は実装していない。

### 2-4. 認証済みユーザーの取得（Controller側）

```kotlin
@DeleteMapping("/end/{book_id}")
fun endRental(
    @PathVariable("book_id") bookId: Long,
    @AuthenticationPrincipal user: BookManagerUserDetails,
) {
    rentalEndUsecase.execute(bookId, user.id)
}
```

`@AuthenticationPrincipal`を引数につけるだけで、Spring Securityが`SecurityContextHolder`から現在のログインユーザー（`BookManagerUserDetails`）を自動的に解決してバインドしてくれる。`SecurityContextHolder.getContext().authentication.principal as BookManagerUserDetails`を手動で書く必要はない。

### 2-5. Redisへのセッション保存

```kotlin
// application.yaml
spring:
  data:
    redis:
      host: redis
      port: 6379
```

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
```

`spring-boot-starter-session-data-redis`が依存関係に存在するだけで、`HttpSession`の保存先が自動的にRedisに切り替わる（`spring.session.store-type`のようなプロパティ指定は不要 — このプロジェクトのSpring Bootバージョンでは技術ごとに自動構成モジュールが分かれており、依存関係の有無で機能が決まる方式になっている）。ログイン成功後、Redis上に`spring:session:sessions:<セッションID>`というキーでセッション内容（`BookManagerUserDetails`を含む`Authentication`オブジェクト）が保存される。

### 2-6. パスワードのハッシュ化

パスワードは`BCryptPasswordEncoder`（`SecurityConfig.passwordEncoder()`）でハッシュ化してDBに保存する。シードデータ（`V2__seed.sql`）が平文パスワードだったため、`V4__hash_user_passwords.sql`でBCryptハッシュ値に置き換え済み。

## 3. Springの処理フロー

### 3-1. ログイン（`POST /login`）のリクエストフロー

```
クライアント
  │  POST /login  (application/x-www-form-urlencoded, email=..., pass=...)
  ▼
UsernamePasswordAuthenticationFilter（formLoginが自動的に組み込むフィルター）
  │  1. リクエストボディから email/pass パラメータを取り出す
  │     （usernameParameter/passwordParameterで指定した名前を見る）
  │  2. UsernamePasswordAuthenticationToken(email, pass) を作って AuthenticationManager に渡す
  ▼
AuthenticationManager → DaoAuthenticationProvider
  │  3. userDetailsService.loadUserByUsername(email) を呼ぶ
  │     → BookManagerUserDetailsService が UserRepository.findByEmail() でDB検索
  │     → 見つからなければ UsernameNotFoundException
  │  4. passwordEncoder.matches(入力されたpass, userDetails.getPassword()) でBCrypt照合
  │     → 一致しなければ BadCredentialsException
  ▼
  ┌── 認証成功 ──────────────────┐   ┌── 認証失敗 ──────────────────┐
  │ 5a. SecurityContext に         │   │ 5b. AuthenticationException  │
  │     Authentication(principal   │   │     を FailureHandler に渡す │
  │     = BookManagerUserDetails)  │   │                              │
  │     を格納                      │   │ BookManagerAuthentication    │
  │ 6a. Spring SessionがCookie発行  │   │ FailureHandler               │
  │     (Set-Cookie: SESSION=...)  │   │   → response.status = 401    │
  │     し、Redisにセッション保存   │   └──────────────────────────────┘
  │ 7a. BookManagerAuthentication  │
  │     SuccessHandler             │
  │     → response.status = 200    │
  └─────────────────────────────────┘
```

### 3-2. 認証後のリクエスト（例: `GET /book/list`）のフロー

```
クライアント
  │  GET /book/list  (Cookie: SESSION=xxxxx)
  ▼
SecurityContextHolderFilter
  │  1. Cookieのセッションidを使ってSpring SessionがRedisからセッションを復元
  │  2. 復元したAuthenticationをSecurityContextHolderにセット
  │     （セッションが無効/期限切れなら未認証状態のまま次へ）
  ▼
AuthorizationFilter
  │  3. SecurityConfigのauthorizeHttpRequestsルールと突き合わせ
  │     - 未認証 かつ authenticated() が必要なパス
  │         → AuthenticationException
  │         → BookManagerAuthenticationEntryPoint → 401
  │     - 認証済みだが hasAuthority("ADMIN") 等の権限が無い
  │         → AccessDeniedException
  │         → BookManagerAccessDeniedHandler → 403
  │     - 条件を満たす
  │         → 次へ進む
  ▼
DispatcherServlet → Controller
  │  4. @AuthenticationPrincipal によって、SecurityContext内の
  │     principal（BookManagerUserDetails）が引数に自動注入される
  ▼
  Usecase / Domain / Infrastructure（通常のビジネスロジック処理）
```

### 3-3. なぜControllerを書かなくてもログインできるのか

`SecurityFilterChain`はDispatcherServletより**手前**で動作するServlet Filterの一種（`FilterChainProxy`として登録される）。`/login`へのリクエストは、Controller（DispatcherServlet配下のハンドラー）に到達する前に`UsernamePasswordAuthenticationFilter`が横取りして処理を完結させてしまうため、対応するControllerが存在しなくても200/401が返せる。同様の理由で、SAMLやOIDCのような他の認証方式を採用する場合も、多くはControllerではなく専用のFilterやフレームワーク側の仕組みで処理される。

## 参考: 関連ファイル一覧

| 役割 | ファイル |
| --- | --- |
| Security全体設定 | `src/main/kotlin/com/shou/demo/presentation/config/SecurityConfig.kt` |
| ユーザー検索・UserDetails実装 | `src/main/kotlin/com/shou/demo/infrastructure/security/BookManagerUserDetailsService.kt` |
| ログイン成功/失敗ハンドラー | `src/main/kotlin/com/shou/demo/presentation/handler/BookManagerAuthentication{Success,Failure}Handler.kt` |
| 未認証/権限不足ハンドラー | `src/main/kotlin/com/shou/demo/presentation/handler/BookManagerAuthenticationEntryPoint.kt`, `BookManagerAccessDeniedHandler.kt` |
| ユーザードメインモデル | `src/main/kotlin/com/shou/demo/domain/user/{User,RoleType,UserRepository}.kt` |
| Redisセッション設定 | `src/main/resources/application.yaml`, `.devcontainer/docker-compose.yml`（`redis`サービス） |
| パスワードハッシュ化の移行 | `src/main/resources/db/migration/V4__hash_user_passwords.sql` |
