# Quiet Channels（客户端补丁）— 让 Axiom 客户端能连上「插件版 Axiom」的 NeoForge 服务端

> 构建时间：2026-09-30 20:08 ｜ 版本 1.0.0 ｜ MIT
> 产物：`quietchannels-1.0.0.jar`（sha256 `ae054ee89012c48281fb92ab94711e1370f8b4176277b9209de4f87fd5bd2b29`）
> 源码：`quietchannels-src.tar.gz`（NeoForge MDK 骨架 + 本模组源码）

## 1. 解决什么问题

MCTG 服务端现在跑的是 **AxiomPaper 插件**（不装 Axiom mod）。而玩家客户端通过 **Sinytra Connector** 加载 Fabric 版 Axiom，会把 `axiom:*` 的 payload 通道注册成 **NeoForge「必需」通道**；服务端没有同名 mod → 协商失败，客户端在配置阶段被踢：

```
模组的网络通道 "Axiom" 连接失败：客户端希望传输载荷，但服务器端不支持！
```

**BeQuietNegotiator 救不了**：它的 bypass 只挂在「服务端被判定为非 NeoForge」的分支上，而 Youer 会说 NeoForge 握手，所以它不触发（已实测）。

本模组改的是 **NeoForge 的通道协商**（不是 Axiom）：把「对端缺失的通道」在指定命名空间内**从协商列表里剔除**，而不是中止连接。其余通道（VSS 等）保持原生协商，不受影响。

- 注入点：`net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator#negotiate(List, List)`
- 行为：HEAD 处过滤 `client` 列表（默认只针对 `axiom` 命名空间），过滤后递归调用原逻辑；无变化时完全走原生。
- 只对**物理客户端**生效（mixin 注册在 `client` 列表）。

## 2. 客户端安装

1. 把 `quietchannels-1.0.0.jar` 放进 `.minecraft/mods/`；
2. 需要 **NeoForge 21.1.x + MC 1.21.1**（与 MCTG 一致）；
3. 与 **Axiom 客户端 mod、Connector、forgified-fabric-api** 并存；**不需要** BeQuietNegotiator（可以删掉，它在这里不起作用）；
4. 服务端**不要装**这个 mod（客户端专用）。

## 3. 配置

首次启动会生成 `config/quietchannels-client.toml`：

```toml
enabled = true
namespaces = ["axiom"]          # 只放宽这个命名空间；若报错里出现别的命名空间，往这里加
dropMissingServerChannels = false
logRelaxed = true
```

生效时日志会打印一行：
```
[QuietChannels] relaxing channel negotiation: client N -> M, server N -> N (namespaces [axiom])
```

## 4. 预期与限制

- **预期**：客户端不再被踢；Axiom 客户端应能识别「服务端没有 mod」，**回退到 Paper 插件协议**（这也是真 Paper 服上的正常路径）。
- **不保证**：Axiom 客户端是闭源，是否会正确回退需实测。如果连上了但编辑无反应，说明它坚持走 mod 协议，那就得改走服务端桥接方案或退回 mod + Connector。
- 只放宽 `axiom` 命名空间，**不会**动 VSS 等其它通道；其它 mod 的协商行为完全不变。
- 过滤后，Axiom 的 mod 通道在 NeoForge 侧不存在，若客户端还尝试用 mod 协议发送，日志会出现未处理 payload 的警告（属预期）。

## 5. 卸载

删掉 jar 即可，不改存档、不改服务端。

## 6. 从源码构建

```bash
tar xzf quietchannels-src.tar.gz && cd quietchannels
JAVA_HOME=<jdk21> gradle build      # 产物在 build/libs/quietchannels-1.0.0.jar
```

骨架来自 NeoForge MDK（ModDevGradle 2.0.91），`neo_version=21.1.251`。

> 构建后请校验**展开后**的元数据（模板里含 `${...}` 占位符，不能直接解析；曾因注释被误取消导致 `not a valid mod file`）：
> ```bash
> ./gradlew build
> python3 -c "import zipfile,tomllib;z=zipfile.ZipFile('build/libs/quietchannels-1.0.1.jar');tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode());print('TOML OK')"
> ```
