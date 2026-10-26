#!/usr/bin/env bash
set -euo pipefail

echo "======================================="
echo "  外榜数据爬虫自动启动脚本 (Linux/Mac)"
echo "======================================="

cd "$(dirname "$0")"

if [ ! -d "venv" ]; then
    echo "[1/4] 未检测到虚拟环境，正在创建 venv..."
    python3 -m venv venv
else
    echo "[1/4] 已发现虚拟环境 venv。"
fi

echo "[2/4] 正在激活虚拟环境..."
source venv/bin/activate

echo "[3/4] 正在检查并安装依赖..."
python3 -m pip install -r requirements.txt

echo "[4/4] 正在启动爬虫程序..."
python3 board_spider.py
