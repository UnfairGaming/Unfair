# 1.8.9 高版本方块支持指南

本文只负责方块注册、协议状态、世界模型、碰撞、放置同步和挖掘规则。纯物品、物品模型、使用状态、特殊实体模型与副手渲染请参考 [`HIGH_VERSION_ITEM_SUPPORT.md`](HIGH_VERSION_ITEM_SUPPORT.md)。方块对应的 `ItemBlock` 同时受两份文档约束。

本文说明两类跨版本方块的处理方式：

- 1.8.9 已有、但高版本修改了模型或碰撞的方块，例如 `farmland` 和 `anvil`。
- 1.8.9 完全不存在、ViaBackwards 会回退成其他旧方块的方块，例如 `dirt_path` 和 `campfire`。

这两类情况必须分开处理。只有第二类方块需要新增本地方块类并继承 `ModernBlock`；第一类方块直接修改原有方块类和资源即可。

## 1. 先确定方块的协议行为

先在 `D:\desktop\Grim-2.0` 中查目标方块的碰撞规则，按目标协议版本分支实现，不要直接照搬当前版本碰撞箱。

重点核对：

- 方块是否完整碰撞、半方块碰撞或无碰撞。
- 点燃/熄灭、湿润/干燥等状态是否改变碰撞。
- 玩家放置时服务端实际发送什么替代方块。
- 1.8.9 客户端是否需要本地虚拟方块保存真实状态。

首先检查 1.8.9 是否已经注册了这个方块，并确认 ViaBackwards 转换后是否仍然保留同一个方块身份：

- 身份仍然存在：不要新建方块类，也不要继承 `ModernBlock`。直接像 `BlockAnvil`、`BlockFarmland` 一样，在原类中按目标协议修改模型、边界或碰撞。
- 身份已经丢失：新增 `ModernBlock`，让 `ModernBlockStateTracker` 在 ViaBackwards fallback 前保存真实状态，并在进入 world 或收到方块更新时恢复本地模型。

推荐把协议判断集中放在 `ViaProtocol`，方块类只负责调用：

```java
if (ViaProtocol.olderThanOrEqualsTo1_13_2()) {
    // 目标协议的旧碰撞规则
} else {
    // 新协议的碰撞规则
}
```

## 2. 判断是否需要 ModernBlock

### 2.1 1.8.9 已有的方块

如果 1.8.9 原生已经有该方块，高版本只是修改了模型、渲染边界或碰撞箱，不要新建另一个 block，也不要让原类继承 `ModernBlock`。

参考：

- `src/main/java/net/minecraft/block/BlockAnvil.java`
- `src/main/java/net/minecraft/block/BlockFarmland.java`

继续使用原生注册项，在原方块类中通过 `ViaProtocol` 选择目标版本行为。例如耕地在 1.8.9 使用完整方块高度的碰撞，从 1.10 开始使用 15/16 高度：

```java
public AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state) {
    double maxY = ViaProtocol.newerThanOrEqualTo1_10() ? 0.9375D : 1.0D;
    return new AxisAlignedBB(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0D, pos.getY() + maxY, pos.getZ() + 1.0D);
}
```

模型变化则修改原方块对应的 blockstate、block model 或渲染选择逻辑，不要为了换模型复制一个方块类或占用新的本地方块 ID。必须保证模型边界、选择框和实体碰撞使用相同版本规则。

### 2.2 1.8.9 不存在的方块

只有当 1.8.9 没有该方块，并且 ViaBackwards 会将其 fallback 成另一个旧方块时，才新增本地方块类并继承 `ModernBlock`。

参考：

- `src/main/java/net/minecraft/block/BlockDirtPath.java`
- `src/main/java/net/minecraft/block/BlockCampfire.java`
- `src/main/java/net/minecraft/block/ModernBlock.java`
- `src/main/java/net/minecraft/block/ModernBlockDirectional.java`

普通方块继承 `ModernBlock`；需要水平朝向属性的方块继承 `ModernBlockDirectional`。每个现代方块自行声明 Via 1.14 状态 ID 范围并负责解码：

```java
public int getViaStateIdMin() {
    return FIRST_STATE_ID;
}

public int getViaStateIdMax() {
    return LAST_STATE_ID;
}

public IBlockState getStateFromViaStateId(int stateId) {
    return this.getDefaultState();
}
```

方块注册完成后，`ModernBlockStateTracker` 会自动扫描所有 `ModernBlock`，无需再在 tracker 内添加方块类型判断。若方块还需要维护放置或交互缓存，可覆写 `onModernStateApplied()`。

方块类至少要处理：

- 属性：使用 `PropertyBool`、`PropertyInteger`、`FACING` 等。
- `createBlockState()`。
- `getStateFromMeta()` / `getMetaFromState()`。
- `isOpaqueCube()` 和 `isFullCube()`。
- `getBlockLayer()`，透明贴图通常使用 `CUTOUT`。
- `getCollisionBoundingBox()`。
- `addCollisionBoxesToList()`。
- `setBlockBoundsBasedOnState()`。
- 掉落物和拾取物品。

不要只修改一个固定的 `setBlockBounds`。实体碰撞查询会走 `getCollisionBoundingBox` 和 `addCollisionBoxesToList`，两条路径都要保持一致。

## 3. 注册新增的 Block 和 ItemBlock

本节只适用于 1.8.9 不存在、需要新增 `ModernBlock` 的方块。原生已有方块继续使用原注册项，不要重复注册。

在 `Block.java` 注册方块 ID 和名称，在 `Blocks.java` 增加字段并从注册表取回：

```java
registerBlock(208, "dirt_path", new BlockDirtPath());
registerBlock(209, "campfire", new BlockCampfire());
```

在 `Item.java` 的 `registerItems()` 中注册对应 ItemBlock：

```java
registerItemBlock(Blocks.campfire);
```

没有 ItemBlock 时，世界方块可能可以显示，但物品栏、手持和放置流程不会完整。

## 4. 添加资源文件

### 4.1 Blockstate

路径：

```text
src/main/resources/assets/minecraft/blockstates/<name>.json
```

状态名必须和 Java 属性名一致。例如营火使用：

```json
{
  "variants": {
    "facing=south,lit=true": { "model": "campfire" },
    "facing=south,lit=false": { "model": "campfire_off" }
  }
}
```

每个有效状态都要有对应 variant，否则世界中会回退到缺失模型。

### 4.2 Block 模型

路径：

```text
src/main/resources/assets/minecraft/models/block/<model>.json
```

本项目的 1.8.9 `ModelBlock` 解析器不允许同一个模型同时包含 `parent` 和 `elements`。需要完整几何时使用土径的自包含写法：

```json
{
  "ambientocclusion": false,
  "textures": {
    "particle": "blocks/example",
    "top": "blocks/example_top"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 15, 16],
      "shade": false,
      "faces": {
        "up": { "texture": "#top" }
      }
    }
  ]
}
```

注意：

- 贴图名写 atlas 路径，不带 `.png`。
- 方块贴图使用 `blocks/...`。
- `particle` 必须能解析到实际贴图。
- 细小或透明结构若物品栏过暗，可以使用 `ambientocclusion: false` 和元素级 `shade: false`。
- 非正方形动画贴图必须带正确的 `.mcmeta`。

### 4.3 Item 模型

路径：

```text
src/main/resources/assets/minecraft/models/item/<name>.json
```

如果物品栏要显示方块模型，可以让 Item 模型引用 block 模型：

```json
{
  "parent": "block/dirt_path"
}
```

如果物品栏需要不同亮度、比例或几何，应单独制作一个 `block/<name>_inventory.json`，不要强行复用世界模型。

### 4.1 3D 方块物品不要继承现代 `block/block` 的 GUI 变换

切石机、堆肥桶、砂轮、讲台等自定义 3D 方块应参考营火的模型拆分方式。世界 block 模型必须是自包含模型，不要保留现代资源中的：

```json
{
  "parent": "block/block",
  "elements": []
}
```

本项目的 `models/block/block.json` 带有现代 `display.gui`，其中包含约 `0.625` 的缩放和 `30/225` 度旋转；1.8.9 的 `RenderItem.setupGuiTransform()` 又会对 3D 模型应用一次库存旋转与缩放。两套 GUI 变换叠加后，物品栏中的方块会斜着显示且明显变小。

资源侧推荐处理方式：

1. 把世界模型需要的 `textures` 和 `elements` 展开到 `models/block/<name>.json`。
2. 从世界模型删除 `parent: block/block`，并删除现代 `display.gui`、`firstperson_righthand` 等 1.8.9 不需要的变换。
3. `models/item/<name>.json` 只引用自包含世界模型，并保留营火同款的旧版 `thirdperson` 变换：

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

4. 不要在 item 模型中再添加 `gui` 变换。库存中的标准 3D 方块视角由 1.8.9 的 `setupGuiTransform()` 负责。
5. 在 `ModelBakery.registerVariantNames()` 收集模型；`ModernBlock` 的本地 ItemBlock 会由 `RenderItem.registerItems()` 末尾的注册表遍历自动建立 `<name>#inventory` 映射。

当前切石机、堆肥桶、砂轮和讲台都使用这套自包含结构。为了覆盖所有已有和以后新增的高版本 3D 方块，渲染代码还提供了统一兜底：

- `ViaBackwardsItemModels` 解析每个已发现 item JSON 的顶层 `parent`，缓存所有引用 `block/...` 的物品模型。
- `ItemModelMesher` 对这些 Via 方块物品，以及本地注册的 `ModernBlock` 物品，在 baked model 为 3D 时将 `GUI` transform 重置为默认值。
- `RenderItem.registerItems()` 最后遍历 `Block.blockRegistry`，自动为所有 `ModernBlock` 注册 `<registry_name>#inventory`。

因此末地烛、紫颂花、幽匿系列、垂滴叶、货架、避雷针、链、灯笼、保险库等通过 block parent 加载的 3D 物品也走相同规则，不需要维护一份方块名称白名单。盾牌、三叉戟、望远镜等不以 `block/...` 为 item parent 的特殊 3D 物品不会被当成方块处理。

ViaBackwards 替代物品会由 `ItemModelMesher` 按 `ViaBackwardsItemModels.getModelName()` 直接取得 inventory 模型；本地 `ItemBlock` 则依靠 `RenderItem` 的自动注册。因此验证时两条路径都要覆盖。资源侧仍建议新模型采用自包含格式，因为它同时避免 1.8.9 parent 解析、世界渲染和手持继承方面的问题；渲染兜底只负责消除物品栏的重复 GUI 变换。

对于使用完整 3D block 模型的物品，1.8.9 的 GUI 会自动启用标准物品光照。若物品栏明显比世界和手持更暗，可以在 `RenderItem.renderItemIntoGUI()` 中按物品类型关闭 GUI 光照；不要直接修改世界模型的贴图颜色：

```java
if (isSpecialBlockItem(stack)) {
    GlStateManager.disableLighting();
}
```

这样只影响物品栏绘制，不会改变世界中的方块光照和碰撞。

## 5. 让 ModelBakery 收集模型和贴图

在 `ModelBakery.registerVariantNames()` 中添加 ItemBlock 的模型名：

```java
this.variantNames.put(
    Item.getItemFromBlock(Blocks.campfire),
    Lists.newArrayList("campfire")
);
```

这样物品模型会参与加载，并且模型引用的贴图会进入 blocks atlas。

如果是通过 ViaBackwards 自定义模型名识别，还要在：

```text
src/main/java/cn/unfair/util/via/ViaBackwardsItemModels.java
```

中添加：

- `MODEL_NAMES`。
- 现代物品标识到模型名的判断。
- 必要的显示名或 CustomModelData 识别。

## 6. 注册手持和物品栏模型

非 `ModernBlock` 的特殊 ItemBlock 需要在 `RenderItem.registerItems()` 中增加 inventory 模型映射：

```java
this.registerBlock(Blocks.campfire, "campfire");
```

`ModernBlock` 会在 `registerItems()` 末尾通过注册表遍历自动注册，不应再逐个维护一份重复列表。只添加 JSON、但既没有自动注册资格也没有显式注册的普通 ItemBlock，仍可能显示缺失模型、透明模型或错误模型。

## 7. 处理高版本放置和服务端替换

本节只适用于 ViaVersion/ViaBackwards 会把真实方块身份替换掉的新增方块。对于 `farmland`、`anvil` 这类 1.8.9 已有且 fallback 后身份仍然正确的方块，不需要本地放置 tracker，也不需要 `ModernBlockStateTracker`。

进入 world 或重新加载区块时，`ModernBlockStateTracker` 会在 ViaBackwards fallback 前读取真实状态，并在 1.8.9 区块加载完成后自动恢复所有已注册 `ModernBlock` 的模型和状态。单方块更新与批量方块更新也走同一套恢复逻辑。

参考：

- `src/main/java/cn/unfair/util/via/DirtPathBlockTracker.java`
- `src/main/java/cn/unfair/util/via/CampfireBlockTracker.java`
- `src/main/java/cn/unfair/util/via/RespawnAnchorBlockTracker.java`

推荐流程：

1. 在 `PlayerControllerMP` 拦截对应现代 ItemBlock 的放置。
2. 在本地世界先放入自定义方块。
3. 记录位置、方块类型和状态。
4. 收到服务端 `BlockChange` 或多方块更新时，将 Via 替代方块映射回本地方块。
5. 服务端确认不是该方块时清理记录。
6. 状态变化时只更新属性，不要重复创建方块。

在 `NetHandlerPlayClient` 中接入 tracker 时，要覆盖单方块更新和批量方块更新两条路径。

## 8. 碰撞和渲染测试清单

每新增或适配一个高版本方块，至少测试：

- 世界中放置后模型是否正确。
- 不同朝向是否正确旋转。
- 所有 blockstate 是否都有模型。
- 熄灭、点燃、湿润等状态切换是否正确。
- 物品栏图标是否存在、亮度是否正常。
- 第一人称手持是否存在、比例是否正常。
- 第三人称掉落物是否存在。
- 重载资源包后模型和贴图是否仍然存在。
- 实体从各方向碰撞是否与 Grim 一致。
- 不同目标协议版本碰撞是否符合对应分支。
- 对于 `ModernBlock`，服务端发送替代方块后本地显示是否恢复。
- 对于 `ModernBlock`，进入 world、重连和区块重载后是否无需交互就能恢复模型。
- 对于 `ModernBlock`，方块被破坏、替换、区块卸载后 tracker 是否清理。
- 对于原生已有方块，确认没有重复注册、本地替换或多余 tracker 状态。

## 9. 编译和资源校验

PowerShell：

```powershell
Get-Content src/main/resources/assets/minecraft/models/block/example.json -Raw |
    ConvertFrom-Json

.\gradlew.bat compileJava
```

如果模型使用动画贴图，还要确认同名 `.mcmeta` 会随资源一起打包。模型 JSON 能通过 JSON 校验不代表 parent、texture 或 blockstate 一定能被 1.8.9 正确解析，最好实际启动客户端测试。

## 10. 推荐新增顺序

按下面顺序实现，排错最省时间：

1. 先确认 1.8.9 是否已有该方块，以及 ViaBackwards fallback 后是否保留方块身份。
2. 原生已有方块直接修改原类和资源，按 `ViaProtocol` 补齐模型与碰撞分支，到此不接 `ModernBlock` 或 tracker。
3. 原生不存在的方块先加纹理、自包含 block 模型和 blockstate，确认世界显示。
4. 为新增方块继承 `ModernBlock` 或 `ModernBlockDirectional`，声明协议状态范围和解码规则。
5. 添加 ItemBlock、`ModelBakery` 和 `RenderItem` 注册，确认物品栏与手持。
6. 再接放置拦截及必要的交互 tracker；进入 world 的恢复由 `ModernBlockStateTracker` 自动处理。
7. 依据 Grim 补齐各协议版本的碰撞分支。
8. 运行编译和完整游戏内测试。

## 11. 挖掘硬度与工具规则

高版本新增方块不能只调用 `setHardness()`。1.8.9 使用 `Material` 同时决定工具效率和是否必须使用正确工具，而现代版本使用方块标签分别描述这些属性。两边语义不一致时，客户端会比服务端提前发送 `STOP_DESTROY_BLOCK`，Grim 会报告 `FastBreak`。

典型案例是砂轮：原实现为了碰撞和声音使用 `Material.wood`，导致客户端空手约 3 秒挖完；现代服务端的砂轮硬度为 `2.0` 且必须使用镐，空手约 10 秒挖完，因此 Grim 报告约 7000ms 的时间差。

现代方块应通过 `ModernBlock.setModernMining()` 独立声明三个属性：

```java
block.setModernMining(
        2.0F,
        ModernBlock.MiningTool.PICKAXE,
        true
);
```

- 第一个参数是原版 hardness。
- 第二个参数是获得挖掘速度加成的工具：`PICKAXE`、`AXE`、`SHOVEL`、`HOE` 或 `NONE`。
- 第三个参数对应现代 `requiresTool`。为 `true` 时，错误工具使用 `/100` 的挖掘除数；否则使用 `/30`。
- 剑的特殊倍率使用 `setModernSwordSpeed()`；竹子使用 `Float.MAX_VALUE`，大型垂滴叶和紫颂植物使用 `1.5F`。

当前已校准规则：

| 方块 | 硬度 | 有效工具 | 必须正确工具 |
| --- | ---: | --- | --- |
| 末地烛、气泡柱、脚手架、甜浆果丛、活珊瑚植物/扇、装饰陶罐 | 0.0 | 无 | 否 |
| 蜂蜜块 | 0.0 | 无 | 否 |
| 竹子 | 1.0 | 斧；剑瞬间破坏 | 否 |
| 紫颂植物、紫颂花 | 0.4 | 斧；剑 1.5 倍 | 否 |
| 草径 | 0.65 | 锹 | 否 |
| 营火、灵魂营火 | 2.0 | 斧 | 否 |
| 细雪 | 0.25 | 无 | 否 |
| 潜影盒 | 2.0 | 镐 | 否 |
| 切石机 | 3.5 | 镐 | 是 |
| 堆肥桶 | 0.6 | 斧 | 否 |
| 灯笼、灵魂灯笼 | 3.5 | 镐 | 否 |
| 讲台 | 2.5 | 斧 | 否 |
| 砂轮 | 2.0 | 镐 | 是 |
| 钟 | 5.0 | 镐 | 否 |
| 锁链 | 5.0 | 镐 | 是 |
| 珊瑚块 | 1.5 | 镐 | 是 |
| 死珊瑚植物/扇/墙扇 | 0.0 | 镐 | 是 |
| 蜡烛 | 0.1 | 无 | 否 |
| 蜡烛蛋糕、嗅探兽蛋 | 0.5 | 无 | 否 |
| 幽匿感测体 | 1.5 | 锄 | 否 |
| 幽匿尖啸体 | 3.0 | 锄 | 否 |
| 大型垂滴叶 | 0.1 | 斧；剑 1.5 倍 | 否 |
| 滴水石锥、紫水晶芽/簇 | 1.5 | 镐 | 否 |
| 泥巴 | 0.5 | 锹 | 否 |
| 重生锚 | 50.0 | 钻石镐 | 是 |

新增现代方块时必须同时核对目标版本 `Blocks` 的 `strength`/`breakInstantly`、`requiresTool`，以及 `AXE_MINEABLE`、`PICKAXE_MINEABLE`、`SHOVEL_MINEABLE`、`HOE_MINEABLE` 和剑效率标签。不要从渲染或碰撞使用的旧版 `Material` 推断挖掘规则。注册结束时会检查每个 `ModernBlock` 是否调用了 `setModernMining()`；漏配会直接抛出异常，禁止静默回退到 1.8 的材质挖掘逻辑。

Grim 回归测试至少覆盖：空手、错误工具、木质工具、钻石工具、效率附魔、急迫/挖掘疲劳、水下和离地状态。砂轮空手应约 10 秒完成，不能再次出现约 7000ms 的 `FastBreak` 差值。

## 12. 完整实战：从零新增一个高版本方块

本章以一个不存在于 1.8.9、拥有 `active=false/true` 两个状态的 `example_block` 为例，给出从协议数据到游戏内验证的完整流程。示例中的 `FIRST_STATE_ID`、`LAST_STATE_ID` 和 `LOCAL_ID` 都是占位符，必须替换成实际测得且未占用的数值，不能直接复制。

最终至少会涉及以下文件：

```text
src/main/java/net/minecraft/block/BlockExample.java
src/main/java/net/minecraft/block/Block.java
src/main/java/net/minecraft/init/Blocks.java
src/main/java/net/minecraft/item/Item.java
src/main/java/net/minecraft/client/resources/model/ModelBakery.java
src/main/java/net/minecraft/client/renderer/entity/RenderItem.java
src/main/java/cn/unfair/util/via/ViaBackwardsItemModels.java
src/main/java/cn/unfair/util/via/ModernBlockStateTracker.java
src/main/resources/assets/minecraft/blockstates/example_block.json
src/main/resources/assets/minecraft/models/block/example_block.json
src/main/resources/assets/minecraft/models/block/example_block_active.json
src/main/resources/assets/minecraft/models/item/example_block.json
src/main/resources/assets/minecraft/textures/blocks/example_block.png
src/main/resources/assets/minecraft/textures/blocks/example_block_active.png
```

并非每个新方块都要手动修改上面的全部 Java 文件。例如，协议捕获层已经存在时不需要改 `ModernBlockStateTracker`，ViaBackwards 映射可以自动识别物品时也不需要给 `ViaBackwardsItemModels` 添加特判。但排查时必须逐项确认整个链路。

### 12.1 收集原版和协议数据

先确定目标版本，而不是只写“高版本”。同一方块在 1.20、1.20.5、1.21 等版本中的 block state ID、属性顺序、碰撞和挖掘标签都可能不同。

至少收集以下信息：

1. 目标版本首次加入该方块的版本，以及需要支持的全部服务端版本。
2. 原版 registry name，例如 `minecraft:example_block`。
3. 完整状态属性和每个状态的原版 block state ID。
4. 碰撞箱、选择框、是否完整方块、是否遮光和渲染层。
5. hardness、有效工具、`requiresTool` 和剑的特殊效率。
6. 放置时如何计算朝向、半砖位置、水浸、层数等初始状态。
7. ViaBackwards 最终发给 1.8.9 客户端的 fallback 方块和物品信息。

协议 state ID 必须在 ViaBackwards 将其替换成 fallback **之前**，并且在 `getViaStateProtocol()` 指定的那一层读取。它不是跨协议通用 ID。例如，在 `ProtocolVersion.v1_20` 层测得的 ID 不能直接用于 `v1_19` 或 `v1_21`。

当前 `ModernBlockStateTracker` 已安装的主要捕获层包括 `v1_9`、`v1_11`、`v1_13`、`v1_14`、`v1_15`、`v1_16`、`v1_17`、`v1_19` 和 `v1_20`。新增方块类以前先检查：

```text
src/main/java/cn/unfair/util/via/ModernBlockStateTracker.java
```

中的 `install()`、对应的 `install1_xx()`、`capture(...)` 和 `decode(...)`。如果方块的真实身份只在一个尚未捕获的协议层存在，只注册 `ModernBlock` 不会生效，必须先给 tracker 补齐该层的区块、单方块更新和 section/multi-block update 捕获。

实际测量 state ID 时可以使用一个本地测试服，按下面流程操作：

1. 用 `/setblock` 或调试插件依次放出该方块的所有属性组合，并记录坐标和属性字符串。
2. 在对应 `install1_xx()` 的 handler 中，于原始 state ID 被写回 packet、ViaBackwards 执行 fallback 之前临时记录 `sourceVersion`、坐标和 `stateId`。也可以临时在 `capture(...)` 入口按测试坐标过滤后记录。
3. 分别触发首次区块加载、`BLOCK_UPDATE` 和 `CHUNK_BLOCKS_UPDATE`/section update，确认三条路径对同一状态得到相同协议层 ID。
4. 将日志整理成“协议版本 + 原版属性字符串 + state ID”表，再据此编写 `getStateFromViaStateId()`。
5. 测量完成后删除临时日志，避免正常游戏中按每个方块刷屏。

只观察 1.8.9 客户端最终收到的方块 ID 得到的是 fallback ID，不能用于 `ModernBlock` 解码。也不要从相邻方块的 ID 猜连续范围；应实际生成每一个状态并核对。

如果不同目标版本的 state ID 不同，有三种处理方式：

- 选择所有目标版本都会经过、且仍保留原始方块身份的最早公共捕获层。
- 为不同协议范围注册明确的映射，并重写 `handlesViaState(...)`/`getStateFromViaState(...)`。
- 状态结构差异很大时拆成独立实现，不要把多个版本的数字范围硬拼在一起。

### 12.2 选择本地类和状态

按方块结构选择最接近的现有实现：

- 固定一个或多个碰撞盒：`BlockModernShape`。
- 水平朝向的固定模型：`BlockModernFacingShape`。
- 整数状态解码：`BlockModernComposter`。
- 多碰撞盒和放置朝向：`BlockModernGrindstone`。
- 需要完全自定义行为：直接继承 `ModernBlock`。

如果方块没有需要在本地保存的属性，可以直接注册 `BlockModernShape`：

```java
new BlockModernShape(
        Material.rock,
        ProtocolVersion.v1_20,
        FIRST_STATE_ID,
        LAST_STATE_ID,
        new double[]{0, 0, 0, 16, 12, 16}
)
```

如果模型、碰撞或交互依赖状态，则在 Java 的 `BlockState` 中声明属性。必须注意：1.8.9 的 metadata 只有 4 bit，`ModernBlock` 的所有本地有效状态总数必须满足：

```java
this.blockState.getValidStates().size() <= 16
```

属性笛卡尔积超过 16 时，不要照搬服务端的所有属性。只保留渲染、碰撞和客户端交互真正需要的属性；其他服务端状态可由位置、邻居计算或单独缓存。确实需要超过 16 种本地表现时，必须设计显式的协议映射和额外状态存储，不能依赖 1.8 metadata。

### 12.3 编写方块类

创建 `src/main/java/net/minecraft/block/BlockExample.java`：

```java
package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;

public class BlockExample extends BlockModernShape {
    public static final PropertyBool ACTIVE = PropertyBool.create("active");

    public BlockExample(int firstState, int lastState) {
        super(
                Material.rock,
                ProtocolVersion.v1_20,
                firstState,
                lastState,
                new double[]{0, 0, 0, 16, 12, 16}
        );
        this.setDefaultState(this.blockState.getBaseState()
                .withProperty(ACTIVE, Boolean.FALSE));
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, new IProperty[]{ACTIVE});
    }

    @Override
    public IBlockState getStateFromViaStateId(int stateId) {
        int offset = stateId - this.firstState;
        if (offset < 0 || offset > 1) {
            throw new IllegalArgumentException("Unexpected example_block state " + stateId);
        }
        return this.getDefaultState()
                .withProperty(ACTIVE, offset == 1);
    }
}
```

这个例子只适用于协议表中两个状态连续排列，并且偏移 `0/1` 确实对应 `false/true` 的情况。必须按目标版本的真实 registry 顺序编写解码。属性组合更多或排列不直观时应显式 `switch`，不要依赖 `%`、位运算或猜测属性顺序：

```java
switch (stateId) {
    case ACTUAL_INACTIVE_STATE_ID:
        return this.getDefaultState().withProperty(ACTIVE, Boolean.FALSE);
    case ACTUAL_ACTIVE_STATE_ID:
        return this.getDefaultState().withProperty(ACTIVE, Boolean.TRUE);
    default:
        throw new IllegalArgumentException("Unexpected example_block state " + stateId);
}
```

现代 registry 中同一方块的状态通常是连续区间。如果实际要接受的 ID 存在空洞，不能只把首尾 ID 交给基类，因为 `handlesViaStateId()` 会接受整个区间。此时还要重写 `handlesViaState(ProtocolVersion, int)`，明确拒绝空洞中的 ID，再让 `getStateFromViaState(...)` 做对应解码；否则可能把其他方块误判成 `example_block`。

实现时还要按原版行为重写必要的方法，例如：

- `isOpaqueCube()`、`isFullCube()` 和渲染层。
- `getCollisionBoundingBox()`/`addCollisionBoxesToList()`。
- `onBlockPlaced()`，用于朝向、上下半部等初始状态。
- `setBlockBoundsBasedOnState()`，用于状态相关的选择框。
- 邻居更新和特殊右键交互。

多个碰撞盒应直接参考 `BlockModernGrindstone`。碰撞数据要按目标协议版本对照原版或 Grim，不能从 JSON 模型的 `elements` 推断，因为视觉模型和物理碰撞经常不同。

### 12.4 注册 Block 和 ItemBlock

在 `Block.registerBlocks()` 中选择一个未使用的 1.8 本地 block ID，并保持 registry name 为 `example_block`：

```java
registerBlock(
        LOCAL_ID,
        "example_block",
        new BlockExample(FIRST_STATE_ID, LAST_STATE_ID)
                .setModernMining(
                        2.0F,
                        ModernBlock.MiningTool.PICKAXE,
                        true
                )
                .setUnlocalizedName("exampleBlock")
);
```

这里有三个不能混淆的标识：

- `LOCAL_ID` 是此 1.8.9 客户端 `Block.blockRegistry` 内未使用的方块 ID。
- `FIRST_STATE_ID`/`LAST_STATE_ID` 是指定协议层中的现代 block state ID。
- `example_block` 是跨 Java 注册、资源和物品识别保持一致的 registry/model name。

绝对不要把现代 block state ID 当作 `LOCAL_ID`。添加前用 `Block.java` 中现有的 `registerBlock(...)` 检查冲突。

在 `src/main/java/net/minecraft/init/Blocks.java` 声明字段：

```java
public static final Block example_block;
```

并在静态初始化中赋值：

```java
example_block = getRegisteredBlock("example_block");
```

在 `Item.registerItems()` 中注册本地 `ItemBlock`：

```java
registerItemBlock(Blocks.example_block);
```

即使服务端物品通常经过 ViaBackwards 变成带自定义模型数据的旧物品，本地 `ItemBlock` 注册仍应存在，供模型加载、原生物品栈和回退路径使用。

### 12.5 添加 blockstate、模型和贴图

创建 `assets/minecraft/blockstates/example_block.json`，每一个 Java 本地有效状态都必须能匹配一个 variant：

```json
{
  "variants": {
    "active=false": { "model": "example_block" },
    "active=true": { "model": "example_block_active" }
  }
}
```

创建自包含的 `assets/minecraft/models/block/example_block.json`：

```json
{
  "textures": {
    "all": "blocks/example_block",
    "particle": "blocks/example_block"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 12, 16],
      "faces": {
        "down":  { "texture": "#all", "cullface": "down" },
        "up":    { "texture": "#all" },
        "north": { "texture": "#all", "cullface": "north" },
        "south": { "texture": "#all", "cullface": "south" },
        "west":  { "texture": "#all", "cullface": "west" },
        "east":  { "texture": "#all", "cullface": "east" }
      }
    }
  ]
}
```

再创建 `models/block/example_block_active.json`，几何可以相同，但把 `all` 和 `particle` 指向 `blocks/example_block_active`。贴图放在：

```text
assets/minecraft/textures/blocks/example_block.png
assets/minecraft/textures/blocks/example_block_active.png
```

创建 `assets/minecraft/models/item/example_block.json`：

```json
{
  "parent": "block/example_block"
}
```

现代原版资源不能保证被 1.8.9 模型解析器直接加载。迁移模型时必须遵守：

- 世界 block 模型尽量展开成自包含 JSON，不要同时保留 `parent` 和 `elements`。
- 自定义 3D 方块不得继承带 `display.gui` 的 `block/block`；否则会与 1.8.9 GUI 变换叠加，造成库存模型倾斜且过小。
- 将继承链中的 `textures`、`elements`、旋转和显示参数实际合并，不能只复制最末级文件。
- 方块 atlas 使用 `blocks/...` 贴图路径，并提供 `particle`。
- 透明贴图要同时核对 PNG alpha、方块渲染层和 `isOpaqueCube()`；按需要使用 `CUTOUT` 或 `TRANSLUCENT`。
- 手持大小异常时单独制作 inventory 模型或补正确的 `display`，不要缩放世界模型碰撞。

### 12.6 接入物品识别和模型加载

在 `ModelBakery.registerVariantNames()` 注册 ItemBlock 的模型名：

```java
this.variantNames.put(
        Item.getItemFromBlock(Blocks.example_block),
        Lists.newArrayList("example_block")
);
```

如当前方块需要显式 inventory 映射，在 `RenderItem.registerItems()` 添加：

```java
this.registerBlock(Blocks.example_block, "example_block");
```

ViaBackwards 物品必须满足：

```java
"example_block".equals(ViaBackwardsItemModels.getModelName(stack))
```

因为通用放置正是用这个名字查询：

```java
Block.blockRegistry.getObject(ResourceLocation.of(modelName))
```

所以 block registry name、item model 文件名和 `getModelName()` 的返回值必须完全一致。

`ViaBackwardsItemModels` 会读取各版本 mapping data，并只收集存在于 `models/item/` 的资源。推荐顺序是：

1. 先添加 `models/item/example_block.json`。
2. 启动后检查 Via backup NBT、源物品 ID或 CustomModelData 是否已被 mapping data 自动解析成 `example_block`。
3. 自动映射无法识别时，再在 `ViaBackwardsItemModels` 中补 backup tag/source ID 规则。
4. 显示名识别只能作为兼容兜底，不能作为主要方案；服务器改名、语言变化或格式码都会使它失效。

物品栏模型正常并不代表放置识别正常。调试时要分别检查 `getModelLocation(stack)` 和 `getModelName(stack)`，后者必须返回 registry name。

### 12.7 接入通用放置和状态同步

普通新增方块注册为 `ModernBlock` 且能被 `ViaBackwardsItemModels` 识别后，通常不需要修改 `PlayerControllerMP`。当前 `placeModernBlock()` 已完成：

1. 用 `resolveModernBlock()` 按模型名查找本地方块。
2. 调用 `onBlockPlaced()` 计算预测状态和目标位置。
3. 调用 `ModernBlockStateTracker.predict(...)` 记录本地预测。
4. 立即 `world.setBlockState(...)`，播放声音并扣除物品。
5. 由现有主手使用流程触发客户端 swing。
6. 收到服务端区块、单方块或批量更新后，用 fallback 前捕获的真实状态确认或覆盖预测。

因此出现问题时按以下方向定位：

- 物品能显示但不进入通用放置：检查 `getModelName(stack)` 与 registry name。
- 放置后短暂显示 fallback：检查所选协议层的更新包是否在 fallback 前被捕获。
- 挖掉后模型残留：检查空气/其他方块更新是否清理 tracker 状态，以及单方块和批量更新是否都覆盖。
- 重连或重载区块后丢模型：检查 chunk capture、chunk apply 和卸载清理。
- 放置不 swing：先确认通用放置返回成功，并确认没有专用分支提前返回；不要在多个分支重复 swing。
- 朝向只在刚放下时正确：对齐 `onBlockPlaced()` 的预测状态和 `getStateFromViaStateId()` 的服务端状态。

不要为每个普通方块复制一套 tracker。`ModernBlockStateTracker.discoverModernBlocks()` 会从 `Block.blockRegistry` 自动发现合法 state ID 范围的 `ModernBlock`。只有状态机、交互或摆放规则特殊时才增加专用逻辑。

### 12.8 特殊交互与状态缓存

以下情况不能只依赖通用放置：

- 脚手架需要沿当前脚手架延伸，并受距离支撑规则限制。
- 右键会切换状态或消耗另一物品，例如重生锚充能。
- 方块状态由方块实体 NBT 决定。
- 状态属性超过 16 个本地组合，需要 tracker 外的缓存。
- 碰撞依赖邻居或世界高度，不能只从单个 block state 得出。

专用实现仍应遵守同一所有权规则：协议 tracker 保存服务端真实状态，本地 prediction 只负责消除放置延迟，服务端确认始终可以覆盖预测。服务端返回空气或其他方块时必须立即清理预测和额外缓存；区块卸载、切换世界和重连也必须清理。

### 12.9 配置挖掘规则

注册方块时必须调用 `setModernMining()`，不要让 `Material` 隐式决定现代挖掘速度：

```java
.setModernMining(
        2.0F,
        ModernBlock.MiningTool.PICKAXE,
        true
)
```

三个值必须来自目标原版版本的 hardness、mineable tag 和 `requiresTool`。如果剑有特殊倍率，再调用：

```java
.setModernSwordSpeed(1.5F)
```

同一方块跨版本规则变化时，应按当前连接协议选择规则或拆分实现，不能只以最早加入版本为准。详细公式和现有方块对照见第 11 章。

### 12.10 编译和游戏内验证

先校验所有新增 JSON，再构建项目：

```powershell
Get-Content src/main/resources/assets/minecraft/blockstates/example_block.json -Raw |
    ConvertFrom-Json
Get-Content src/main/resources/assets/minecraft/models/block/example_block.json -Raw |
    ConvertFrom-Json
Get-Content src/main/resources/assets/minecraft/models/block/example_block_active.json -Raw |
    ConvertFrom-Json
Get-Content src/main/resources/assets/minecraft/models/item/example_block.json -Raw |
    ConvertFrom-Json

.\gradlew.bat build --console=plain
```

至少在首次加入版本、当前主要测试版本和最新版目标协议各测一次。不能只在 1.20.5 通过后就认为其他版本正确。

游戏内按以下顺序验证，便于区分资源、协议和预测问题：

1. 通过服务端已有区块加载方块，检查所有状态模型和碰撞。
2. 服务端发送单方块更新，检查状态切换与移除。
3. 服务端发送批量/section 更新，检查状态切换与移除。
4. 主手放置，检查 swing、声音、数量扣除、预测位置和服务端确认。
5. 从六个面和可替换方块上放置，检查朝向和目标位置。
6. 挖掉、活塞替换、爆炸破坏，确认模型立即消失且不会恢复。
7. 走出区块再返回、重载资源包、切换世界和重连。
8. 测试空手、错误工具、正确工具、效率附魔、急迫、疲劳、水下和离地挖掘，确认 Grim 不报 `FastBreak`。
9. 检查第一人称、第三人称、物品栏和掉落物渲染，尤其是透明、光照和大小。

### 12.11 新方块提交前检查表

- [ ] 已确认 1.8.9 不存在可直接复用且 fallback 后身份可靠的原生方块。
- [ ] state ID 来自正确协议层，所有数字都已注明对应 Minecraft/Via 协议版本。
- [ ] 区块、单方块和批量更新都能在 fallback 前捕获。
- [ ] Java 本地有效状态不超过 16，且每个状态都有 blockstate variant。
- [ ] `getStateFromViaStateId()` 覆盖真实 ID，不依赖猜测的属性顺序。
- [ ] 本地 block ID 未冲突，且没有把协议 state ID 当成本地 ID。
- [ ] `Block.java`、`Blocks.java` 和 `Item.java` 注册完整。
- [ ] registry name、blockstate 名、模型名和 Via 物品模型名完全一致。
- [ ] block 模型已展开为 1.8.9 可加载格式，贴图进入 blocks atlas。
- [ ] 3D block 模型未继承现代 `block/block`，item 模型没有重复的 `display.gui` 缩放和旋转。
- [ ] `ModelBakery` 已收集模型；需要时已在 `RenderItem` 注册 inventory 模型。
- [ ] 普通方块复用通用放置；特殊方块的预测和清理逻辑完整。
- [ ] hardness、有效工具、`requiresTool` 和剑效率已经按目标版本配置。
- [ ] 透明层、遮光、选择框、碰撞和 Grim 结果一致。
- [ ] 放置有 swing，服务端拒绝时本地预测会被回滚。
- [ ] 挖掉、替换、区块卸载、切换世界和重连后没有残留模型或 tracker 状态。
- [ ] 已对全部声称支持的协议版本完成游戏内回归，而不是只验证单一版本。
