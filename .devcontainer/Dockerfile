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

# demo-gradle-cache ボリュームを /home/vscode/.gradle にマウントした際、
# ディレクトリが存在しないと root 所有で作成され vscode から書き込めなくなるため、
# 事前に作成して所有者を vscode にしておく。
RUN mkdir -p /home/vscode/.gradle && chown -R vscode:vscode /home/vscode/.gradle