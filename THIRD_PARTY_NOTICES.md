# Third-party notices

根目录 MIT 许可适用于本项目原创且未另行声明许可的部分。以下组件保留独立许可、版权和来源；根目录许可不替代这些许可。依赖管理器安装的其他软件还适用其随包附带的许可，本文件列出随本仓库直接分发的主要第三方资源与插件。

## HydroOJ 衍生插件和模板

- 上游：[hydro-dev/Hydro](https://github.com/hydro-dev/Hydro)，尤其是 `packages/ui-default`（`@hydrooj/ui-default`）。
- 仓库中的 `hydro-api/rating-system/` 与 `hydro-api/paa-external-login/` 包含基于 Hydro UI 的修改模板及配套插件代码，独立按 **GNU Affero General Public License v3 / AGPL-3.0-only** 分发。
- 完整许可分别保存在 [rating-system/LICENSE](hydro-api/rating-system/LICENSE) 与 [paa-external-login/LICENSE](hydro-api/paa-external-login/LICENSE)。来源对应与修改说明分别见 [rating-system/NOTICE.md](hydro-api/rating-system/NOTICE.md) 和 [paa-external-login/NOTICE.md](hydro-api/paa-external-login/NOTICE.md)。
- Hydro 及其贡献者拥有上游部分的相关权利。已有 attribution 保留；本仓库未提供隐藏 Hydro 原生版权页脚的替代模板。
- 这些插件由独立 Hydro 实例按需加载。主站基础部署不加载 Hydro，也不包含 Hydro 数据库、用户会话或运行配置。通过 HTTP 集成使用的接口说明不改变这些独立插件的许可。

## Ant Design Icons

- 位置：`hydro-api/rating-system/templates/components/rating_board_icons.html` 中使用的图标。
- 上游：[ant-design/ant-design-icons](https://github.com/ant-design/ant-design-icons)。
- 许可：**MIT**。
- Copyright (c) 2018-present Ant UED, https://xtech.antfin.com/
- 完整许可：[ant-design-icons-LICENSE.txt](hydro-api/rating-system/public/ant-design-icons-LICENSE.txt)。该资源保留独立 MIT 许可，不因所在插件采用 AGPL 而失去原有许可声明。

## AlgoSpark Hydro Bridge

- 位置：`hydro-api/algospark-hydro-bridge-v0.2.0/`。
- 包标识：`@algospark/hydro-bridge`，版本 `0.2.0`。
- 随包 `package.json` 声明许可为 **MIT**；本发行版保留该包标识、许可声明与使用说明。现有材料未提供独立上游仓库或具名版权声明，此处未代为编造。
- 此桥接供独立 AlgoSpark 系统选择部署，主站基础服务不依赖它。真实桥接密钥只从其服务端配置或环境变量读取。

## Noto Sans SC 字体

- 位置：`backend/src/main/resources/fonts/NotoSansSC.ttf`，用于中文证书生成。
- 来源：[Google Fonts / Noto Sans SC](https://github.com/google/fonts/tree/main/ofl/notosanssc)。
- 许可：**SIL Open Font License 1.1**。
- 字体随附的版权与完整许可：[OFL.txt](backend/src/main/resources/fonts/OFL.txt)。请在再次分发字体时保留该文件，遵守其中保留字体名称与字体独立销售等条件。

## ICPC Tools Resolver 与参考资源

- 上游：[icpctools/icpctools](https://github.com/icpctools/icpctools)。本版本使用 ICPC Tools `2.7.1354` 的 contest model/resolver 生成滚榜步骤。
- 随仓库分发：`backend/lib/contestModel-2.7.1354.jar`、`frontend/public/resolver/` 中的参考图片，以及 `frontend/public/fonts/resolver-helvetica.ttf`。
- 许可为 **ICPC Software License**，完整文本保存在 [后端组件许可](backend/lib/ICPC-SOFTWARE-LICENSE.txt)、[图片许可](frontend/public/resolver/ICPC-SOFTWARE-LICENSE.txt)及 [参考字体许可](frontend/public/fonts/icpc-license.txt)。
- 该许可含非商业/非创收用途限制、公开源代码和保留许可的条件，并保留基金会书面撤销授权的条款。根目录 MIT 许可不替代这些条款。分发时应同时保留原始许可和上游源码入口。
- 中文滚榜字体 `frontend/public/fonts/resolver-noto-sans-sc.woff2` 独立适用 **SIL Open Font License 1.1**，版权和完整许可位于 [noto-OFL.txt](frontend/public/fonts/noto-OFL.txt)。

## 外部运行依赖

PostgreSQL、Redis、Nginx、Node.js、Java、Spring Boot、Next.js、React、Ant Design、NapCat 和 HydroOJ 等运行依赖各自使用自己的许可。Docker 镜像和依赖包由部署流程按配置获取，其源码、商标、版权和许可不由本项目重新授权。
