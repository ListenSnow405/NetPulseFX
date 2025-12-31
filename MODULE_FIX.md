# 模块依赖问题修复指南

## 问题描述

编译时出现错误：
```
java: 找不到模块: org.pcap4j.packetfactory.static_
```

## 解决方案

### 方案 1：使用正确的自动模块名（已应用）

自动模块名基于 JAR 文件名。对于 `pcap4j-packetfactory-static-1.8.2.jar`，自动模块名应该是：
- `pcap4j.packetfactory.static`（去掉版本号和连字符，保留点）

已在 `module-info.java` 中更新为：
```java
requires pcap4j.packetfactory.static;
```

### 方案 2：如果方案 1 仍然失败

如果仍然出现模块找不到的错误，可以尝试以下步骤：

#### 2.1 检查实际的模块名

运行以下命令查看实际的自动模块名：
```bash
jar --file=target/dependency/pcap4j-packetfactory-static-1.8.2.jar --describe-module
```

或者检查 Maven 本地仓库中的 JAR 文件：
```bash
# Windows PowerShell
Get-ChildItem "$env:USERPROFILE\.m2\repository\org\pcap4j\pcap4j-packetfactory-static" -Recurse -Filter "*.jar" | ForEach-Object { jar --file=$_.FullName --describe-module }
```

#### 2.2 如果 JAR 不是模块化的

如果 `pcap4j-packetfactory-static` 不是模块化的 JAR，可以：

1. **移除模块依赖**（如果项目允许）：
   - 从 `module-info.java` 中移除 `requires` 语句
   - 确保 Maven 依赖在 `pom.xml` 中正确配置
   - 代码将通过类路径访问（在非模块化模式下）

2. **使用 `requires static`**（可选依赖）：
   ```java
   requires static pcap4j.packetfactory.static;
   ```

3. **使用反射访问**（不推荐，但可行）：
   - 移除模块依赖
   - 使用 `Class.forName()` 动态加载类

### 方案 3：验证 Maven 依赖

确保 `pom.xml` 中包含正确的依赖：

```xml
<dependency>
    <groupId>org.pcap4j</groupId>
    <artifactId>pcap4j-packetfactory-static</artifactId>
    <version>1.8.2</version>
</dependency>
```

然后运行：
```bash
mvn clean compile
```

### 方案 4：检查是否需要 packetfactory-static

实际上，`pcap4j-core` 可能已经包含了基本的包解析功能。如果不需要 `packetfactory-static` 的特殊功能，可以：

1. 从 `pom.xml` 中移除依赖
2. 从 `module-info.java` 中移除 `requires` 语句
3. 修改代码使用 `pcap4j-core` 提供的功能

## 当前状态

已应用方案 1，使用模块名：`pcap4j.packetfactory.static`

如果编译仍然失败，请按照方案 2 的步骤检查实际的模块名。


