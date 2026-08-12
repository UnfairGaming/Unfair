# 1.8.9 新增高版本方块支持指南

本文以 `dirt_path`、`farmland` 和 `campfire` 为例，说明如何把 1.8.9 原本不存在的高版本方块接入客户端，使它能正常显示、放置、同步状态，并按目标协议处理碰撞箱。

## 1. 先确定方块的协议行为

先在 `D:\desktop\Grim-2.0` 中查目标方块的碰撞规则，按目标协议版本分支实现，不要直接照搬当前版本碰撞箱。

重点核对：

- 方块是否完整碰撞、半方块碰撞或无碰撞。
- 点燃/熄灭、湿润/干燥等状态是否改变碰撞。
- 玩家放置时服务端实际发送什么替代方块。
- 1.8.9 客户端是否需要本地虚拟方块保存真实状态。

推荐把协议判断集中放在 `ViaProtocol`，方块类只负责调用：

```java
if (ViaProtocol.olderThanOrEqualsTo1_13_2()) {
    // 目标协议的旧碰撞规则
} else {
    // 新协议的碰撞规则
}
```

## 2. 新建本地方块类

参考：

- `src/main/java/net/minecraft/block/BlockDirtPath.java`
- `src/main/java/net/minecraft/block/BlockFarmland.java`
- `src/main/java/net/minecraft/block/BlockCampfire.java`

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

## 3. 注册 Block 和 ItemBlock

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

在 `RenderItem.registerItems()` 中增加 ItemBlock 到 inventory 模型的映射：

```java
this.registerBlock(Blocks.campfire, "campfire");
```

这是土径能正常出现在手持和物品栏中的关键路径。只添加 JSON 而不添加这里的注册，物品可能显示缺失模型、透明模型或错误模型。

## 7. 处理高版本放置和服务端替换

1.8.9 没有高版本方块时，ViaVersion/ViaBackwards 可能把放置结果临时替换成旧方块。

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

每新增一个高版本方块，至少测试：

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
- 服务端发送替代方块后本地显示是否恢复。
- 方块被破坏、替换、区块重载后 tracker 是否清理。

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

1. 先加纹理和一个最简单的自包含 block 模型。
2. 加 blockstate，确认世界显示。
3. 加 ItemBlock 和 `RenderItem` 注册，确认物品栏/手持。
4. 再加状态属性和朝向。
5. 最后接放置拦截、服务端替换和 tracker。
6. 依据 Grim 补齐碰撞分支。
7. 运行编译和完整游戏内测试。
