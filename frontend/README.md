# PAA 前端

基于 Next.js 16、React 19、TypeScript 与 Ant Design 的竞赛管理界面。完整部署说明、默认管理员账号和功能介绍见[项目 README](../README.md)。

## 本地开发

需要 Node.js 22 或更高版本以及 npm。先启动后端，再执行：

```bash
npm ci
API_URL=http://localhost:8080 npm run dev
```

前端默认地址为 `http://localhost:3000`。浏览器统一访问 `/api`，Next.js 将请求转发至 `API_URL`；后端地址不得包含结尾的 `/api`。生产环境在构建时设置该值。

```bash
API_URL=http://localhost:8080 PUBLIC_SITE_URL=https://example.com npm run build
npm start
```

`PUBLIC_SITE_URL` 用于页面元信息和站点绝对地址，应与后端同名配置一致。`NEXT_PUBLIC_SCHOOL_OJ_URL` 是可选的学校 OJ 地址，构建时设置后启用学校 OJ 题目跳转；留空不会访问任何学校服务。站点品牌图标、默认头像和首页背景均为本地 SVG，可替换 `public/paa-*.svg`。

## 检查

```bash
npm run test:security
npm run test:qq-bot-security
npm run test:board-progress
npm run test:board-participants
npm run build
```

`src/app/admin/_legacy/` 中的管理组件仍由现有路由使用，请保留该目录。数据接口中的历史字段名为兼容后端而保留。
