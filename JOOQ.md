# jOOQ の使い方

このプロジェクトでは [jOOQ](https://www.jooq.org/) を使って、MySQLのテーブルスキーマから型安全なKotlinコードを自動生成し、SQLをKotlinのDSLとして書けるようにしています。

## jOOQ とは

jOOQ は「DBスキーマを正としてKotlin/Javaのコードを生成し、そのコードを使って型安全にSQLを組み立てる」ライブラリです。

- ORMのように「エンティティからテーブルを作る」のではなく、**「テーブルからコードを作る」**（DB定義が常に唯一の正）
- 生成されたコードにはテーブル名・カラム名・型情報が含まれるため、存在しないカラムを参照するとコンパイルエラーになる
- 生のSQLに近い書き方をしつつ、文字列によるSQL組み立て（ミス・SQLインジェクションのリスク）を避けられる

## コード生成の仕組み

### 設定場所

`build.gradle.kts` の `jooq { }` ブロックで設定しています。

```kotlin
jooq {
	version.set("3.19.15")
	configurations {
		create("main") {
			generateSchemaSourceOnCompilation.set(true)
			jooqConfiguration.apply {
				jdbc.apply {
					driver = "com.mysql.cj.jdbc.Driver"
					url = "jdbc:mysql://mysql:3306/demo"
					user = "user"
					password = "password"
				}
				generator.apply {
					name = "org.jooq.codegen.KotlinGenerator"
					database.apply {
						name = "org.jooq.meta.mysql.MySQLDatabase"
						inputSchema = "demo"
					}
					target.apply {
						packageName = "com.shou.demo.jooq"
						directory = "build/generated-src/jooq/main"
					}
				}
			}
		}
	}
}
```

- 接続先はDev Container内のMySQLコンテナ（`mysql:3306`）。**コード生成にはMySQLが起動している必要があります**
- `KotlinGenerator` を使っているため、生成物はKotlinらしいコード（`val` プロパティ、null許容型など）になる
- 生成先パッケージは `com.shou.demo.jooq`

### 実行方法

```shell
./gradlew generateJooq
```

`compileKotlin` 実行時にも自動的にコード生成が走ります（`generateSchemaSourceOnCompilation.set(true)` のため）。

### バージョン固定について

Spring Boot（`io.spring.dependency-management`）が管理する jOOQ のデフォルトバージョンは Java 21 必須のため、このプロジェクトのJDK17と非互換です。そのため `dependencyManagement` で明示的に `3.19.15` に固定しています。

```kotlin
dependencyManagement {
	dependencies {
		dependency("org.jooq:jooq:3.19.15")
		dependency("org.jooq:jooq-meta:3.19.15")
		dependency("org.jooq:jooq-codegen:3.19.15")
	}
}
```

### スキーマを変更したら

`user.sql` などテーブル定義を変更した場合、以下の手順で反映します。

1. MySQLのデータボリュームを初期化（`docker compose down -v` 等。詳細は [README.md](README.md) 参照）してスキーマ変更を反映
2. `./gradlew generateJooq` を再実行してコードを再生成

生成物 (`build/generated-src/jooq/main`) はビルド成果物なので Git 管理対象外です（`build/` は `.gitignore` 済み）。

## 生成されるコードの例

`user` テーブル（`id`, `name`, `age`, `profile`）から、以下が生成されます。

| クラス | 役割 |
| --- | --- |
| `com.shou.demo.jooq.tables.User` | テーブル定義（`USER.ID`, `USER.NAME` などカラム参照を持つ） |
| `com.shou.demo.jooq.tables.references.USER` | `User` テーブルへのグローバル参照（`val USER: User`） |
| `com.shou.demo.jooq.tables.records.UserRecord` | 1行分のレコードを表すクラス |
| `com.shou.demo.jooq.Demo` | スキーマ（`demo`）定義 |

## 基本的な使い方

`DSLContext` は `spring-boot-starter-jooq` によって自動でBean登録されるため、コンストラクタインジェクションで受け取るだけで使えます。

```kotlin
import com.shou.demo.jooq.tables.references.USER
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val dsl: DSLContext) {

    // SELECT * FROM user
    fun findAll() =
        dsl.selectFrom(USER)
            .fetch()

    // SELECT * FROM user WHERE id = ?
    fun findById(id: Int) =
        dsl.selectFrom(USER)
            .where(USER.ID.eq(id))
            .fetchOne()

    // SELECT name, age FROM user WHERE age >= ?
    fun findNamesOlderThan(age: Int) =
        dsl.select(USER.NAME, USER.AGE)
            .from(USER)
            .where(USER.AGE.ge(age))
            .fetch()

    // INSERT INTO user (id, name, age, profile) VALUES (?, ?, ?, ?)
    fun insert(id: Int, name: String, age: Int, profile: String) {
        dsl.insertInto(USER)
            .set(USER.ID, id)
            .set(USER.NAME, name)
            .set(USER.AGE, age)
            .set(USER.PROFILE, profile)
            .execute()
    }

    // UPDATE user SET age = ? WHERE id = ?
    fun updateAge(id: Int, age: Int) {
        dsl.update(USER)
            .set(USER.AGE, age)
            .where(USER.ID.eq(id))
            .execute()
    }

    // DELETE FROM user WHERE id = ?
    fun deleteById(id: Int) {
        dsl.deleteFrom(USER)
            .where(USER.ID.eq(id))
            .execute()
    }
}
```

### このプロジェクトでの具体例（RESTエンドポイント）

`GreeterController`（`HelloController.kt`）と同様のパターンで、DBから取得した値を返すエンドポイントを作る例です。

```kotlin
import com.shou.demo.jooq.tables.references.USER
import org.jooq.DSLContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("users")
class UserController(private val dsl: DSLContext) {

    @GetMapping
    fun list(): List<UserResponse> =
        dsl.selectFrom(USER)
            .fetch { record ->
                UserResponse(
                    id = record.id!!,
                    name = record.name!!,
                    age = record.age!!,
                    profile = record.profile!!,
                )
            }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Int): UserResponse? =
        dsl.selectFrom(USER)
            .where(USER.ID.eq(id))
            .fetchOne { record ->
                UserResponse(
                    id = record.id!!,
                    name = record.name!!,
                    age = record.age!!,
                    profile = record.profile!!,
                )
            }
}

data class UserResponse(val id: Int, val name: String, val age: Int, val profile: String)
```

`record.id` / `record.name` などは `UserRecord`（`com.shou.demo.jooq.tables.records.UserRecord`）のプロパティで、`user.sql` のカラム定義（`id`, `name`, `age`, `profile`）にそのまま対応しています。

## 参考リンク

- [jOOQ 公式ドキュメント](https://www.jooq.org/doc/latest/manual/)
- [jOOQ Kotlin Coroutines and Reactive APIs](https://www.jooq.org/doc/latest/manual/reactive/)（今回は未使用。同期APIのみ）
