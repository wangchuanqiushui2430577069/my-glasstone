# glasstone-java

这是对原 Python 项目 `glasstone` 的 Java 迁移工程。  
目标不是逐行翻译，而是在 **Java 17 + Maven** 环境下重建一个可维护、可测试、可扩展的数值计算库，并保留原项目的学术模型边界与示例能力。

## 项目目标

- 保留原项目的模块划分：`units`、`overpressure`、`radiation`、`thermal`、`fallout`
- 用 Java 风格重组 API，同时尽量保持与原 Python 公共入口的语义对应
- 把图表插值、特殊函数、单位换算与领域公式分层隔离
- 保持数值行为可测试，并为后续继续迁移更多模型留出空间
- 提供 Java 版图形化示例，便于对照原 `examples/*.py`

## 技术栈

- Java 17
- Maven
- Hipparchus
  - 特殊函数：`Gamma`、`Erf`
  - 分布函数：`NormalDistribution`、`LogNormalDistribution`
  - 插值：`LinearInterpolator`
  - 数组工具：`MathArrays`
  - 基础数学函数：`FastMath`
- XChart
  - 用于 2D 曲线示例
- Swing
  - 用于窗口、控件和自绘 3D/等值线面板

## 工程结构

```text
glasstone-java
├─ pom.xml
├─ README.md
└─ src
   ├─ main
   │  └─ java
   │     └─ com
   │        └─ glasstone
   │           ├─ exception
   │           ├─ model
   │           ├─ units
   │           ├─ math
   │           ├─ overpressure
   │           ├─ radiation
   │           ├─ thermal
   │           ├─ fallout
   │           └─ examples
   │              └─ ui
   └─ test
      └─ java
         └─ com
            └─ glasstone
```

## 包级架构说明

### `com.glasstone.exception`

统一定义领域异常：

- `UnknownUnitException`
  - 单位换算组合不被支持时抛出
- `ValueOutsideGraphException`
  - 输入超出原始经验图表有效范围时抛出

这两个异常是整个工程的“边界保护层”，用于显式拒绝错误输入或不可信外推。

### `com.glasstone.model`

定义跨模块共享的数据结构：

- `BurstParameters`
  - 爆炸当量、爆高、对应单位
- `Coordinate2D`
  - 二维坐标
- `WindProfile`
  - 风速、风向、风切变

这一层只保存数据，不放业务公式。

### `com.glasstone.units`

统一管理单位体系与换算逻辑：

- 单位枚举
  - `DistanceUnit`
  - `PressureUnit`
  - `DoseUnit`
  - `SpeedUnit`
  - `WindShearUnit`
  - `YieldUnit`
- `UnitConverter`
  - 所有模块都通过它做单位归一化

设计原则：

- 领域模块不直接写散落的单位换算常数
- 先归一到内部标准单位，再换回调用方请求单位
- 所有不支持的单位组合都显式报错

### `com.glasstone.math`

数学支撑层，负责把通用数值问题从领域公式中剥离出来：

- `Interpolation`
  - 一维线性插值
  - 区间检查
  - 点集排序与重复点检查
- `SpecialFunctions`
  - Gamma
  - logGamma
  - erf
  - 标准正态分布 CDF

这层不包含任何核效应业务语义，只提供数值工具。

### `com.glasstone.overpressure`

冲击波/超压模块：

- `OverpressureCalculator`
  - `brodeOverpressure`
  - `dnaStaticOverpressure`
  - `dnaDynamicPressure`
  - `sovietOverpressure`
  - `inverseSovietOverpressure`
- `SovietOverpressureData`
  - 保存苏联超压图表离散点
  - 为“正查”和“反查”准备排序后的曲线

这一层的结构是：

1. 公开 API 接收 `BurstParameters + 距离 + 输出单位`
2. 统一把输入换算到公式需要的单位
3. 调用对应模型
4. 输出前再做单位换算

毁伤资料：
https://www.cdc.gov/niosh/docket/archive/pdfs/NIOSH-125/125-ExplosionsandRefugeChambers.pdf
https://en.wikipedia.org/wiki/Overpressure
https://html.rhhz.net/ZGFSWS/HTML/2019-2-129.htm


### `com.glasstone.radiation`

放射性沉降物模块：

- `RadiationCalculator`
  - 苏联模型：
    - `sovietSummary`
    - `sovietGamma`
    - `sovietNeutron`
  - Glasstone 模型：
    - `glasstoneSummary`
    - `glasstoneFissionFragmentGamma`
    - `glasstoneFissionSecondaryGamma`
    - `glasstoneThermonuclearSecondaryGamma`
    - `glasstoneFissionNeutron`
    - `glasstoneThermonuclearNeutron`
- `RadiationScenario`
  - `SUMMER` / `WINTER` / `MOUNTAIN`
- `SovietRadiationData`
  - 苏联图表数据
- `GlasstoneRadiationData`
  - Glasstone 图表数据

这一层的核心特点：

- 大量使用“图表数据 + 插值”
- 苏联模型采用“先按距离插值，再按当量插值”
- Glasstone 模型由多个剂量分量叠加

### `com.glasstone.thermal`

热辐射模块：

- `ThermalCalculator`
  - `sovietAirThermal`
  - `sovietGroundThermal`
  - `inverseSovietAirThermal`
  - `inverseSovietGroundThermal`

特点：

- 以离散图表为基础
- 通过对数距离与经验直线关系进行正算/反算
- 支持国际能见度代码映射到模型图表编号

### `com.glasstone.fallout`

放射性沉降模块：

- `Wseg10Config`
  - WSEG-10 输入配置
- `Wseg10Model`
  - 模型主体
  - 在构造时预计算云团和扩散参数
  - 后续可重复调用：
    - `doseRateHPlus1`
    - `falloutArrivalTimeHours`
    - `equivalentResidualDose`

这是当前 Java 工程里最典型的“有状态模型对象”。  
和其他模块的纯函数式风格不同，`Wseg10Model` 更适合先构建、再批量查询。

### `com.glasstone.examples`

Java 版图形示例入口：

- `InteractiveOverpressureExample`
- `InteractiveDynamicExample`
- `ThermalDemoExample`
- `OverpressureComparison3DExample`
- `RadiationComparison3DExample`
- `Wseg10DoseRateExample`
- `Wseg10DoseExample`
- `Wseg10CasualtiesExample`
- `ExampleSupport`
  - 示例共享参数、配色、网格采样、默认 WSEG-10 配置

### `com.glasstone.examples.ui`

示例层 UI 组件：

- `SurfacePlotPanel`
  - 自绘 3D 线框面板
- `ContourPlotPanel`
  - 自绘二维等值线/色带面板
- `SurfaceSeries`
  - 3D 曲面序列定义

这一层故意和核心算法层隔离，避免把 UI 依赖带入计算模块。

## 架构设计原则

### 1. 分层明确

工程按以下顺序组织依赖：

`exception/model/units/math` → `overpressure/radiation/thermal/fallout` → `examples/ui`

含义是：

- 基础层不依赖业务层
- 业务层只依赖基础层
- 示例层依赖业务层，但业务层不依赖任何图形库

### 2. 单位换算集中化

所有公开 API 都尽量采用：

1. 接受调用方指定单位
2. 统一换算到模型原生单位
3. 计算
4. 输出时换回调用方单位

这样做的好处是：

- 领域公式更清晰
- 单位错误更容易定位
- 测试可以直接做跨单位一致性校验

### 3. 图表数据与公式分离

像 `SovietOverpressureData`、`SovietRadiationData`、`GlasstoneRadiationData` 这类文件只保存数据和轻量整理逻辑。  
真正的业务计算在对应 `Calculator` 内完成。

这样做的价值是：

- 数据源更容易校对
- 公式层更容易阅读和测试
- 后续替换数据来源时影响面更小

### 4. Hipparchus 只用于“数学共性”，不侵入业务语义

当前使用 Hipparchus 的位置主要是：

- `Interpolation`：线性插值器和数组工具
- `SpecialFunctions`：特殊函数和分布函数
- `FastMath`：统一基础数学函数

领域公式本身仍保留业务语义，不会为了“全量库化”而牺牲可读性。

## 关键类说明

### `UnitConverter`

工程的公共换算入口。  
如果后续新增单位，优先在这里扩展，而不是把换算常数散落到各业务类。

### `Interpolation`

所有图表模型的公共插值入口。  
如果将来需要二维插值、样条或积分，这一层可以继续扩展。

### `Wseg10Model`

当前最“面向对象”的核心模型。  
它和 `OverpressureCalculator` / `RadiationCalculator` / `ThermalCalculator` 的区别在于：

- 前三者更像无状态函数库
- `Wseg10Model` 需要保留一套预计算状态

### `SurfacePlotPanel` 与 `ContourPlotPanel`

这两个类属于“示例展示层”，不参与任何物理模型计算。  
它们的职责是把数值结果转成可以在 Swing 窗口里直接展示的图形。

## 当前已完成的迁移范围

### 已完成

- 单位系统与异常
- 数学支撑层
- 苏联热辐射模型
- 苏联穿透辐射模型
- Glasstone 穿透辐射分支
- WSEG-10 沉降模型
- 超压模块的 Brode / DNA / 苏联主要入口
- Java 图形化示例

### 当前状态

- 核心计算代码可编译
- 核心测试可通过
- 示例可通过 Maven 直接启动

## 构建与测试

进入子项目目录：

```bash
cd glasstone-java
```

编译：

```bash
mvn compile
```

运行测试：

```bash
mvn test
```

## 运行示例

使用 `exec-maven-plugin`：

```bash
mvn exec:java -Dexec.mainClass=com.glasstone.examples.InteractiveOverpressureExample
```

可运行示例：

- `com.glasstone.examples.InteractiveOverpressureExample`
- `com.glasstone.examples.InteractiveDynamicExample`
- `com.glasstone.examples.ThermalDemoExample`
- `com.glasstone.examples.OverpressureComparison3DExample`
- `com.glasstone.examples.RadiationComparison3DExample`
- `com.glasstone.examples.Wseg10DoseRateExample`
- `com.glasstone.examples.Wseg10DoseExample`
- `com.glasstone.examples.Wseg10CasualtiesExample`

## 示例与 Python 对照

| Python 示例 | Java 示例 |
|---|---|
| `examples/interactive_overpressure.py` | `InteractiveOverpressureExample` |
| `examples/interactive_dynamic.py` | `InteractiveDynamicExample` |
| `examples/thermal_demo.py` | `ThermalDemoExample` |
| `examples/3dopcomparison.py` | `OverpressureComparison3DExample` |
| `examples/3dradcomparison.py` | `RadiationComparison3DExample` |
| `examples/wseg10.py` | `Wseg10DoseRateExample` |
| `examples/wseg10_dose.py` | `Wseg10DoseExample` |
| `examples/wseg10_casualties.py` | `Wseg10CasualtiesExample` |

## 开发建议

### 如果继续迁移模型

建议优先顺序：

1. 补更多回归测试样本
2. 继续完善 `overpressure` 中更复杂的波形/冲量部分
3. 提炼统一的图表数据装载规范
4. 为示例层增加统一启动器

### 如果继续工程化

建议下一步：

- 增加 `ExampleLauncher`
- 给示例层加截图或导出能力
- 为核心模型补更多边界测试
- 为外部调用者提供更稳定的 facade API

## 设计取舍说明

### 为什么示例层没有直接引 3D 专用图形库

当前做法是：

- 2D 曲线：`XChart`
- 3D 线框 / 等值线：Swing 自绘

原因是：

- 依赖更轻
- Maven 构建更稳定
- 对当前“迁移验证型工程”已经足够

如果未来目标是更强交互、更精细 3D 渲染，再考虑引入更完整的可视化框架更合适。

### 为什么保留数据类

原项目大量模型都依赖离散图表。  
直接把这些数据单独放到 `*Data` 类里，虽然文件会比较长，但优点是边界清楚：

- 数据是数据
- 公式是公式
- UI 是 UI

这比把数据散进计算方法里更容易维护。

## 结论

当前 `glasstone-java` 已经不是单纯的迁移脚手架，而是一个具备以下能力的 Java 工程：

- 可编译
- 可测试
- 可运行图形示例
- 模块边界清晰
- 可以继续作为正式 Java 库演进
