@echo off
chcp 65001 >nul
echo =======================================
echo   外榜数据爬虫自动启动脚本 (Windows)
echo =======================================

cd /d "%~dp0"

if not exist "venv\Scripts\activate.bat" (
    echo [1/4] 未检测到虚拟环境，正在创建 venv...
    python -m venv venv
) else (
    echo [1/4] 已发现虚拟环境 venv。
)

echo [2/4] 正在激活虚拟环境...
call venv\Scripts\activate

echo [3/4] 正在检查并安装依赖...
python -m pip install -r requirements.txt
if errorlevel 1 exit /b 1

echo [4/4] 正在启动爬虫程序...
python board_spider.py

pause
