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
| 1 | 検索系機能（一覧取得・詳細取得） | [ ] 未着手 |
| 2 | 更新系機能（登録・更新・削除） | [ ] 未着手 |
| 3 | Spring Security による認証・認可 | [ ] 未着手 |
| 4 | 貸出・返却機能 | [ ] 未着手 |
| 5 | Spring AOP でログ出力 | [ ] 未着手 |
| 6 | JUnitで単体テスト | [ ] 未着手 |

### フェーズ1: 検索系機能（一覧取得・詳細取得） `[ ] 未着手`

認証機構より先に実装するため、この時点では未認証でアクセス可能な状態でよい。

- **Domain**
  - [ ] `domain/book/Book.kt`: `Book(id, title, author, releaseDate)`
  - [ ] `domain/rental/Rental.kt`: `Rental(id, userId, bookId, rentalDatetime, returnDeadline)`
  - [ ] `domain/book/BookRepository.kt`: `findAll(): List<Book>`, `findById(id): Book?`
  - [ ] `domain/rental/RentalRepository.kt`: `findActiveByBookId(bookId): Rental?`, `findActiveByBookIds(bookIds): List<Rental>`（一覧のN+1回避用）
- **Infrastructure**
  - [ ] `infrastructure/book/BookRepositoryImpl.kt`（jOOQ `BOOK` テーブル操作）
  - [ ] `infrastructure/rental/RentalRepositoryImpl.kt`（jOOQ `RENTAL` テーブル操作。「現在貸出中」は `return_deadline` 等では判定できないため、返却時にレコード自体を削除する設計 ―後述の削除方式を参照― であれば「rentalテーブルに存在する = 貸出中」で判定できる）
- **Usecase**
  - [ ] `usecase/book/FindBookListUsecase.kt`: 全書籍＋貸出状況を取得し `List<BookListItem>` を返す
  - [ ] `usecase/book/FindBookDetailUsecase.kt`: 1件の書籍＋貸出情報を取得
- **Presentation**
  - [ ] `presentation/book/BookController.kt`: `GET /book/list`, `GET /book/detail/{id}`
  - [ ] レスポンス用クラス（`BookListResponse`, `BookListItem`, `BookDetailResponse`, `RentalInfoResponse`）

### フェーズ2: 更新系機能（登録・更新・削除） `[ ] 未着手`

この時点ではまだ権限チェックが無いため、`/admin/*` のパスであっても未認証でアクセス可能な状態になる（フェーズ3で認可を追加するまでの暫定状態）。

- [ ] **Domain**: `BookRepository` に `save(book)`, `update(book)`, `deleteById(id)` を追加
- [ ] **Infrastructure**: `BookRepositoryImpl` に対応する jOOQ の `INSERT`/`UPDATE`/`DELETE` を実装
- [ ] **Usecase**: `RegisterBookUsecase`, `UpdateBookUsecase`, `DeleteBookUsecase`
- [ ] **Presentation**: `BookController` に `POST /admin/book/register`, `PUT /admin/book/update`, `DELETE /admin/book/delete/{id}` を追加

### フェーズ3: Spring Security による認証・認可 `[ ] 未着手`

- [ ] Redis導入: `docker-compose.yml` に `redis` サービス追加、`build.gradle.kts` に `spring-boot-starter-security`, `spring-session-data-redis`, `spring-boot-starter-data-redis` を追加
- [ ] **Domain**: `domain/user/User.kt`（`id, email, password, name, roleType`）, `domain/user/RoleType.kt`（`ADMIN`/`USER`）, `domain/user/UserRepository.kt`（`findByEmail(email): User?`）
- [ ] **Infrastructure**: `infrastructure/user/UserRepositoryImpl.kt`（jOOQ）。Spring Securityの `UserDetailsService` 実装もここ（もしくは `infrastructure/security/` を新設）に配置
- [ ] **Usecase**: `usecase/auth/LoginUsecase.kt`（Spring Securityの認証フローに委譲する形が基本のため、必要最小限のラッパーになる想定）
- [ ] **Presentation**: `presentation/auth/LoginController.kt`（`POST /login`。Spring Securityのフォームログインをそのまま使うか、カスタムフィルタにするかは実装時に決定）
- [ ] **設定**: Spring Session のセッションストアを Redis に向ける設定（`spring.session.store-type=redis` 等）
- [ ] **認可**: `/admin/**` はADMINロール限定にする認可ルールを追加（フェーズ2で暫定的に無認可だった `/admin/*` を保護する）

### フェーズ4: 貸出・返却機能 `[ ] 未着手`

貸出中の判定方式を先に決める必要がある（下記「要検討事項」参照）。

- [ ] **Domain**: `RentalRepository` に `save(rental)`, `deleteByBookId(bookId)` を追加（貸出＝作成、返却＝削除という設計を仮定）
- [ ] **Infrastructure**: `RentalRepositoryImpl` に対応する実装を追加
- [ ] **Usecase**: `StartRentalUsecase`（ログイン中ユーザーIDを使って貸出登録。返却期限の計算ルールも実装）, `EndRentalUsecase`
- [ ] **Presentation**: `presentation/rental/RentalController.kt`: `POST /rental/start`, `DELETE /rental/end/{id}`

### フェーズ5: Spring AOP でログ出力 `[ ] 未着手`

- [ ] `com.shou.demo.usecase..*` を対象にしたポイントカットで、各Usecaseの実行前後（開始・終了・例外）をログ出力する `Aspect` クラスを `infrastructure`（もしくは横断的関心事として別途 `com.shou.demo.aop` 等）に実装

### フェーズ6: JUnitで単体テスト `[ ] 未着手`

- [ ] 各層（Usecase中心）に対するテストを追加。Repositoryは実DBを使う結合テスト、Usecaseはリポジトリをモック化した単体テストを想定

## 要検討事項（実装時に判断が必要）

- **貸出状態の判定方法**: `rental` テーブルに「返却済みかどうか」を表すカラムが無い。案A: 返却時に該当レコードを`DELETE`し「レコードが存在する＝貸出中」とする（テーブル定義上はこちらが自然）。案B: `returned_at` 等のカラムを追加するマイグレーションを足す。どちらにするかで貸出関連のクエリ設計が変わる
- **返却期限の算出ルール**: `return_deadline` をどう計算するか（例: 貸出日+2週間など）がAGENTS.mdに明記されていない
- **ログインの失敗時レスポンス**: フロントエンド側は失敗時のハンドリングをしていない（`then`のみ）ため、エラー時のステータスコード等は実装側で自由に決めてよい
