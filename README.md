# Unfair-MCP

本项目使用 Gradle 构建，编译基线为 Java 17。源码与资源沿用原目录结构，均位于 `src/`。

## 构建

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

构建产物位于 `build/libs/`，应用分发包位于 `build/distributions/`。

## 运行

```powershell
.\gradlew.bat run
```

`run` 任务以 `src/` 为工作目录，以兼容现有的资源目录和 `test_natives/` 相对路径。

## 外部库

Lombok、fastutil、JavaCV、JavaCPP、FFmpeg 和 LWJGL 已完成一次性升级并保存在
`libs/client/` 与 `libs/lwjgl/`。Gradle 不配置远程仓库，编译与运行只读取
`libs/` 中的本地 JAR，不会自动检查或下载新版本。

当前固定版本：

- Lombok `1.18.46`
- fastutil `8.5.19`
- JavaCV/JavaCPP `1.5.13`
- FFmpeg `8.0.1-1.5.13`
- LWJGL `3.4.2`
- J2ObjC annotations `3.1`

`libs/minecraft/` 中的 authlib、声音系统、旧 OSHI 和定制 Netty 等库与 Minecraft 1.8
源码及运行时强绑定，因此保持兼容版本，不参与通用库升级。
