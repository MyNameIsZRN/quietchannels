## 评审交接（给评审 agent）

### 这是什么

`quietchannels` —— 一个**客户端专用**的 NeoForge 1.21.1 小模组（MIT），只有一个目的：
在 **NeoForge 系服务端**（本环境是 Youer 混合端）上，**放宽 payload 通道协商**，让通过
Sinytra Connector 加载了 Fabric 版 Axiom 的客户端不再因为「服务端缺少 `axiom:*` 必需通道」而在配置阶段被踢。

服务端那边跑的是官方 **AxiomPaper 插件**（Paper 插件形态），刻意*不*装 Axiom 的 mod。

### 需要评审的点（重点）

1. **mixin 正确性**（`src/main/java/com/mctg/quietchannels/mixin/NetworkComponentNegotiatorMixin.java`）
   - 注入点 `NetworkComponentNegotiator#negotiate(List, List)` 是否是该版本（NeoForge **21.1.251**）唯一/正确的协商入口？目标类在实体 jar 中的签名已核对：`negotiate(List,List)NegotiationResult`（static）。
   - HEAD 注入 + `circ.setReturnValue(...)` 里**递归调用** `NetworkComponentNegotiator.negotiate(...)`：靠静态布尔 `quietchannels$active` 防重入。请评估：
     - 重入保护是否足够（是否存在并发/多连接同时协商的路径，静态标志会不会误伤？配置阶段是否单线程？）
     - `finally` 是否覆盖所有路径（异常时会不会把标志位卡在 true，导致后续协商被跳过？）
     - 用 `@Inject` 改写返回值 vs 用 `@ModifyVariable`/`@Redirect` 直接在参数上做过滤，哪个更稳？为什么？
   - `catch (Throwable)` 兜底是否恰当（会吞掉哪些错误？会不会掩盖真实问题？）。
2. **语义正确性**：只剔除「对端缺失且命名空间命中的通道」，其余通道（例如 VSS）保持不变——这个取舍是否符合预期？会不会让某些 mod 进入"以为自己有通道、实际没有"的坏状态？
   - 过滤后协商结果里不再包含 `axiom:*`，客户端侧 Axiom 预期**回退到 Paper 插件协议**（真 Paper 服上的正常路径）。客户端闭源，回退行为需实测——请评估这个假设是否成立，以及失败时的表现。
3. **配置**（`QuietChannelsConfig.java`）
   - `ModConfig.Type.CLIENT` 注册时机与 mixin 读取时序（协商发生在配置阶段，配置是否一定已加载？`try/catch` 兜底默认值是否合理）。
   - `defineList` 的校验器与默认值是否会在旧版本 NeoForge 上抛错。
4. **适用面/副作用**
   - mixin 注册在 `client` 列表（仅物理客户端），请确认这样写不会在专用服务端加载时报错。
   - 本模组与 BeQuietNegotiator（GPL-3.0，注入 `NetworkRegistry.initializeOtherConnection` / `PacketDecoder` / `onPacketError`）是**不同注入点、不同路径**，理论上可共存；请确认没有交叉影响。
5. **许可与合规**
   - 代码为本项目自写（MIT）；工程骨架来自 NeoForge MDK（`build.gradle` / `gradlew` / `gradle/wrapper` / `.github`），未复制 BeQuietNegotiator 的任何代码。
   - 明确：本补丁**不绕过** Axiom 的授权/白名单机制（客户端仍然要向 `axiom.moulberry.com` 校验 UUID 的多人白名单），只处理 NeoForge 的传输层协商。

### 构建与验证

```bash
# 需要 JDK 21
gradle build          # 产物 build/libs/quietchannels-1.0.0.jar
```

已知构建环境：Gradle 8.10.2 + Temurin 21.0.12 + ModDevGradle 2.0.91，`neo_version=21.1.251`，构建成功（本机实测 5m41s）。
客户端验证（需要一台带 NeoForge + Connector + Axiom mod 的客户端）：
1. 把 jar 放进客户端 `mods/`；
2. 连接目标服（服务端只装 AxiomPaper 插件、不装 Axiom mod）；
3. 期望：不再出现 `模组的网络通道 "Axiom" 连接失败`；日志出现 `[QuietChannels] relaxing channel negotiation: ...`；
4. 进一步验证：Axiom 的编辑功能是否真的生效（若无效，说明客户端没有回退到插件协议）。

### 已知限制（请重点评估是否可接受）

- 只处理「客户端必需、服务端缺失」这一类；若服务端反而多出客户端没有的必需通道，默认不处理（配置项 `dropMissingServerChannels` 可开）。
- 客户端闭源，无法保证它一定会回退到插件协议。
- 这是一个**兼容性补丁**，不改变服务端行为；服务端插件本身在 Youer 上还有一个已知问题（`starlight$serverRelightChunks` 方法缺失导致每 tick 抛 `NoSuchMethodError`），与本模组无关，另行处理。
