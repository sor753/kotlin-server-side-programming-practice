# demo (Spring Boot / Kotlin)

バックエンド（Spring Boot / Kotlin, このリポジトリのルート）とフロントエンド（Vue.js, `frontend/`）を、それぞれ別コンテナに分離した構成です。

- `backend`: Spring Boot アプリ（本README記載の内容）
- `frontend`: Vue.js アプリ（`frontend/README.md` 参照）
- `mysql`: MySQL

## 起動方法

### ローカル環境で起動

JDK 17 がインストールされていれば、Gradle Wrapper でそのまま起動できます。

```shell
./gradlew bootRun
```

起動後、`http://localhost:8080` でアクセスできます。停止する場合はターミナルで `Ctrl+C`。

### Dev Container で起動

`.devcontainer/` 以下に VS Code 向けの Dev Container 設定があります。docker-compose 構成で、`backend`（Spring Boot実行環境）・`frontend`（Vue.js実行環境）・`mysql` の3コンテナが同時に起動します。

- VS Code: コマンドパレット →「Dev Containers: Reopen in Container」（デフォルトで `backend` コンテナに接続します）

コンテナが起動したら、`backend` コンテナ内のターミナルで同様に `./gradlew bootRun` を実行します。

#### フロントエンド（Vue.js）

`frontend` コンテナは起動時に自動で `npm install && npm run dev` を実行し、`http://localhost:8081` でVue.jsアプリを配信します。手動での起動操作は不要です。

- 初回の `npm install` に数十秒〜数分かかります。進捗は `docker logs demo_devcontainer-frontend-1` で確認できます
- `node_modules` は専用の名前付きボリューム（`frontend-node-modules`）に置いているため、ホスト側のOS/アーキテクチャに依存するネイティブモジュールの不整合を避けています
- フロントエンドのコード（`frontend/src/api/*.js`）はAPI接続先を `http://localhost:8080` に直接ハードコードしています。これはブラウザ上のJavaScriptから直接バックエンドへアクセスする構成のため、`backend` コンテナ側で `8080` がホストへフォワードされていれば、フロントエンドがどのコンテナで動いていても問題なく接続できます
- 古いWebpack3/Babel6ベースのツールチェーンのため、Node.jsのOpenSSL3非互換を避けるべく `NODE_OPTIONS=--openssl-legacy-provider` を設定しています

詳細は [frontend/README.md](frontend/README.md) を参照してください。

#### MySQL 接続情報

| 項目 | 値 |
| --- | --- |
| ホスト（コンテナ間） | `mysql` |
| ポート | `3306` |
| データベース名 | `demo` |
| ユーザー名 | `user` |
| パスワード | `password` |

`backend` コンテナ（Spring実行環境）からは `mysql` という名前でホスト解決できるため、`application.yaml` では `jdbc:mysql://mysql:3306/demo` を接続先としています。データはボリューム `mysql-data` に永続化されます。

> `.devcontainer/docker-compose.yml` や `devcontainer.json` を変更した場合は、コンテナの再作成（コマンドパレット →「Dev Containers: Rebuild Container」）が必要です。

#### ターミナル（backend コンテナ）から DB にアクセスする

`backend` コンテナには `mysql` CLI クライアントを導入済みです。VS Code のターミナル（`backend` コンテナに接続）から以下を実行します。

```shell
mysql -h mysql -P 3306 -u user -p demo
```

パスワードを聞かれたら `password` を入力します。ワンライナーで済ませたい場合は次のようにパスワードを直接指定できます（`-p` とパスワードの間にスペースを入れない）。

```shell
mysql -h mysql -P 3306 -u user -ppassword demo
```

#### 初期データ投入用 SQL の置き場

`.devcontainer/mysql/initdb.d/` は、MySQL公式イメージの初期化機構（`mysql-data` ボリュームが空の状態でコンテナが初回起動したときに、置かれた `.sql`/`.sh` ファイルをファイル名の昇順で自動実行する仕組み）用のディレクトリです。

**テーブルスキーマとシードデータは Flyway で管理**しているため、現在このディレクトリは空です（DBユーザー追加など、マイグレーション対象外の初期化が必要になったときに使ってください）。スキーマ変更やシードデータの追加は [Flyway](#flyway) セクションを参照してください。

### Flyway

DBスキーマのバージョン管理に [Flyway](https://flywaydb.org/) を導入しています。

マイグレーションファイルは `src/main/resources/db/migration/` に `V<番号>__<説明>.sql` の形式で置きます。

| ファイル | 内容 |
| --- | --- |
| `V1__create_tables.sql` | `user` / `book` / `rental` テーブルの作成 |
| `V2__seed.sql` | サンプルデータの投入 |

マイグレーションは `spring-boot-starter`（Spring Boot の Flyway自動構成）により、**アプリ起動時に自動的に最新まで適用**されます。手動で適用したい場合は以下を実行します。

```shell
./gradlew flywayMigrate
```

> 公式の `flyway` Gradleプラグインは Gradle 9 と非互換（削除済みの内部APIに依存）だったため、`flyway-commandline` を `JavaExec` で直接実行する自前タスクとして実装しています。

jOOQのコード生成（`generateJooq`）は、このタスクに依存しているため、**常にマイグレーション適用後の最新スキーマを読み取って生成**されます。

新しいマイグレーションを追加した場合、jOOQの生成コードを最新化するには以下を実行してください。

```shell
./gradlew generateJooq
```

### jOOQ

DBスキーマから型安全なKotlinコードを自動生成する [jOOQ](https://www.jooq.org/) を導入しています。基本的な使い方やこのプロジェクトでの具体例は [JOOQ.md](JOOQ.md) を参照してください。

コード生成には起動中のMySQL（`mysql:3306`）への接続が必要なため、Dev Container内で実行してください。

```shell
./gradlew generateJooq
```

### コード変更時の自動リスタート（DevTools）

`spring-boot-devtools` を導入済みのため、以下の手順で保存時に自動リスタートできます。

1. ターミナル1: `./gradlew bootRun` を起動したままにする
2. ターミナル2: `./gradlew compileKotlin --continuous` を実行（保存のたびに自動コンパイル）
3. ファイルを保存すると自動コンパイル → DevTools がクラスパスの変更を検知して Spring を自動リスタート

### VS Code で外部ライブラリの定義ジャンプを有効にする

VS Code + Kotlin Language Server（fwcd）環境では、`.jar` 内のクラスは認識できても Java 実装クラスの `.java` ソースが取得できず、定義ジャンプが効かないことがあります。`idea` プラグインで `sources.jar` / `javadoc.jar` を取得することで解決できます（[参考記事](https://qiita.com/takeru-hirai/items/df836d4147b2fb30813d)）。

```shell
./gradlew idea --no-configuration-cache
```

初回実行時は `--no-configuration-cache` フラグが必須です。

### 新規 Kotlin ファイル作成時の package 宣言忘れ防止

VS Code には IntelliJ の「New Kotlin File」のような、ディレクトリ構造から `package` 宣言を自動生成する機能がありません。`package` 宣言が漏れると Kotlin のコンパイルは通ってしまう一方、Spring のコンポーネントスキャン対象から外れて実行時に動作しない、という気づきにくい不具合につながります。

これを防ぐため `.vscode/kotlin.code-snippets` にスニペットを用意しています。新規 `.kt` ファイルで `pkg` と入力して Tab を押すと、`package com.shou.demo` が挿入されます（パッケージ名部分は編集可能）。

## 参考

さらに詳しい情報は [HELP.md](HELP.md) を参照してください。
