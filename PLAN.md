# 実装プラン

[AGENTS.md](AGENTS.md) の仕様・実装順序と、現状のコードベース（DBスキーマ・フロントエンド実装済みAPI呼び出し）を突き合わせて具体化した実装プランです。

## 前提・確定事項

- パッケージ構成は [AGENTS.md](AGENTS.md) のオニオンアーキテクチャ（`Presentation`/`Usecase`/`Domain`/`Infrastructure`）を正とする
- `src/main/kotlin/com/shou/demo/HelloController.kt` はサンプルなので触らない・参考にしない
- 認証はCookie＋セッションID方式（Spring Session、セッション実体はRedis）。Redis自体の導入は実装順序3.（認証機能）着手時にまとめて行う
- AOPは実装順序5.で `com.shou.demo.usecase..*` へのログ出力のみ。他層への適用は現時点でなし
- フロントエンド（`frontend/`）は実装済みで変更しない。API のURL・パラメータ形式はフロントエンドのコード（`frontend/src/api/*.js`）から逆算した仕様を正とする

## パッケージ構成

```
com.shou.demo
├── presentation/
│   ├── book/       BookController, リクエスト/レスポンスクラス
│   ├── rental/     RentalController
│   └── auth/       LoginController
├── usecase/
│   ├── book/       各ユースケースクラス（1機能1クラス）
│   ├── rental/
│   └── auth/
├── domain/
│   ├── book/       Book（モデル）, BookRepository（インターフェース）
│   ├── rental/     Rental（モデル）, RentalRepository（インターフェース）
│   └── user/       User（モデル）, UserRepository（インターフェース）, RoleType
└── infrastructure/
    ├── book/       BookRepositoryImpl（jOOQ）
    ├── rental/     RentalRepositoryImpl（jOOQ）
    └── user/       UserRepositoryImpl（jOOQ）
```

呼び出し方向は AGENTS.md の通り `Presentation → Usecase → Domain(インターフェース)`。`Infrastructure` は `Domain` のインターフェースを実装し、DIで注入する（Usecase/DomainはjOOQ生成コードに直接依存しない）。

## フロントエンドから逆算したAPI仕様

`frontend/src/api/*.js` を根拠とする。

| メソッド | パス | リクエスト | レスポンス |
| --- | --- | --- | --- |
| GET | `/book/list` | - | `{ book_list: [{ id, title, author, is_rental }] }` |
| GET | `/book/detail/{id}` | - | `{ id, title, author, release_date, rental_info: { user_id, rental_datetime, return_deadline } \| null }` |
| POST | `/admin/book/register` | `{ id, title, author, release_date }` | - |
| PUT | `/admin/book/update` | `{ id, title, author, release_date }` | - |
| DELETE | `/admin/book/delete/{id}` | - | - |
| POST | `/rental/start` | `{ book_id }` | - |
| DELETE | `/rental/end/{id}` | - | - |
| POST | `/login` | `application/x-www-form-urlencoded`: `email`, `pass` | Cookie（セッションID）をセット |

> `spring.jackson.property-naming-strategy: SNAKE_CASE` 設定済みのため、Kotlin側は camelCase のプロパティ名でよい（`releaseDate` → `release_date` に自動変換される）。
> ログインのフォームパラメータ名は `password` ではなく `pass` である点に注意（`frontend/src/api/auth.js`参照）。

## フェーズ別実装プラン（AGENTS.md 実装順序に対応）

### 進捗サマリー

| フェーズ | 内容 | 状態 |
| --- | --- | --- |
| 1 | 検索系機能（一覧取得・詳細取得） | [x] 完了 |
| 2 | 更新系機能（登録・更新・削除） | [x] 完了 |
| 3 | Spring Security による認証・認可 | [ ] 対応中（Redis導入のみ残り） |
| 4 | 貸出・返却機能 | [ ] 未着手 |
| 5 | Spring AOP でログ出力 | [ ] 未着手 |
| 6 | JUnitで単体テスト | [ ] 未着手 |

### フェーズ1: 検索系機能（一覧取得・詳細取得） `[x] 完了`

認証機構より先に実装するため、この時点では未認証でアクセス可能な状態でよい。

- **Domain**
  - [x] `domain/book/Book.kt`: `Book(id, title, author, releaseDate)`
  - [x] `domain/rental/Rental.kt`: `Rental(id, bookId, userId, rentalDatetime, returnDeadline)`
  - [x] `domain/book/BookWithRental.kt`: `BookWithRental(book: Book, rental: Rental?)`（`isRental` は `rental != null` から導出）
  - [x] `domain/book/BookRepository.kt`: `findAllWithRental(): List<BookWithRental>`（一覧取得用。詳細取得用の `findByIdWithRental` は一覧取得側が完成してから別途追加する）
- **Infrastructure**
  - [x] `infrastructure/book/BookRepositoryImpl.kt`（jOOQ で `book LEFT JOIN rental` を1クエリで取得し `BookWithRental` に変換する。`RentalRepository`/`RentalRepositoryImpl` はフェーズ4まで不要）
- **Usecase**
  - [x] `usecase/book/FindBookListUsecase.kt`: 全書籍＋貸出状況を取得し `List<BookListItem>` を返す
  - [x] `usecase/book/FindBookDetailUsecase.kt`: 1件の書籍＋貸出情報を取得（`BookRepository.findByIdWithRental` の追加が前提。一覧取得側を先に完成させてから着手）
- **Presentation**
  - [x] `presentation/book/BookController.kt`: `GET /book/list`, `GET /book/detail/{id}`
  - [x] レスポンス用クラス（`BookListResponse`, `BookListItem`, `BookDetailResponse`, `RentalInfoResponse`）

### フェーズ2: 更新系機能（登録・更新・削除） `[x] 完了`

この時点ではまだ権限チェックが無いため、`/admin/*` のパスであっても未認証でアクセス可能な状態になる（フェーズ3で認可を追加するまでの暫定状態）。

- [x] **Domain**: `BookRepository` に `save(book)`, `update(id, title?, author?, releaseDate?)`, `deleteById(id)` を追加。`update` はnullが渡されたカラムを更新対象から除外する部分更新方式
- [x] **Infrastructure**: `BookRepositoryImpl` に対応する jOOQ の `INSERT`/`UPDATE`/`DELETE` を実装
- [x] **Usecase**: `RegisterBookUsecase`（重複IDは`ConflictException`で409）, `UpdateBookUsecase`, `DeleteBookUsecase`（存在しないIDは`NotFoundException`で404）
- [x] **Presentation**: `AdminBookController` に `POST /admin/book/register`, `PUT /admin/book/update`, `DELETE /admin/book/delete/{book_id}` を追加

### フェーズ3: Spring Security による認証・認可 `[ ] 対応中`

`POST /login` はSpring Securityの `formLogin()` がフィルターレベルで直接処理するため、**`LoginUsecase`/`LoginController` は作成しなかった**（Presentation/Usecase層に「ログイン」という独立した処理は存在せず、`SecurityConfig`＋`BookManagerUserDetailsService`＋`presentation/handler/*` で完結する）。

- [ ] Redis導入: `docker-compose.yml` に `redis` サービス追加、`build.gradle.kts` に `spring-session-data-redis`, `spring-boot-starter-data-redis` を追加。現状はSpring Bootのデフォルト（インメモリ）セッションで動作確認済み
- [x] **Domain**: `domain/user/User.kt`（`id, email, password, name, roleType`）, `domain/user/RoleType.kt`（`ADMIN`/`USER`）, `domain/user/UserRepository.kt`（`findByEmail(email): User?`）
- [x] **Infrastructure**: `infrastructure/user/JooqUserRepositoryImpl.kt`（jOOQ）, `infrastructure/security/BookManagerUserDetailsService.kt`（Spring Securityの `UserDetailsService` 実装）
- [x] **Presentation**: `presentation/config/SecurityConfig.kt`（`SecurityFilterChain`, CORS設定）, `presentation/handler/`（`BookManagerAuthenticationSuccessHandler`/`FailureHandler`/`AuthenticationEntryPoint`/`AccessDeniedHandler`）
- [ ] **設定**: Spring Session のセッションストアを Redis に向ける設定（`spring.session.store-type=redis` 等）。Redis導入と合わせて対応
- [x] **認可**: `/admin/**` はADMINロール限定、それ以外は認証済みユーザーのみ。ログイン成功/失敗（200/401）、未認証アクセス（401）、権限不足（403）、ADMIN権限での成功（200）を実際にcurlで動作確認済み

### フェーズ4: 貸出・返却機能 `[ ] 未着手`

貸出中の判定は「返却時にレコードを削除し、レコードが存在する＝貸出中」という方式で確定済み（`BookWithRental(rental: Rental?)` の設計を参照）。

- [ ] **Domain**: `domain/rental/RentalRepository.kt` を新規作成し `save(rental)`, `deleteByBookId(bookId)` を定義
- [ ] **Infrastructure**: `RentalRepositoryImpl` に対応する実装を追加
- [ ] **Usecase**: `StartRentalUsecase`（ログイン中ユーザーIDを使って貸出登録。返却期限の計算ルールも実装）, `EndRentalUsecase`
- [ ] **Presentation**: `presentation/rental/RentalController.kt`: `POST /rental/start`, `DELETE /rental/end/{id}`

### フェーズ5: Spring AOP でログ出力 `[ ] 未着手`

- [ ] `com.shou.demo.usecase..*` を対象にしたポイントカットで、各Usecaseの実行前後（開始・終了・例外）をログ出力する `Aspect` クラスを `infrastructure`（もしくは横断的関心事として別途 `com.shou.demo.aop` 等）に実装

### フェーズ6: JUnitで単体テスト `[ ] 未着手`

- [ ] 各層（Usecase中心）に対するテストを追加。Repositoryは実DBを使う結合テスト、Usecaseはリポジトリをモック化した単体テストを想定

## 決定事項

- **貸出状態の判定方法**: 返却時に該当 `rental` レコードを `DELETE` し、「レコードが存在する＝貸出中」とする方式に決定（`domain/book/BookWithRental.kt` の `rental: Rental?` として実装済み）

## 要検討事項（実装時に判断が必要）

- **返却期限の算出ルール**: `return_deadline` をどう計算するか（例: 貸出日+2週間など）がAGENTS.mdに明記されていない
- **ログインの失敗時レスポンス**: フロントエンド側は失敗時のハンドリングをしていない（`then`のみ）ため、エラー時のステータスコード等は実装側で自由に決めてよい
