# demo (Spring Boot / Kotlin)

## 起動方法

### ローカル環境で起動

JDK 17 がインストールされていれば、Gradle Wrapper でそのまま起動できます。

```shell
./gradlew bootRun
```

起動後、`http://localhost:8080` でアクセスできます。停止する場合はターミナルで `Ctrl+C`。

### Dev Container で起動

`.devcontainer/` 以下に VS Code 向けの Dev Container 設定があります。docker-compose 構成で、アプリ実行用の `app` コンテナと MySQL用の `mysql` コンテナが同時に起動します。

- VS Code: コマンドパレット →「Dev Containers: Reopen in Container」

コンテナが起動したら、コンテナ内のターミナルで同様に `./gradlew bootRun` を実行します。

#### MySQL 接続情報

| 項目 | 値 |
| --- | --- |
| ホスト（コンテナ間） | `mysql` |
| ポート | `3306` |
| データベース名 | `demo` |
| ユーザー名 | `user` |
| パスワード | `password` |

`app` コンテナ（Spring実行環境）からは `mysql` という名前でホスト解決できるため、`application.yaml` では `jdbc:mysql://mysql:3306/demo` を接続先としています。データはボリューム `mysql-data` に永続化されます。

> `.devcontainer/docker-compose.yml` や `devcontainer.json` を変更した場合は、コンテナの再作成（コマンドパレット →「Dev Containers: Rebuild Container」）が必要です。

#### ターミナル（app コンテナ）から DB にアクセスする

`app` コンテナには `mysql` CLI クライアントを導入済みです。VS Code のターミナル（`app` コンテナに接続）から以下を実行します。

```shell
mysql -h mysql -P 3306 -u user -p demo
```

パスワードを聞かれたら `password` を入力します。ワンライナーで済ませたい場合は次のようにパスワードを直接指定できます（`-p` とパスワードの間にスペースを入れない）。

```shell
mysql -h mysql -P 3306 -u user -ppassword demo
```

#### 初期データ投入用 SQL の置き場

`.devcontainer/mysql/initdb.d/` に `.sql`（または `.sh`）ファイルを置くと、MySQL公式イメージの初期化機構により、**`mysql-data` ボリュームが空の状態でコンテナが初回起動したときにファイル名の昇順で自動実行**されます。

例: `01_schema.sql`、`02_seed.sql` のように番号を振ると実行順を制御できます。

> 一度データボリュームが作成されると、SQLファイルを追加してもコンテナの再起動だけでは反映されません。反映するには `docker compose down -v`（または `mysql-data` ボリュームの削除）でデータを初期化してから再作成してください。

初期化スクリプトの先頭には `SET NAMES utf8mb4;` を必ず入れてください。`docker-entrypoint-initdb.d` のスクリプトを実行する `mysql` クライアントは接続文字コードがデフォルトのままだと、日本語などのマルチバイト文字列を**バイト数**で `VARCHAR` の上限判定してしまい、文字数は上限内でも `ERROR 1406: Data too long for column` になることがあります（`SET NAMES utf8mb4;` でセッションの文字コードを明示すると解決します）。

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
