@echo off
chcp 65001 >nul
echo ========================================
echo   GitHub 一键上传工具
echo ========================================
echo.

REM 检查git是否安装
git --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到Git，请先安装Git
    echo 下载地址: https://git-scm.com/downloads
    pause
    exit /b 1
)

echo 请输入你的GitHub用户名:
set /p USERNAME=

echo.
echo 请输入仓库名称 (默认: CardCounter):
set /p REPO=
if "%REPO%"=="" set REPO=CardCounter

echo.
echo ========================================
echo   正在初始化Git仓库...
echo ========================================

cd /d "%~dp0"

git init
echo.

echo [1/4] 添加文件...
git add .

echo.
echo [2/4] 提交文件...
git commit -m "Initial commit: CardCounter App"

echo.
echo [3/4] 添加远程仓库...
git remote add origin https://github.com/%USERNAME%/%REPO%.git

echo.
echo ========================================
echo   [4/4] 准备上传到GitHub
echo ========================================
echo.
echo 接下来需要你手动操作:
echo.
echo 1. 先在GitHub创建仓库: https://github.com/new
echo    仓库名: %REPO%
echo    设为 Public (公开)
echo.
echo 2. 创建完成后，按任意键继续上传...
pause >nul

echo.
echo 正在上传...
git push -u origin main

if errorlevel 1 (
    echo.
    echo 上传失败！可能的原因:
    echo 1. 仓库尚未在GitHub创建
    echo 2. 需要登录GitHub (输入用户名和Token)
    echo.
    echo 请重试，或手动执行:
    echo git push -u origin main
) else (
    echo.
    echo ========================================
    echo   上传成功！
    echo ========================================
    echo.
    echo 下一步:
    echo 1. 访问: https://github.com/%USERNAME%/%REPO%/actions
    echo 2. 点击 "Build APK" → "Run workflow"
    echo 3. 等待3-5分钟后下载APK
    echo.
)

pause
