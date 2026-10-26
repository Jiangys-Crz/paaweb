# Hydro 来源与修改说明

本插件包含基于 [Hydro](https://github.com/hydro-dev/Hydro) 的 `@hydrooj/ui-default` 模板制作的覆盖模板。上游项目和 UI 包声明 `AGPL-3.0`，本插件按 GNU Affero General Public License v3（`AGPL-3.0-only`）分发，完整许可见 [LICENSE](LICENSE)。上游已有的版权、许可和界面 attribution 保留；插件不提供隐藏 Hydro 原生版权信息的功能。

## 修改

本发行版日期：2026-10-26。覆盖模板增加了 PAA Web 的 主站账号登录和验证码登录界面。相关 TypeScript、CSS 和 JavaScript 用于支持这些插件功能；源代码随本目录提供。完整模板对应关系如下，路径均相对各自模板根目录。

| 本插件模板 | Hydro 上游文件 |
| --- | --- |
| `user_login.html` | `packages/ui-default/templates/user_login.html` |
| `partials/login_dialog.html` | `packages/ui-default/templates/partials/login_dialog.html` |

这些模板之外，插件内原有的第三方许可仍适用。网络部署或再次分发时请保留本文件、完整源码和相关许可证，遵守所用组件的许可证条件。
