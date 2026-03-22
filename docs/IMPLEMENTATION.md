# Better Furnace 实现说明（Fabric 1.20.4）

## 1. 总体设计

本模组采用“服务端主逻辑 + 客户端可选 GUI”的结构：

- 服务端未安装客户端模组时：动力矿车仍可正常加燃料、运行、链接、传送、区块加载。
- 客户端安装模组时：额外支持 `Shift + 右键` 打开自定义燃料 GUI。
- 客户端未安装时：`Shift + 右键` 自动回退为普通加燃料，不影响核心玩法。

核心技术手段：

1. **Mixin 注入原版矿车行为**
2. **把动力矿车扩展为容器（`Container + WorldlyContainer`）**
3. **用管理器分离“列车链接逻辑”和“区块强制加载逻辑”**
4. **通过实体 NBT 持久化关键状态**

---

## 2. 关键功能对应实现

### 2.1 列车链接与防脱轨

- 在矿车碰撞阶段尝试建立链接（要求至少一组包含动力矿车）。
- 已链接矿车之间忽略碰撞，避免弯道互推导致脱轨。
- 后车每 tick 跟随前车历史轨迹点（轨迹采样 + 速度修正 + 远距纠偏）。
- 链接距离超阈值自动断开，防止无效“拉链”。

### 2.2 传送门整组传送

- 在实体 `changeDimension` 入口挂钩。
- 仅当触发者是 `MinecartFurnace` 时，调用列车管理器传送同组矿车。
- 使用传送中的标记避免递归触发。

### 2.3 燃料系统与两种模式

- 动力矿车新增 1 个燃料槽，燃料规则与原版熔炉一致（`AbstractFurnaceBlockEntity.isFuel/getFuel`）。
- 模式：
  - `ACCELERATE`：常规加速，燃烧推进。
  - `DECELERATE`：反向加速；速度归零后在坡道进入制动状态，并以半速燃烧抑制溜车。
- 激活铁轨规则：
  - 进入“红石供能激活铁轨”时切换模式。
  - 减速模式离开该轨后自动恢复加速模式（满足“持续激活维持减速模式”）。

### 2.4 动力铁轨速度上限叠加

- 覆盖 `getMaxSpeed`：
  - 基础动力矿车上限 + 动力铁轨上限（叠加）。

### 2.5 GUI 与可选客户端

- 服务端注册自定义菜单类型。
- 客户端通过握手包上报“已安装模组”。
- 服务端仅对已握手玩家开放 GUI；否则回退普通右键加燃料。
- GUI 中展示单燃料槽与火焰进度，槽内物品和燃烧状态与服务端同步。

### 2.6 漏斗与比较器兼容

- 动力矿车实现 `Container/WorldlyContainer` 后可被漏斗逻辑识别为容器实体。
- 燃料槽允许自动化侧面注入燃料。
- 作为容器实体可参与原版容器红石读数链路（如配合铁轨检测）。

### 2.7 区块加载

- 动力矿车处于燃烧态时，强制加载其所在与周边共 `3x3` 区块。
- 停止燃烧后延迟 `15s`（`300 tick`）释放加载。
- 采用“区块引用计数”避免多车重叠加载时误释放。

### 2.8 数据持久化

持久化到实体 NBT 的关键数据：

- 工作模式（加速/减速）
- 制动状态
- 燃料槽物品
- 当前燃烧总时长（用于 GUI 火焰比例）
- 半速燃烧计数器
- 链接关系（由列车数据接口 + 实体保存机制共同保持 UUID 链接）

---

## 3. 文件用途说明

### 3.1 入口与注册

- `src/main/java/better/furnace/BetterFurnace.java`
  - 服务端入口，注册菜单、网络握手、区块加载生命周期。

- `src/main/java/better/furnace/BetterFurnaceNetworking.java`
  - 客户端握手状态管理（记录哪些玩家可开 GUI）。

- `src/main/java/better/furnace/BetterFurnaceScreenHandlers.java`
  - 自定义菜单类型注册（扩展菜单，支持写入实体 id）。

### 3.2 列车与矿车逻辑

- `src/main/java/better/furnace/minecart/BetterFurnaceMinecartMode.java`
  - 模式枚举（加速/减速）与切换工具。

- `src/main/java/better/furnace/minecart/BetterFurnaceMinecartAccess.java`
  - 动力矿车扩展状态访问接口（燃料槽、模式、燃烧参数、GUI 数据）。

- `src/main/java/better/furnace/minecart/BetterFurnaceTrainAccess.java`
  - 所有矿车共享的链接状态接口（前后 UUID、轨迹历史、冷却）。

- `src/main/java/better/furnace/minecart/BetterFurnaceTrainManager.java`
  - 列车核心管理器：
    - 接触链接
    - 同组碰撞忽略
    - 轨迹跟随
    - 链接校验/断开
    - 整组跨维度传送

### 3.3 区块加载

- `src/main/java/better/furnace/chunk/BetterFurnaceChunkLoadingManager.java`
  - 3x3 区块强制加载、15 秒延迟释放、引用计数与缺失实体清理。

### 3.4 GUI

- `src/main/java/better/furnace/menu/BetterFurnaceMinecartMenu.java`
  - 服务端/客户端菜单同步逻辑，单槽燃料交互与快捷移动。

- `src/client/java/better/furnace/client/gui/BetterFurnaceMinecartScreen.java`
  - 客户端界面渲染（背景、火焰进度、模式/制动文案）。

- `src/client/java/better/furnace/BetterFurnaceClient.java`
  - 客户端入口：注册屏幕，入服发送握手包。

### 3.5 Mixins

- `src/main/java/better/furnace/mixin/AbstractMinecartMixin.java`
  - 给所有矿车注入链接状态字段与碰撞/链接钩子。

- `src/main/java/better/furnace/mixin/MinecartFurnaceMixin.java`
  - 动力矿车主改造：
    - 燃料槽容器化
    - 交互与 GUI
    - 模式切换与制动
    - 速度上限与推进行为
    - 持久化

- `src/main/java/better/furnace/mixin/EntityMixin.java`
  - 处理继承方法钩子（`remove` / `changeDimension`）：
    - 删除时清理加载与链接
    - 动力矿车跨维度时触发整组传送

### 3.6 资源与配置

- `src/main/resources/better-furnace.mixins.json`
  - 主 mixin 配置清单。

- `src/client/resources/better-furnace.client.mixins.json`
  - 客户端 mixin 配置（当前保留空列表）。

- `src/main/resources/fabric.mod.json`
  - 模组元信息与入口声明。

- `src/main/resources/assets/better-furnace/lang/en_us.json`
- `src/main/resources/assets/better-furnace/lang/zh_cn.json`
  - GUI 与状态文本本地化。

- `src/main/resources/assets/better-furnace/textures/gui/container/furnace.png`
  - GUI 背景纹理（你可替换为自定义图）。

### 3.7 过程文档

- `TODO.md`
  - 本次一次性实现的任务清单与状态。

- `docs/IMPLEMENTATION.md`（本文件）
  - 实现原理与文件用途全量说明。

---

## 4. 已完成验证

已执行并通过：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
.\gradlew.bat build
```

构建结果：`BUILD SUCCESSFUL`
