# 更新日志

[**English**](CHANGELOG.md)

## 未发布

### 变更

- 使用 NeoForged 的 Forge 1.20.1 ModDevGradle Legacy MDK 结构替换原 ForgeGradle 项目。
- 将上游 NeoForge 实现移植到标准 `src/main` 源集。
- 将 AE2 声明为必需依赖，并明确固定 Forge 版 AE2 构件。
- 将 NeoForge 的通信、注册、事件、能力、物品数据组件和渲染调用替换为 Forge 1.20.1 API。
- 删除附加模组反射与猜测式兼容，改用直接 AE2 API。
- 更新构建与发布自动化以适配重写后的 Forge 构件。

### 修复

- 在物品 NBT 中保留网络定位器全部九个过滤位置，包括中间的空槽。
- 修正 Minecraft 1.20.1 的配方目录及结果字段。
- 在开发和运行时冒烟测试环境中加入 AE2 所需的 GuideME。
- 防止无限期运行时高亮导致剩余 tick 数据溢出。
