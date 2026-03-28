# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Better Furnace 是一个 Minecraft 1.20.4 Fabric 模组，为动力矿车（Furnace Minecart）提供服务端优先的全面改造，包括：
- 列车耦合系统（Train Coupling）
- 制动模式（Braking Mode）
- GUI 燃料槽
- 区块加载（Chunk Loading）
- 传送门组传送（Portal Group Teleport）

## 构建和开发命令

### Windows 环境
```bash
# 构建模组
gradlew.bat build

# 运行客户端（开发测试）
gradlew.bat runClient

# 运行服务端（开发测试）
gradlew.bat runServer

# 生成数据（Data Generation）
gradlew.bat runDatagen

# 清理构建
gradlew.bat clean
```

### Unix/Linux/Mac 环境
```bash
# 构建模组
./gradlew build

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer

# 生成数据
./gradlew runDatagen

# 清理构建
./gradlew clean
```

构建产物位于 `build/libs/` 目录。

## 核心架构

### 1. 列车耦合系统 (Train Coupling)
- **BetterFurnaceTrainManager**: 核心管理器，负责列车链接、跟随路径、碰撞抑制和组传送
- **BetterFurnaceTrainAccess**: Mixin 接口，为 AbstractMinecart 添加链接状态（previousUuid, nextUuid, trackHistory）
- 列车通过 UUID 链接，每个矿车记录前后车辆
- 使用历史轨迹点（140 个点）实现平滑跟随
- 最多支持 4 节车厢的列车
- 碰撞时自动耦合（需要至少一节动力矿车）

### 2. 工作模式系统
- **BetterFurnaceMinecartMode**: 枚举类型，定义两种模式
  - ACCELERATE: 原版加速模式
  - DECELERATE: 反向加速（制动）模式
- **BetterFurnaceMinecartAccess**: Mixin 接口，为 MinecartFurnace 添加模式状态

### 3. 区块加载系统
- **BetterFurnaceChunkLoadingManager**: 管理燃烧态动力矿车的 3x3 区块强制加载
- 燃烧时加载周围 3x3 区块
- 熄火后延迟 15 秒释放区块
- 使用引用计数避免重复加载/卸载
- 支持跨维度切换

### 4. 网络通信
- **BetterFurnaceNetworking**: 服务端网络握手管理
- 客户端发送 "client_ready" 握手包标识已安装模组
- 服务端根据标记决定是否允许打开自定义 GUI
- 纯服务端环境下仍可正常工作（无 GUI）

### 5. Mixin 架构
- **EntityMixin**: 在实体 tick 时调用列车管理器
- **AbstractMinecartMixin**: 实现碰撞抑制和耦合逻辑，添加列车状态字段
- **MinecartFurnaceMixin**: 添加工作模式、GUI 支持、区块加载和传送门组传送

### 6. 客户端 GUI
- **BetterFurnaceMinecartScreen**: 自定义 GUI 界面
- **BetterFurnaceMinecartMenu**: 服务端菜单容器
- 仅在客户端安装模组时可用

## 代码约定

- 使用 Java 17
- 使用官方 Mojang 映射（Official Mojang Mappings）
- Mixin 方法使用 `betterFurnace$` 前缀避免冲突
- 中文注释用于核心逻辑说明
- 服务端优先设计：所有核心功能在纯服务端环境下可用

## 项目结构

```
src/
├── main/
│   ├── java/better/furnace/
│   │   ├── BetterFurnace.java              # 主入口
│   │   ├── BetterFurnaceNetworking.java    # 网络通信
│   │   ├── BetterFurnaceScreenHandlers.java # GUI 注册
│   │   ├── minecart/                        # 列车系统
│   │   │   ├── BetterFurnaceTrainManager.java
│   │   │   ├── BetterFurnaceMinecartMode.java
│   │   │   ├── BetterFurnaceMinecartAccess.java
│   │   │   └── BetterFurnaceTrainAccess.java
│   │   ├── chunk/                           # 区块加载
│   │   │   └── BetterFurnaceChunkLoadingManager.java
│   │   ├── menu/                            # GUI 菜单
│   │   │   └── BetterFurnaceMinecartMenu.java
│   │   └── mixin/                           # Mixin 实现
│   │       ├── EntityMixin.java
│   │       ├── AbstractMinecartMixin.java
│   │       └── MinecartFurnaceMixin.java
│   └── resources/
│       ├── fabric.mod.json
│       ├── better-furnace.mixins.json
│       └── assets/better-furnace/
│           └── lang/                        # 多语言支持
│               ├── en_us.json
│               └── zh_cn.json
└── client/
    ├── java/better/furnace/
    │   ├── BetterFurnaceClient.java         # 客户端入口
    │   ├── BetterFurnaceDataGenerator.java  # 数据生成
    │   └── client/gui/
    │       └── BetterFurnaceMinecartScreen.java
    └── resources/
        └── better-furnace.client.mixins.json
```

## 关键实现细节

### 列车跟随算法
- 使用历史轨迹点队列（MAX_HISTORY_POINTS = 140）
- 跟随间距 1.0 方块（FOLLOW_SPACING）
- 路径采样使用切线方向和横向误差修正
- 速度平滑插值（0.36 系数）
- 最小链接距离保护（0.8 方块）

### 碰撞抑制规则
- 同一列车的矿车之间忽略碰撞
- 直接链接的相邻矿车在距离过近时仍允许碰撞以防止重叠
- 使用 BFS 算法判断是否属于同一列车（MAX_TRAIN_SCAN = 128）

### 区块加载策略
- 3x3 区块范围（RADIUS = 1）
- 延迟释放 15 秒（RELEASE_DELAY_TICKS = 15 * 20）
- 引用计数管理，支持多个矿车加载同一区块
- 维度切换时自动释放旧维度区块

### 传送门组传送
- 动力矿车通过传送门时，整个列车一起传送
- 使用 ThreadLocal 标记防止递归传送
- 保持列车顺序和链接关系
