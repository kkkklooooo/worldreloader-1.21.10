# WorldReloader 自研配置屏幕设计计划 (Config Redesign Plan)

## 1. 背景与动机 (Background & Motivation)
目前项目使用 `cloth-config` 作为配置界面方案。虽然其功能强大，但也存在以下局限：
- **视觉风格**: 与项目特定的艺术风格（如世界重载的科技感/魔法感）不完全统一。
- **交互限制**: 对于复杂的映射列表（如 `StructureMapping` 和 `BiomeMapping`），默认的列表编辑器交互较为繁琐。
- **依赖性**: 减少对外部大型库的依赖，使项目更轻量化。

## 2. 设计目标 (Design Goals)
- **更直观的交互**: 针对物品和方块选择，提供带有图标预览的选择器。
- **分类清晰**: 使用侧边栏或顶部页签进行分类，避免过长的滚动列表。
- **动态预览**: 如果可能，在修改配置时实时显示相关说明或小图示。
- **本地化支持**: 完美继承现有的 `zh_cn.json` 和 `en_us.json`。

## 3. 技术架构 (Technical Architecture)

### 3.1 核心组件
- `ConfigScreen`: 继承自 `net.minecraft.client.gui.screen.Screen`，作为主容器。
- `ConfigEntryListWidget`: 自定义滚动列表，用于承载各个配置项。
- `AbstractConfigEntry`: 所有配置项的基类。
    - `BooleanEntry`, `IntEntry`, `StringEntry`: 基础类型。
    - `ItemMappingEntry`: 针对映射设计的复合类型（物品+目标ID）。

### 3.2 数据持久化
- 继续使用 `Gson` 进行 JSON 序列化。
- 脱离 `auto-config` 的注解依赖，或者保留数据结构但自定义 UI 绑定逻辑。

## 4. 实施阶段 (Implementation Phases)

### 第一阶段：原型开发 (Prototype)
- [ ] 创建基础 `Screen` 类。
- [ ] 实现基础的布局方案（页签切换逻辑）。
- [ ] 实现配置数据的读取与回写逻辑。

### 第二阶段：基础组件 (Basic Components)
- [ ] 实现按钮、输入框、勾选框等基础 UI 元素。
- [ ] 确保与 Minecraft 默认 UI 风格兼容并有所提升。

### 第三阶段：复杂交互 (Complex Interaction)
- [ ] 开发专门用于 `ItemRequirement` 和 `BiomeMapping` 的编辑器。
- [ ] 增加物品图标渲染支持。

### 第四阶段：集成与测试 (Integration)
- [ ] 替换 `KeyBindings` 中打开配置界面的逻辑。
- [ ] 进行多分辨率下的 UI 适配测试。

## 5. 预期成果
一个完全定制化、符合 `WorldReloader` 调性的配置界面，提升用户体验并增强项目的独立性。
