# 1.8.9 高版本物品支持指南

本文说明如何在 1.8.9 客户端中适配 ViaVersion/ViaBackwards 提供的高版本物品。方块在世界中的状态、碰撞、放置和挖掘规则见 [`HIGH_VERSION_BLOCK_SUPPORT.md`](HIGH_VERSION_BLOCK_SUPPORT.md)；这里负责物品身份、模型、手持、使用状态和特殊渲染。

## 1. 先判断物品类型

新增前先把物品分到正确路径：

| 类型 | 例子 | 主要实现 |
| --- | --- | --- |
| 普通 2D 物品 | 图腾、弩的图标 | `builtin/generated`、`items/...` 贴图 |
| 普通 3D 方块物品 | 营火、切石机、砂轮、讲台 | item JSON 引用 `block/...` |
| 多状态 baked model | 弓、弩 | 根据使用时间切换多个 `ModelResourceLocation` |
| special/entity 模型 | 盾牌、三叉戟 | `builtin/entity` 或独立几何 renderer |
| 动画或专用屏幕效果 | 图腾激活、望远镜 | `ItemRenderer`/`EntityRenderer` 专用流程 |

不能因为某个物品在物品栏里是 3D，就把它当成 3D 方块。盾牌和三叉戟有自己的实体几何、状态与左右手变换，不能套用 `ModernBlock` 的 GUI 修复。

## 2. ViaBackwards 物品身份识别

入口是：

```text
src/main/java/cn/unfair/util/via/ViaBackwardsItemModels.java
```

`ViaBackwardsItemModels` 会依次读取各版本 mapping data，并结合：

- Via backup NBT tag。
- 源版本 item ID。
- CustomModelData。
- 自动生成的显示名。
- 必要的兼容特判。

返回稳定的现代模型名：

```java
String modelName = ViaBackwardsItemModels.getModelName(stack);
```

新增物品时先添加：

```text
src/main/resources/assets/minecraft/models/item/<name>.json
```

资源存在后 mapping data 才会把它加入 `MODEL_NAMES`。自动识别失败时再检查 stack 的实际 NBT 和来源协议；显示名匹配只能作为最后的兼容兜底，不能作为主要身份来源。

模型名必须稳定且无状态。例如盾牌始终识别为 `shield`，是否正在格挡由渲染时的 `using item` 状态决定，不应把物品身份改成 `shield_blocking`。

## 3. 普通 2D 物品

普通 2D 物品使用 1.8.9 generated item 模型：

```json
{
  "parent": "builtin/generated",
  "textures": {
    "layer0": "items/example_item"
  }
}
```

贴图放在：

```text
src/main/resources/assets/minecraft/textures/items/example_item.png
```

注意本项目使用旧版目录名 `textures/items`，不是现代资源包的 `textures/item`。动画贴图还要复制同名 `.png.mcmeta`。

如果物品需要旧版手持姿态，可以在 item JSON 中使用 1.8 支持的 `thirdperson` 和 `firstperson`。不要直接复制现代的 `thirdperson_righthand`、`firstperson_lefthand` 等字段，1.8.9 解析器只识别旧字段。

## 4. 3D 方块物品

item JSON 引用 block 模型：

```json
{
  "parent": "block/example_block",
  "display": {
    "thirdperson": {
      "rotation": [10, -45, 170],
      "translation": [0, 1.5, -2.75],
      "scale": [0.375, 0.375, 0.375]
    }
  }
}
```

世界 block 模型应尽量展开成自包含 JSON，不要让自定义 `elements` 继续继承现代 `block/block` 的 `display.gui`。否则 1.8.9 的 `RenderItem.setupGuiTransform()` 会与现代 GUI 变换叠加，造成库存模型斜着且过小。

当前还有统一兜底：

- `ViaBackwardsItemModels` 读取 item JSON 的顶层 `parent`，把 `block/...` 模型标记为方块物品。
- `ItemModelMesher` 对这些 Via 方块物品和本地 `ModernBlock`，在 baked model 为 3D 时清除继承来的 GUI transform。
- `RenderItem.registerItems()` 自动遍历注册表，为全部 `ModernBlock` 注册 `<registry_name>#inventory`。

因此已有和以后新增的高版本 3D 方块都走同一规则，不需要维护名称白名单。资源仍应采用自包含格式；运行时兜底只修正库存 GUI 变换，不负责修复错误贴图、父模型和世界渲染。

## 5. 多状态 baked model

1.8.9 不理解现代 `assets/minecraft/items/<name>.json` 的 condition/range dispatch。需要在代码中读取使用状态并切换已加载的模型。

典型路径：

```text
ModelBakery.loadItemModels()
RenderItem.renderItemModelForEntity()
ItemRenderer.renderItemInFirstPerson()
```

以弩为例，应准备基础、拉弦阶段和装填结果模型，然后按使用 tick 选择：

```java
if (useTicks >= 18) {
    location = new ModelResourceLocation("crossbow_pulling_2", "inventory");
} else if (useTicks > 13) {
    location = new ModelResourceLocation("crossbow_pulling_1", "inventory");
}
```

所有状态模型都必须被 `ModelBakery` 收集，否则运行时切换会得到 missing model。

## 6. special/entity 物品模型

盾牌、三叉戟等物品的真实几何不是普通 block/item `elements`。应使用：

1. 一个 `builtin/entity` base model，保存各视角 transform。
2. 一个专用 renderer，绘制真实几何和实体贴图。
3. 状态选择逻辑，在使用时切换另一个 base model。

不要为了省事把这类物品转成普通 2D 图标。这样物品栏可能勉强可见，但第一人称、第三人称、格挡状态和副手都会错误。

### 6.1 盾牌参考实现

1.21.11 的盾牌定义使用 `using_item` 条件：

```json
{
  "model": {
    "type": "minecraft:condition",
    "property": "minecraft:using_item",
    "on_false": {
      "type": "minecraft:special",
      "base": "minecraft:item/shield",
      "model": { "type": "minecraft:shield" }
    },
    "on_true": {
      "type": "minecraft:special",
      "base": "minecraft:item/shield_blocking",
      "model": { "type": "minecraft:shield" }
    }
  }
}
```

1.8.9 无法直接解析这个文件，因此本项目等价地实现为：

- `models/item/shield.json`：普通状态的 GUI、地面和展示框变换；不再声明手持变换。
- `models/item/shield_blocking.json`：格挡状态的 GUI 变换；不再声明手持变换。
- `RenderItem.renderItemModelForEntity()`：检测当前使用栈并切换到 `shield_blocking#inventory`。
- `TileEntityItemStackRenderer.ModelShield`：绘制 12×22×1 的盾面和 2×6×6 的手柄。
- `textures/entity/shield_base_nopattern.png`：无图案盾牌纹理。
- `ModernShieldRenderer`：专门负责第一人称和第三人称的左右手、普通/格挡四组矩阵，并调用 `RenderItem.renderBuiltinItemDirect()` 绘制实体模型。
- `ItemRenderer`：保留原版挥手和装备进度，将盾牌手持变换完全交给 `ModernShieldRenderer`，避免 1.8.9 单一 `firstperson/thirdperson` 字段造成左右手互换。
- `LayerHeldItem`：分别挂接右臂和左臂的手部锚点，不再镜像整条右手渲染链。

盾牌手持矩阵必须写在 Java renderer 中，不能重新加回 JSON 的 `firstperson` 或 `thirdperson`。1.8.9 的 JSON 解析器没有左右手字段，而旧版 `RenderItem.renderItem(IBakedModel)` 还会额外执行 0.5 缩放、Y 轴 180° 旋转和中心平移；`renderBuiltinItemDirect()` 只保留现代 special model 所需的 `-0.5` 模型中心平移。新增其他非对称 special/entity 物品时，沿用同样的边界：模型 JSON 只负责 GUI/地面/展示框，左右手和使用状态由代码显式处理。

盾牌专用 renderer 内部只执行与现代实现一致的：

```java
GlStateManager.scale(1.0F, -1.0F, -1.0F);
```

不要再叠加 1.8 剑格挡的 `doBlockTransformations()`，也不要在第三人称路径额外放大两倍；这些都会与盾牌 base model transform 重复。

1.21.11 的原始参数必须转换成 1.8/1.9 旧模型坐标，不能直接把现代 `*_righthand` 数值改名为 `firstperson`/`thirdperson`。当前实现由 `ModernShieldRenderer` 直接应用四组状态矩阵：

| 状态 | 视角 | rotation | translation | scale |
| --- | --- | --- | --- | --- |
| 普通 | 第三人称 | `[0, 90, 0]` | `[10, 6, -4]` | `[1, 1, 1]` |
| 普通 | 第一人称 | `[0, 180, 5]` | `[-10, 2, -10]` | `[1.25, 1.25, 1.25]` |
| 格挡 | 第三人称 | `[45, 155, 0]` | `[-3.49, 11, -2]` | `[1, 1, 1]` |
| 格挡 | 第一人称 | `[0, 180, -5]` | `[-15, 5, -11]` | `[1.25, 1.25, 1.25]` |

左手不是简单复制右手数值。当前实现使用 1.21.11 的 left-handed 规则，在应用变换时反转 X 平移、Y/Z 旋转方向，并单独挂接左臂锚点：

| 状态 | 视角 | rotation | translation | scale |
| --- | --- | --- | --- | --- |
| 普通 | 第三人称左手 | `[0, -90, 0]` | `[-10, 6, 12]` | `[1, 1, 1]` |
| 普通 | 第一人称左手 | `[0, -180, -5]` | `[-10, 0, -10]` | `[1.25, 1.25, 1.25]` |
| 格挡 | 第三人称左手 | `[45, -155, 0]` | `[-11.51, 7, 2.5]` | `[1, 1, 1]` |
| 格挡 | 第一人称左手 | `[0, -180, 5]` | `[-5, 5, -11]` | `[1.25, 1.25, 1.25]` |

1.8.9 的 `ItemCameraTransforms` 没有左右手独立字段。兼容实现不应依赖 JSON 的 `firstperson`/`thirdperson` 表达盾牌手持，而应由 `ModernShieldRenderer.renderFirstPerson()` 和 `renderThirdPerson()` 显式传入 `leftHand`。`LayerHeldItem` 必须分别使用右臂、左臂锚点；不要镜像整条右手渲染链，也不要把现代 `firstperson_righthand` 的负 X translation 直接写进旧版 `firstperson`，否则会让右手盾牌出现在左侧并导致左右手对调。

### 6.2 盾牌物品栏 / GUI 渲染

物品栏不是手持渲染的缩小版。1.21.11 的 GUI 路径先建立一个以格子中心为原点的 `16 x (-16) x 16` 槽位坐标，再应用模型 JSON 的 `gui` 变换，最后调用 special model renderer。1.8.9 原版 GUI 路径对所有 `isGui3d()` 模型都会额外执行：

```text
0.5 缩放 -> X 210° -> Y -135° -> JSON GUI transform
```

这会让盾牌再被缩放和旋转一次，表现为物品栏中斜着、过小或中心偏移。因此盾牌必须在 `RenderItem.renderItemIntoGUI()` 中走专用分支：

1. 通过 `ViaBackwardsItemModels.getModelName(stack)` 判断 `shield` 或 `shield_blocking`。
2. 使用 `setupModernGuiTransform(x, y)`：平移到格子中心，应用 `scale(16, -16, 16)`，关闭旧版 GUI 光照旋转。
3. 从当前 baked model 读取 `ItemCameraTransforms.TransformType.GUI`，按现代 `rotationXYZ` 顺序应用 X、Y、Z 旋转、平移和缩放。
4. 调用 `renderBuiltinItemDirect(stack)`，跳过旧版 builtin/entity 的 0.5 缩放和 Y 轴 180° 旋转，只保留 special model 所需的 `translate(-0.5, -0.5, -0.5)`。

对应代码位置：

```text
src/main/java/net/minecraft/client/renderer/entity/RenderItem.java
  renderItemIntoGUI()
  setupModernGuiTransform()
  applyModernGuiTransform()
  renderBuiltinItemDirect()
src/main/java/net/minecraft/client/renderer/tileentity/TileEntityItemStackRenderer.java
  renderByItem()
  renderShield()
```

普通状态和格挡状态可以各自保留 `gui` JSON 变换，例如 `shield.json` 与 `shield_blocking.json`；手持字段不要重新添加。GUI 中使用状态的模型切换仍由 `renderItemModelForEntity()` 选择 `shield_blocking#inventory`，物品栏本身不应自行推断左右手。

### 6.3 special/entity 物品的通用实现边界

新增高版本特殊手持模型时，按以下责任分层：

| 层 | 负责内容 | 不应负责 |
| --- | --- | --- |
| `ViaBackwardsItemModels` | 稳定身份名、来源协议和 Via NBT | 当前是否使用、左右手矩阵 |
| item JSON | `gui`、`ground`、`fixed` 等模型显示变换 | 1.8.9 不支持的左右手字段、实体动画 |
| `RenderItem` | GUI 基础槽位、状态模型选择、special model 入口 | 手臂骨骼和使用动画 |
| 专用 renderer | 几何、纹理、第一/第三人称、左右手、使用状态 | 普通方块 GUI 兜底 |
| `LayerHeldItem` / `ItemRenderer` | 左右臂锚点、装备进度、swing 和事件隔离 | 修改服务器协议状态 |

三叉戟、旗帜、头颅等也应沿用这个边界：先让模型 JSON 只描述非手持视角，再为实体几何提供独立 renderer。若 renderer 需要使用图案或 NBT，应在 `renderByItem(ItemStack)` 中读取 stack，而不是把每种状态复制成大量身份模型名。

## 7. 交互、使用动作和副手

模型正常不代表物品可用。新增物品还要核对：

- 客户端发送的 use item/use on block 包是否符合目标协议。
- `getItemUseAction()` 的等价动作，例如盾牌是 `BLOCK`、弩拉弦可复用 `BOW` 动画。
- 主手和 Via 模拟副手的 active stack 是否正确。
- 使用期间模型状态是否切换，停止使用后是否恢复。
- swing 是否只触发一次。
- 第三人称其他玩家是否依据其 active stack 显示状态。

相关实现集中在：

```text
src/main/java/cn/unfair/util/via/ModernOffhandInteraction.java
src/main/java/net/minecraft/client/multiplayer/PlayerControllerMP.java
src/main/java/net/minecraft/client/renderer/ItemRenderer.java
src/main/java/net/minecraft/client/renderer/entity/RenderItem.java
src/main/java/net/minecraft/client/renderer/entity/layers/LayerHeldItem.java
```

## 8. 从零新增高版本物品

推荐按以下顺序实现：

1. 确定 registry name、首次加入版本、Via fallback item 和实际 stack NBT。
2. 判断它属于 2D、3D block、多状态 baked model 还是 special/entity model。
3. 添加 1.8.9 可读的 item model 和 `textures/items`/`textures/entity` 资源。
4. 验证 `ViaBackwardsItemModels.getModelName(stack)` 返回稳定模型名。
5. 确保 `ModelBakery` 收集所有基础和状态模型。
6. 普通 3D 方块复用统一 GUI 修复；special item 编写独立 renderer。
7. 在第一人称、第三人称、GUI、掉落物、物品展示框和副手中分别验证。
8. 实现使用动作、状态切换和协议发包。
9. 在全部声称支持的协议版本回归，而不是只测试单一版本。

## 9. 验证清单

- [ ] `getModelName(stack)` 返回正确且稳定的现代名称。
- [ ] 基础模型与所有使用状态模型都已加载，无 missing model。
- [ ] 2D 物品使用 `textures/items` 路径，动画 `.mcmeta` 已打包。
- [ ] 3D 方块在 GUI 中大小和朝向正常，没有现代 GUI transform 叠加。
- [ ] special item 使用真实实体几何和实体纹理，没有错误套用方块规则。
- [ ] 第一人称普通、使用中、停止使用三个状态正确。
- [ ] 第三人称自己和其他玩家的模型状态正确。
- [ ] 主手、副手及左右手变换正确，没有用简单镜像代替原版参数。
- [ ] 地面掉落物、物品展示框和 GUI 均正常。
- [ ] 使用包、消耗、冷却、耐久、swing 和服务端确认符合目标版本。
- [ ] 资源重载、切换世界和重连后模型仍然正确。
