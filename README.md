![TITRADECK](docs/promo/titradeck-banner.webp)

<p align="center">
  <a href="https://github.com/dongzhan4137/titradeck/releases/latest"><img src="https://img.shields.io/github/v/release/dongzhan4137/titradeck?style=flat-square&amp;label=release&amp;labelColor=700f63&amp;color=a1168e" alt="Release"></a>
  <a href="https://github.com/dongzhan4137/titradeck/releases"><img src="https://img.shields.io/github/downloads/dongzhan4137/titradeck/total?style=flat-square&amp;label=downloads&amp;labelColor=a6660d&amp;color=ff9500" alt="Downloads"></a>
  <a href="https://github.com/dongzhan4137/titradeck/stargazers"><img src="https://img.shields.io/github/stars/dongzhan4137/titradeck?style=flat-square&amp;label=stars&amp;labelColor=a1a116&amp;color=ffff00" alt="GitHub Stars"></a>
</p>

## 什么是TitraDeck？

TitraDeck 是一款面向化学学习和日常计算的 Android 工作台。计算结果附带公式、单位和推导步骤，便于核对。

## 下载安装

需要 Android 8.0 或更新版本。
从 [GitHub Release](https://github.com/dongzhan4137/titradeck/releases) 下载 `TitraDeck-1.0.3.apk`，在手机上打开安装，或从源码构建。

## 主要功能

| 分类 | 功能 |
| --- | --- |
| 化学基础 | 解析化学式，计算摩尔质量与元素质量分数。支持方程式配平、化学计量、极限反应物、产率、经验式和分子式 |
| 溶液与酸碱 | 浓度换算、稀释、混合、质量与体积分数、强酸强碱及单质子弱酸弱碱的 pH |
| 物理化学 | 理想气体、化学平衡、溶度积、能斯特方程、电解、反应动力学与盖斯定律 |
| 结构与参考 | VSEPR、不饱和度、118 元素交互周期表、常数库和单位换算 |
| 化合物查询 | 通过名称、分子式、SMILES 或 PubChem CID 查询有机和无机化合物。结果每页 20 条，详情可切换二维结构图和分子模型 |

计算记录保存在设备上，可以恢复输入修改。应用支持自适应系统主题色和自定义主题色。

## 界面预览

![TitraDeck 化学工作台。封面展示计算首页与摩尔质量结果。](docs/promo/titradeck-cover.webp)

![化学计算。以硫酸铜五水合物为例，展示摩尔质量、元素质量分数与单位推导。](docs/promo/chemical-calculations.webp)

![化学参考。展示含 118 种元素的可交互周期表。](docs/promo/periodic-table.webp)

![化合物查询。并排展示咖啡因的二维结构图和三维球棍模型。](docs/promo/compound-structures.webp)

## 网络与数据

核心计算、周期表和常数库可离线使用。化合物查询需要联网，查询内容会发送至 PubChem。中文名称直接查询没有匹配时，应用会向 MyMemory 请求英文译名，再使用译名查询 PubChem。计算历史保留在本机。

遇到问题时，可提供 Android 版本、工具名称、输入和实际结果。 [反馈问题](https://github.com/dongzhan4137/titradeck/issues)

## 开发者与许可

東站
[Bilibili](https://space.bilibili.com/521110373) · [X](https://x.com/dongzhan4137) · [GitHub](https://github.com/dongzhan4137)

[MIT 许可证](https://github.com/dongzhan4137/titradeck/blob/main/LICENSE)
