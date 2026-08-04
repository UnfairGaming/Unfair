# Unfair

Unfair 是一个面向 Minecraft 1.8.9 的 Forge 客户端模组，以 OpenMyau+7 为地基，并提供了更多自定义模块。

### 鸣谢

- Demise
- OpenMyau https://github.com/60124808866/OpenMyau
- Epilogue https://github.com/qm123pz/Epilogue-Client
- 其他...

## 使用方式

### 打开 ClickGUI

默认按下 **右 Shift** 打开 ClickGUI。也可以在游戏内通过命令切换 `ClickGui` 模块：

```text
.toggle ClickGui
```

### 命令

命令必须以 `.` 开头，并在 Minecraft 聊天框中输入：

| 命令 | 用途 |
| --- | --- |
| `.help` / `.commands` | 查看命令列表 |
| `.toggle <模块>` / `.t <模块>` | 开关模块 |
| `.bind <模块> <按键>` / `.b` | 设置模块按键 |
| `.bind <模块> none` | 清除模块按键 |
| `.bind list` | 查看已有按键绑定 |
| `.config load <名称>` | 加载配置 |
| `.config save <名称>` | 保存配置 |
| `.config list` | 查看配置列表 |
| `.config folder` | 打开配置目录 |
| `.friend ...` | 管理好友 |
| `.target ...` | 管理目标 |
| `.module ...` | 查看或操作模块 |
| `.list` | 查看模块列表 |
| `.show` / `.hide` | 显示或隐藏模块 |
| `.playerlist` / `.players` | 查看当前玩家列表 |
| `.itemname` / `.item` | 查看当前手持物品信息 |
| `.vclip ...` | 执行垂直位移 |
| `.username` / `.name` / `.ign` | 复制当前用户名 |
| `.denick <名称>` | 查询玩家真实名称和 UUID |

具体参数可以在游戏内使用：

```text
.help
```

### 配置文件

配置文件存放在：

```text
config/Unfair/
```

默认配置文件为 `default.json`。模块开关、按键、隐藏状态和模块属性会写入 JSON；修改设置后会自动保存，游戏退出时也会保存当前配置。

## 开发环境

- Minecraft `1.8.9`
- Minecraft Forge `11.15.1.2318`
- Java `8`
- Gradle Wrapper `8.8`
- Architectury Loom / ForgeGradle 兼容开发环境
- SpongePowered Mixin

项目使用 Java 8 语言级别编写。建议使用 JDK 8 进行开发，JDK 17 或 21 构建；如果使用较新的 JDK，需要确认 Gradle、Loom 及旧版 Minecraft 依赖能够正常运行。

模组启动时会扫描各分类包并自动注册 `Module` 实现，因此新增普通模块时通常只需要将类放入对应分类包并继承 `Module`。

## 构建

Windows:

```powershell
.\gradlew.bat build
```

构建完成后，发布用 JAR 位于：

```text
build/libs/Unfair-<版本>.jar
```

项目还提供 Forge 客户端运行配置，可使用以下命令启动开发客户端：

```powershell
.\gradlew.bat runClient
```

首次构建会下载 Minecraft、Forge、映射和其他 Gradle 依赖，因此需要网络连接。

## 许可证

本项目以 GNU General Public License v3.0 发布，详见 [LICENSE](LICENSE)。
