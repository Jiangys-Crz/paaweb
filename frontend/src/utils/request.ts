import axios from 'axios';

const request = axios.create({
  baseURL: '/api',
  withCredentials: true, // 核心配置：允许携带 Cookie
  timeout: 10000,
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default request;
