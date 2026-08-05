# Unfair-MCP

本项目使用 Gradle 构建，编译基线为 Java 25。

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

当前固定版本：

- Lombok `1.18.46`
- fastutil `8.5.19`
- JavaCV/JavaCPP `1.5.13`
- FFmpeg `8.0.1-1.5.13`
- LWJGL `3.4.2`
- J2ObjC annotations `3.1`

`libs/minecraft/` 中的 authlib、声音系统、旧 OSHI 和定制 Netty 等库与 Minecraft 1.8
源码及运行时强绑定，因此保持兼容版本，不参与通用库升级。
