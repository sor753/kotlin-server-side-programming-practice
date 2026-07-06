FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

# ベースイメージのデフォルトユーザーが vscode の場合 apt-get が権限エラーになるため root に切り替える
USER root

# mysql コンテナへ接続確認するための CLI クライアント
# ベースイメージ同梱の yarn リポジトリが GPG検証エラーで apt-get update を失敗させるため、
# 今回の作業に不要なそのリポジトリ定義を先に削除しておく。
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update && apt-get install -y --no-install-recommends default-mysql-client \
    && rm -rf /var/lib/apt/lists/*

USER vscode

# ktlint公式配布の単体実行可能jarを導入する（Maven Central上の分割モジュール(ktlint-rule-engine-core等)を
# Gradle経由で組み合わせるとバージョン番号が同じでもABIが一致せずNoSuchFieldErrorになるため、公式の単体jarを使う）
RUN mkdir -p /home/vscode/.local/bin \
    && curl -sSL -o /home/vscode/.local/bin/ktlint https://github.com/pinterest/ktlint/releases/download/1.3.1/ktlint \
    && chmod a+x /home/vscode/.local/bin/ktlint
ENV PATH="/home/vscode/.local/bin:${PATH}"

# detekt公式配布の単体実行可能jarを導入する。detektのGradleプラグイン経由だと、プロジェクトの
# Kotlinバージョン(2.3.21)がGradleの依存関係解決でdetekt内部のkotlin-compiler-embeddableに
# 波及し「detekt was compiled with Kotlin 2.0.10 but is currently running with 2.3.21」という
# 非互換エラーになるため、依存関係が完全に分離された単体jarを直接実行する
RUN mkdir -p /home/vscode/.local/lib \
    && curl -sSL -o /home/vscode/.local/lib/detekt-cli.jar https://github.com/detekt/detekt/releases/download/v1.23.7/detekt-cli-1.23.7-all.jar \
    && printf '#!/bin/sh\nexec java -jar /home/vscode/.local/lib/detekt-cli.jar "$@"\n' > /home/vscode/.local/bin/detekt \
    && chmod a+x /home/vscode/.local/bin/detekt

# demo-gradle-cache ボリュームを /home/vscode/.gradle にマウントした際、
# ディレクトリが存在しないと root 所有で作成され vscode から書き込めなくなるため、
# 事前に作成して所有者を vscode にしておく。
RUN mkdir -p /home/vscode/.gradle && chown -R vscode:vscode /home/vscode/.gradle