# Project Release Rule

每次完成本项目的功能更新、缺陷修复或数据模型调整后，默认执行完整发布流程：

1. 清理旧构建目录、旧安装包和过期临时产物，不删除业务数据库、备份、源码或运行工具；
2. 运行后端全量测试、前端 TypeScript 检查和生产构建；
3. 更新版本号，并构建网页登录版、Windows 安装包、Windows 绿色版和 Android APK；
4. 上传到 GitHub 仓库 `wang66676/ivs-inventory-system`，创建最新语义化版本 Release；
5. 用最新 Windows 安装包替换桌面文件 `C:\Users\Administrator\Desktop\容大进销存系统.exe`；
6. 验证 `https://wang66676.github.io/ivs-inventory-system/`、最新 Release、在线 API 和桌面安装包。

发布前必须再次向用户确认目标仓库和可见性；不得上传业务数据库、备份、日志、密钥或本地运行数据。
