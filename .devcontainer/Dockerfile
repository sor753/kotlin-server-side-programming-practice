FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

# demo-gradle-cache ボリュームを /home/vscode/.gradle にマウントした際、
# ディレクトリが存在しないと root 所有で作成され vscode から書き込めなくなるため、
# 事前に作成して所有者を vscode にしておく。
RUN mkdir -p /home/vscode/.gradle && chown -R vscode:vscode /home/vscode/.gradle