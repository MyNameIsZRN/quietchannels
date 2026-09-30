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


## 1.2.x 补充说明（实测驱动）

1. **协商不能"排除"通道，只能"对齐"**：把 `axiom:*` 从协商里剔除后，NeoForge 会把服务端要发的载荷
   降级为 `DiscardedPayload`，而 Youer 的发送路径硬转成自己的 `PluginsDiscardedPayload` →
   `ClassCastException` → 玩家进服 7 秒后被踢。现在改为：对配置命名空间的通道，
   * 服务端侧条目采用客户端声明的 flow/version；
   * 两侧都标记 optional（对端缺失时自动忽略，不再报错）。
2. **补上 Youer 缺失的 `starlight$serverRelightChunks`**：Youer 没打 Starlight 补丁，AxiomPaper
   每 tick 调这个方法 → `NoSuchMethodError` ≈20 次/秒（实测 104k 条 / 87 分钟 / 日志 204MB）。
   本模组用 mixin 给 `ThreadedLevelLightEngine` **补一个同签名方法**，用
   `updateSectionStatus(section, false)` 做等价重光照（单次上限见配置 `relightMaxChunksPerCall`）。
3. 注意：`@Shadow` 取不到**父类**字段（`LevelLightEngine.levelHeightAccessor`），
   所以另加了 `LevelLightEngineAccessor` 接口 mixin。


## 1.2.2（配置系统切换）

COMMON 配置会让客户端 `FileWatcher` 反复判定 `quietchannels-common.toml`「不正确→修正」，
形成**每秒一次**的写文件 + 刷屏死循环（实测客户端日志 125 行全是该警告）。
1.2.2 起**不再使用 NeoForge 的 config 系统**，改为自读 `config/quietchannels.properties`：

```properties
enabled = true
namespaces = axiom
relightMaxChunksPerCall = 256
logRelaxed = true
```

客户端升级后请手动删除旧的 `config/quietchannels-common.toml`（它已无人管理，留着只是碍事）。


## 1.2.3（修正归一化规则）

反编译 `neoforge-21.1.251-universal.jar` 得到真实校验规则后修正：

```java
validateComponent(left, right, side):
  if (left.flow 有 && right.flow 无) -> flow.<side>.missing
  if (left.flow 有 && right.flow 有 && 不同) -> flow.<side>.mismatch
  if (!fix(left.version, right.version)) -> version.mismatch
```

实测失败是 `flow.client.missing`：**服务端**（Youer 桥接 AxiomPaper 的 Bukkit 通道）声明了 flow，
**客户端**（Connector 转译的 Axiom mod）没有。1.2.2 的规则只在"客户端有 flow"时才采用，
客户端没有 flow 时保留了服务端的 → 仍然失败。1.2.3 改为**对称归一化**：
命中命名空间且两侧同 id 的条目统一写成 `(id, "quietchannels", 空 flow, optional)`；
单侧独有的条目仅标 optional 交给 NeoForge 丢弃。规则对称，与调用点参数顺序无关。

离线验证（复刻协商算法）：归一化后协商成功、VSS 等通道保留、反序同样成功。


## 1.3.0（修 Youer 载荷编码类型不匹配，插件路线的最后一块）

反编译证据链：
- Youer 把 `DiscardedPayload` 改造成「实现 `PluginsPayload`、带 `data` 字段」，并加了 2 参构造器；
- AxiomPaper 用 Paper 风格 API 直接 `new ClientboundCustomPayloadPacket(DiscardedPayload)`（`VersionHelper` 反射找 2 参构造器）；
- 但 Youer 给插件通道注册的 codec 是 `PluginsPayload.codec(...)`，其泛型定型为 `PluginsDiscardedPayload`（编译期插入 `checkcast`）；
- → 编码时 `ClassCastException: DiscardedPayload cannot be cast to PluginsDiscardedPayload`
  → `EncoderException` → 玩家进服数秒后被踢。

修法：mixin `CustomPacketPayload$1#writeCap`（**仅服务端**，注册在 mixins.json 的 `server` 列表），
遇到 `DiscardedPayload` 时按 Youer `PluginsPayload.dcodec` 语义直接写 `id + 原始字节`，
数据用反射取（`data()` / `getData()` / 字段 `data`），绕开那个定型错误的 codec。


## 1.4.0（补 Paper 的 LevelChunk.locX/locZ 字段）

AxiomPaper 的字节码里直接 `getfield net/minecraft/world/level/chunk/LevelChunk.locX:I`（还有 locZ）——
这两个 `public int` 字段是 **Paper 区块系统重写**加上的，而 Youer 的补丁集里没有，于是第一次处理方块改动时就报
`Class net.minecraft.world.level.chunk.LevelChunk does not have member field 'int locX'` 并把玩家踢掉。

1.4.0 用 mixin 在 `LevelChunk` 上补出这两个字段（名字必须与 Paper 一致），并在 `ChunkAccess`
构造器尾部按区块坐标写入值。JVM 解析字段引用会沿继承链查找，因此插件那条 `getfield` 即可解析成功。
仅服务端生效。
