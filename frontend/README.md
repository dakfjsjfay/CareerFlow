# CareerFlow-AI 前端（frontend）

技术栈：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios + ECharts

## 本地启动

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173（/api 请求由 Vite 代理到后端 8080，需先启动后端）。

## 构建验证

```bash
npm run build
```

## 目录说明

```
src/
├── api/         # 后端接口请求（路径以 docs/api.md 为准）
├── assets/      # 图片、图标、全局样式
├── components/  # 公共组件
├── layouts/     # 统一布局（侧边栏 + 顶栏）
├── router/      # 路由 + 守卫（JWT 校验）
├── stores/      # Pinia 状态管理
├── utils/       # 工具函数
└── views/       # 页面：Login/Register/Dashboard/Chat/Profile/Resume/Jobs/Match/Applications/Analytics
```

## 当前状态（Day 1）

- 骨架：路由 10 条、路由守卫、Axios 实例、Pinia、统一布局、蓝白 SaaS 风格首页
- 业务页面为占位（按纵向切片开发：D2 认证 → D3-5 对话建档 → …）
- 接口路径必须与 docs/api.md 一致，禁止自行编造
