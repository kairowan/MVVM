#!/bin/bash
# 双击可执行：进入项目根目录调用脚本

cd "$(dirname "$0")/.."
bash scripts/build_release.sh
