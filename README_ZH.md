# AE Crafting Tracker — Forge 1.20.1

[**English**](README.md)

这是 Chatterjay 的 AE Crafting Tracker 的非官方 Minecraft Forge 1.20.1 移植版。本仓库维护 Forge 实现；原始 NeoForge 实现位于[上游项目](https://github.com/Chatterjay/AE-Crafting-Tracker)。

## 功能

### Pattern Provider 高亮

- 追踪已启用高亮的玩家附近的 AE2 Pattern Provider。
- 使用可配置的绿色、黄色和红色覆盖层表示活跃、卡顿和卡死状态。
- 在 Provider 上方显示最多三个匹配的物品或流体输出图标。
- 可通过 `/crafttracker`、网络定位器界面或 AE2 合成状态界面切换。

### 网络定位器

- 在物品 NBT 中持久保存九个幽灵过滤槽。
- 潜行使用于 AE2 网络方块时绑定对应网络。
- 扫描网络中的方块库存、Pattern Provider、存储总线、输入/输出总线、接口和库存检测器。
- 玩家与绑定位置处于同一维度时高亮匹配位置。
- 支持手动操作幽灵槽；安装 EMI 后支持拖放。

## 依赖

- Minecraft 1.20.1
- Minecraft Forge 47.4.21 或更高版本
- Applied Energistics 2 15.4.8 或更高的 15.x 版本
- GuideME 20.1.x（当前 AE2 15.4.x 的依赖）
- EMI 1.1.24 或更高版本（可选，仅客户端）

## 构建

项目基于 NeoForged 的 Forge 1.20.1 ModDevGradle Legacy MDK，需要 Java 17。

```bash
./gradlew build
```

可发布 jar 位于 `build/libs/`。

## 配置

Forge 会生成 `config/crafting_tracker-common.toml`，用于配置默认高亮状态、扫描间隔和半径、状态阈值、颜色及透明度。

## 兼容策略

AE2 是必需的编译期依赖。本移植版不包含基于反射的兼容层，也不支持缺少 AE2 时强行启动。模组附加兼容应通过稳定、明确声明的 API 与依赖实现。

## 许可证

GNU LGPL 3.0。

### 资源鸣谢

网络定位器贴图基于 AE2 的 `network_tool` 贴图，版权归 Applied Energistics 2 所有。
