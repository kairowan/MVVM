@echo off
REM 双击可执行：进入项目根目录调用脚本

cd /d %~dp0
cd ..
bash scripts/build_release.sh
pause
