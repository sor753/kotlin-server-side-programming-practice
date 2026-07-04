# Demo
Spring/Kotlin の勉強用デモアプリケーション

## 1.Demo システムの仕様

組織で所有する書籍の情報や。貸出、返却の状態を管理するアプリケーション

### 実装する機能

 - ログイン、セッション管理
 - 権限機能
 - 書籍の一覧取得
 - 書籍の詳細取得
 - 書籍情報の登録
 - 書籍情報の更新
 - 書籍情報の削除
 - 貸出
 - 返却

#### ログイン、セッション管理、権限機能
ID、パスワードを入力してのログインと。ログインしているユーザーの権限に応じての機能制限を行う

#### 書籍の一覧取得、詳細取得
一覧ですべての書籍のリストを表示し、書籍名を選択すると詳細表示の画面へ遷移する。すべてのユーザーが実行できる

####  書籍情報の登録、更新、削除
管理者権限のユーザーのみが実行できる機能

#### 貸出、返却
書籍に紐づく貸出情報の登録、削除を行う。すべてのユーザーが実行できる

---

## 2.アプリケーションの構成

### 使用している技術スタック

 - Kotlin
 - Spring Boot
 - JOOQ
 - Flyway
 - MySQL
 - Redis
 - Docker

なお、フロントエンドは、frontend　に実装ずみ
Redis は認証処理で使用

### オニオンアーキテクチャをベースにした設計
オニオンアーキテクチャをベースに DDD を指向する

#### 概要

|階層|概要|
|----|----|
|Presentation|UI に直結する処理の実装を担う|
|Infrastructure|I/Oに関する技術スタック（データベースなど）に関する実装を担う|
|Usecase|各機能の仕様に応じた処理を担う|
|Domain Service|ドメインに関するビジネスロジックを担う|
|Domain Model|ドメインに関する状態と振る舞いを担う|
|Text|テストコードの実装を担う|

[呼び出し例]
OK:Presentation → Usecase
NG:Domain Service → Usecase
NG:Presentation → Infrastructure

#### ディレクトリ構造
|階層|パッケージ|主なコード|
|----|----|----|
|Presentation|com.shou.demo.presentation|Controllerクラス、Formクラス|
|Infrastructure|com.shou.demo.infrastructure|RepositoryImplクラス|
|Usecase|com.shou.demo.usecase|Usecaseクラス|
|Domain|com.shou.demo.domain|Repositoryインターフェース、Modelクラス|

AOP でパッケージ指定した設計を行うため、パッケージ構成は厳守

---

## 3.実装順序

 1. 検索系機能（一覧取得、詳細取得）の API 実装
 2. 更新系機能（登録、更新、削除）の API 実装
 3. Spring Security を用いたユーザー認証、認可機構の実装
 4. 貸出、返却機能の API 実装
 5. Spring AOP でログの出力
 6. JUnit で単体テストを実装