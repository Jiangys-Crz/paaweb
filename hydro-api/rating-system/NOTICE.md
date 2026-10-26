# Hydro 来源与修改说明

本插件包含基于 [Hydro](https://github.com/hydro-dev/Hydro) 的 `@hydrooj/ui-default` 模板制作的覆盖模板。上游项目和 UI 包声明 `AGPL-3.0`，本插件按 GNU Affero General Public License v3（`AGPL-3.0-only`）分发，完整许可见 [LICENSE](LICENSE)。上游已有的版权、许可和界面 attribution 保留；插件不提供隐藏 Hydro 原生版权信息的功能。

## 修改

本发行版日期：2026-10-26。覆盖模板增加了 PAA Web 的 Rating 展示、比赛权限/榜单/提交记录交互，并使用中性渐变背景。相关 TypeScript、CSS 和 JavaScript 用于支持这些插件功能；源代码随本目录提供。完整模板对应关系如下，路径均相对各自模板根目录。

| 本插件模板 | Hydro 上游文件 |
| --- | --- |
| `contest_edit.html` | `packages/ui-default/templates/contest_edit.html` |
| `record_main.html` | `packages/ui-default/templates/record_main.html` |
| `ranking.html` | `packages/ui-default/templates/ranking.html` |
| `user_detail.html` | `packages/ui-default/templates/user_detail.html` |
| `record_main_tr.html` | `packages/ui-default/templates/record_main_tr.html` |
| `contest_problemlist.html` | `packages/ui-default/templates/contest_problemlist.html` |
| `contest_scoreboard.html` | `packages/ui-default/templates/contest_scoreboard.html` |
| `problem_edit.html` | `packages/ui-default/templates/problem_edit.html` |
| `contest_detail.html` | `packages/ui-default/templates/contest_detail.html` |
| `problem_detail.html` | `packages/ui-default/templates/problem_detail.html` |
| `layout/html5.html` | `packages/ui-default/templates/layout/html5.html` |
| `components/user.html` | `packages/ui-default/templates/components/user.html` |
| `partials/problem_list.html` | `packages/ui-default/templates/partials/problem_list.html` |
| `partials/contest_sidebar.html` | `packages/ui-default/templates/partials/contest_sidebar.html` |
| `partials/training_detail.html` | `packages/ui-default/templates/partials/training_detail.html` |
| `partials/category.html` | `packages/ui-default/templates/partials/category.html` |
| `partials/scoreboard.html` | `packages/ui-default/templates/partials/scoreboard.html` |
| `partials/rating_native_record_tr.html` | `packages/ui-default/templates/record_main_tr.html` |
| `partials/problem-sidebar-information.html` | `packages/ui-default/templates/partials/problem-sidebar-information.html` |
| `partials/rating_native_scoreboard.html` | `packages/ui-default/templates/partials/scoreboard.html` |
| `partials/nav.html` | `packages/ui-default/templates/partials/nav.html` |
| `partials/homepage/ranking.html` | `packages/ui-default/templates/partials/homepage/ranking.html` |

这些模板之外，插件内原有的第三方许可仍适用。网络部署或再次分发时请保留本文件、完整源码和相关许可证，遵守所用组件的许可证条件。
