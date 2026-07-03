# demo (Spring Boot / Kotlin)

## 起動方法

### ローカル環境で起動

JDK 17 がインストールされていれば、Gradle Wrapper でそのまま起動できます。

```shell
./gradlew bootRun
```

起動後、`http://localhost:8080` でアクセスできます。停止する場合はターミナルで `Ctrl+C`。

### Dev Container で起動

`.devcontainer/` 以下に VS Code 向けの Dev Container 設定があります。

- VS Code: コマンドパレット →「Dev Containers: Reopen in Container」

コンテナが起動したら、コンテナ内のターミナルで同様に `./gradlew bootRun` を実行します。

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
