import requests
import time
import logging
import threading
import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import json
from urllib.parse import urlparse
from datetime import datetime

# 配置日志
logging.basicConfig(level=logging.INFO, format='[%(asctime)s] %(message)s', datefmt='%H:%M:%S')

class ApiClient:
    def __init__(self, config):
        self.config = config
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        })

    def fetch_problems(self):
        raise NotImplementedError

    def fetch_submissions(self):
        raise NotImplementedError

class HydroClient(ApiClient):
    def __init__(self, base_url, contest_id, config):
        super().__init__(config)
        self.base_url = base_url.rstrip('/')
        self.contest_id = contest_id
        cookie_val = self.config.get('cookie_sid', '').strip()
        if cookie_val.startswith("sid="):
            cookie_val = cookie_val[4:]
        self.session.headers.update({
            "Cookie": f"sid={cookie_val}"
        })

    def fetch_problems(self):
        url = f"{self.base_url}/api/contest/{self.contest_id}/problems"
        try:
            resp = self.session.get(url, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            # Hydro returns list of problems directly
            problems = []
            for item in data:
                problems.append({
                    "display_id": item.get("problem_id"),
                    "title": item.get("problem_name"),
                    "score": item.get("problem_score", 100),
                    "time_limit": item.get("time"),
                    "memory_limit": item.get("memory")
                })
            return problems
        except Exception as e:
            logging.error(f"Hydro fetch problems error: {e}")
            return []

    def format_submit_time(self, t):
        if not t:
            return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        if isinstance(t, (int, float)):
            # Handle timestamps (seconds or milliseconds)
            if t > 1e11: # likely milliseconds
                t = t / 1000.0
            dt = datetime.fromtimestamp(t)
            return dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        if isinstance(t, str):
            # Support legacy time strings without an explicit seconds field.
            try:
                import re
                # Check if it matches exactly YYYY-MM-DD HH:mm:SSS (where SSS is 3 digits and no seconds)
                m = re.match(r'^(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}):(\d{3})$', t.strip())
                if m:
                    # It's missing seconds. Let's assume seconds is 00 and the 3 digits are milliseconds.
                    # Backend expects standard time format, but we can just pass it as YYYY-MM-DD HH:mm:00.SSS
                    return f"{m.group(1)}:00.{m.group(2)}"
                
                # Check if it matches YYYY-MM-DD HH:mm:ss:SSS (colon instead of dot)
                m2 = re.match(r'^(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}):(\d{1,3})$', t.strip())
                if m2:
                    return f"{m2.group(1)}.{m2.group(2)}"

                # If it's already in the target format YYYY-MM-DD HH:mm:ss.SSS
                if re.match(r'^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{1,3}$', t.strip()):
                    return t.strip()

                # If it's ISO format
                if 'T' in t:
                    dt = datetime.fromisoformat(t.replace('Z', '+00:00'))
                    return dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
            except Exception:
                pass
            return t
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

    def fetch_submissions(self):
        url = f"{self.base_url}/api/contest/{self.contest_id}/submissions"
        try:
            resp = self.session.get(url, timeout=30) # Submissions list can be large
            resp.raise_for_status()
            data = resp.json()
            # Hydro returns list of submissions directly
            submissions = []
            for item in data:
                submissions.append({
                    "rid": str(item.get("rid")),
                    "status": item.get("status"),
                    "score": item.get("score"),
                    "problem": item.get("problem"), # Hydro returns problem name usually
                    "username": item.get("username"),
                    "runtime": str(item.get("runtime")),
                    "memory": str(item.get("memory")),
                    "language": item.get("language"),
                    "submit_time": self.format_submit_time(item.get("submit_time")),
                    "code": item.get("code", "")
                })
            return submissions
        except Exception as e:
            logging.error(f"Hydro fetch submissions error: {e}")
            return []

class XcpcClient(ApiClient):
    def __init__(self, base_url, contest_id, config):
        super().__init__(config)
        self.base_url = base_url.rstrip('/')
        self.contest_id = contest_id
        self.session.headers.update({
            "x-api-key": self.config.get('source_api_key', '')
        })

    def fetch_problems(self):
        url = f"{self.base_url}/api/contests/{self.contest_id}/problems"
        try:
            resp = self.session.get(url, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            problems = []
            for item in data:
                problems.append({
                    "display_id": item.get("displayId"),
                    "title": item.get("name"),
                    "score": item.get("score", 100), # Default 100 if missing
                    "time_limit": item.get("time"),
                    "memory_limit": item.get("memory")
                })
            return problems
        except Exception as e:
            logging.error(f"XCPC fetch problems error: {e}")
            return []

    def format_submit_time(self, t):
        if not t:
            return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        if isinstance(t, (int, float)):
            if t > 1e11:
                t = t / 1000.0
            dt = datetime.fromtimestamp(t)
            return dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        if isinstance(t, str):
            try:
                import re
                m = re.match(r'^(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}):(\d{3})$', t.strip())
                if m:
                    return f"{m.group(1)}:00.{m.group(2)}"
                
                m2 = re.match(r'^(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}):(\d{1,3})$', t.strip())
                if m2:
                    return f"{m2.group(1)}.{m2.group(2)}"

                if re.match(r'^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{1,3}$', t.strip()):
                    return t.strip()

                if 'T' in t:
                    dt = datetime.fromisoformat(t.replace('Z', '+00:00'))
                    return dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
            except Exception:
                pass
            return t
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

    def fetch_submissions(self):
        url = f"{self.base_url}/api/contests/{self.contest_id}/submissions"
        try:
            resp = self.session.get(url, timeout=30)
            resp.raise_for_status()
            data = resp.json()
            submissions = []
            for item in data:
                submissions.append({
                    "rid": str(item.get("rid")),
                    "status": item.get("status"),
                    "score": item.get("score"),
                    "problem": item.get("problem"),
                    "username": item.get("username"),
                    "runtime": str(item.get("runtime")),
                    "memory": str(item.get("memory")),
                    "language": item.get("language"),
                    "submit_time": self.format_submit_time(item.get("submit_time")),
                    "code": item.get("code", "")
                })
            return submissions
        except Exception as e:
            logging.error(f"XCPC fetch submissions error: {e}")
            return []

class TextHandler(logging.Handler):
    def __init__(self, text_widget):
        super().__init__()
        self.text_widget = text_widget

    def emit(self, record):
        msg = self.format(record)
        def append():
            self.text_widget.configure(state='normal')
            self.text_widget.insert(tk.END, msg + '\n')
            self.text_widget.configure(state='disabled')
            self.text_widget.yview(tk.END)
        self.text_widget.after(0, append)

class SpiderApp:
    def __init__(self, root):
        self.root = root
        self.root.title("外榜自动同步工具 (API版)")
        self.root.geometry("700x800")
        
        self.is_running = False
        self.sync_thread = None
        
        self.create_widgets()
        self.setup_logging()

    def setup_logging(self):
        logger = logging.getLogger()
        for handler in logger.handlers[:]:
            logger.removeHandler(handler)
        text_handler = TextHandler(self.log_text)
        formatter = logging.Formatter('[%(asctime)s] %(message)s', '%H:%M:%S')
        text_handler.setFormatter(formatter)
        logger.addHandler(text_handler)

    def create_widgets(self):
        # 1. Source Config
        lf_source = ttk.LabelFrame(self.root, text="源比赛系统配置", padding=10)
        lf_source.pack(fill="x", padx=10, pady=5)
        
        ttk.Label(lf_source, text="系统类型:").grid(row=0, column=0, sticky="w", pady=5)
        self.source_type = tk.StringVar(value="hydro")
        ttk.Radiobutton(lf_source, text="Hydro OJ", variable=self.source_type, value="hydro").grid(row=0, column=1, sticky="w")
        ttk.Radiobutton(lf_source, text="XCPC API", variable=self.source_type, value="xcpc").grid(row=0, column=2, sticky="w")
        
        ttk.Label(lf_source, text="基础URL:").grid(row=1, column=0, sticky="w", pady=5)
        self.url_var = tk.StringVar()
        ttk.Entry(lf_source, textvariable=self.url_var, width=50).grid(row=1, column=1, columnspan=2, sticky="w")
        ttk.Label(lf_source, text="(例如: https://oj.example.com)").grid(row=1, column=3, sticky="w", padx=5)

        ttk.Label(lf_source, text="比赛ID:").grid(row=2, column=0, sticky="w", pady=5)
        self.contest_id_var = tk.StringVar()
        ttk.Entry(lf_source, textvariable=self.contest_id_var, width=20).grid(row=2, column=1, sticky="w")

        ttk.Label(lf_source, text="认证凭证:").grid(row=3, column=0, sticky="w", pady=5)
        self.auth_var = tk.StringVar()
        ttk.Entry(lf_source, textvariable=self.auth_var, width=50, show="*").grid(row=3, column=1, columnspan=2, sticky="w")
        ttk.Label(lf_source, text="(Hydro填SID, XCPC填API Key)").grid(row=3, column=3, sticky="w", padx=5)

        # 2. Target Config
        lf_target = ttk.LabelFrame(self.root, text="目标后台配置", padding=10)
        lf_target.pack(fill="x", padx=10, pady=5)

        ttk.Label(lf_target, text="后台API域名:").grid(row=0, column=0, sticky="w", pady=5)
        self.target_url_var = tk.StringVar(value="http://localhost:3403")
        ttk.Entry(lf_target, textvariable=self.target_url_var, width=40).grid(row=0, column=1, sticky="w")

        ttk.Label(lf_target, text="目标外榜ID:").grid(row=0, column=2, sticky="w", padx=10)
        self.target_event_id_var = tk.StringVar()
        ttk.Entry(lf_target, textvariable=self.target_event_id_var, width=10).grid(row=0, column=3, sticky="w")

        ttk.Label(lf_target, text="后台API Key:").grid(row=1, column=0, sticky="w", pady=5)
        self.target_key_var = tk.StringVar()
        ttk.Entry(lf_target, textvariable=self.target_key_var, width=50, show="*").grid(row=1, column=1, columnspan=3, sticky="w")

        # 3. Control
        lf_ctrl = ttk.LabelFrame(self.root, text="控制", padding=10)
        lf_ctrl.pack(fill="x", padx=10, pady=5)

        ttk.Label(lf_ctrl, text="轮询间隔(秒):").pack(side="left")
        self.interval_var = tk.IntVar(value=10)
        ttk.Entry(lf_ctrl, textvariable=self.interval_var, width=8).pack(side="left", padx=5)

        self.btn_start = ttk.Button(lf_ctrl, text="▶ 开始同步", command=self.start_sync)
        self.btn_start.pack(side="left", padx=20)
        
        self.btn_stop = ttk.Button(lf_ctrl, text="⏹ 停止", command=self.stop_sync, state="disabled")
        self.btn_stop.pack(side="left")

        # 4. Log
        lf_log = ttk.LabelFrame(self.root, text="日志", padding=5)
        lf_log.pack(fill="both", expand=True, padx=10, pady=5)
        self.log_text = scrolledtext.ScrolledText(lf_log, state='disabled', bg='black', fg='lime', font=('Consolas', 9))
        self.log_text.pack(fill="both", expand=True)

    def start_sync(self):
        if self.is_running: return
        
        source_type = self.source_type.get()
        base_url = self.url_var.get().strip()
        contest_id = self.contest_id_var.get().strip()
        auth = self.auth_var.get().strip()
        
        target_url = self.target_url_var.get().strip()
        target_event_id = self.target_event_id_var.get().strip()
        target_key = self.target_key_var.get().strip()
        
        # Auto-fix URLs
        if base_url and not base_url.startswith(('http://', 'https://')):
            base_url = 'https://' + base_url
        if target_url and not target_url.startswith(('http://', 'https://')):
            target_url = 'https://' + target_url
        
        if not all([base_url, contest_id, auth, target_url, target_event_id, target_key]):
            messagebox.showerror("错误", "请填写所有必填字段")
            return

        config = {
            "source_type": source_type,
            "cookie_sid": auth if source_type == 'hydro' else '',
            "source_api_key": auth if source_type == 'xcpc' else '',
            "target_url": target_url.rstrip('/'),
            "target_event_id": target_event_id,
            "target_api_key": target_key,
            "interval": self.interval_var.get()
        }

        if source_type == 'hydro':
            self.client = HydroClient(base_url, contest_id, config)
        else:
            self.client = XcpcClient(base_url, contest_id, config)

        self.is_running = True
        self.btn_start.config(state="disabled")
        self.btn_stop.config(state="normal")
        
        self.sync_thread = threading.Thread(target=self.run_loop, args=(config,), daemon=True)
        self.sync_thread.start()

    def stop_sync(self):
        self.is_running = False
        self.btn_start.config(state="normal")
        self.btn_stop.config(state="disabled")
        logging.info("正在停止...")

    def run_loop(self, config):
        logging.info(f"同步任务启动，源: {config['source_type'].upper()}, 目标外榜ID: {config['target_event_id']}")
        
        while self.is_running:
            try:
                # 1. Fetch & Send Problems
                logging.info("正在获取题目信息...")
                problems = self.client.fetch_problems()
                if problems:
                    logging.info(f"获取到 {len(problems)} 个题目，正在发送...")
                    self.send_to_backend(problems, "problems", config)
                else:
                    logging.warning("未获取到题目或题目列表为空")

                # 2. Fetch & Send Submissions
                logging.info("正在获取提交记录...")
                submissions = self.client.fetch_submissions()
                if submissions:
                    logging.info(f"获取到 {len(submissions)} 条提交记录，正在发送...")
                    self.send_to_backend(submissions, "submissions", config)
                else:
                    logging.info("未获取到提交记录")

                logging.info(f"本轮同步完成，休眠 {config['interval']} 秒...")
            except Exception as e:
                logging.error(f"同步过程发生错误: {e}")

            # Sleep loop
            for _ in range(config['interval']):
                if not self.is_running: break
                time.sleep(1)

        logging.info("同步任务已停止")

    def send_to_backend(self, data, data_type, config):
        base_url = config['target_url']
        payload = {
            "api_key": config['target_api_key'],
            data_type: data
        }
        
        # Strategy:
        # 1. If user provided /api suffix, respect it.
        # 2. Else, try /api/external/... (Nginx proxy usually)
        # 3. If 404/405, fallback to /external/... (Direct backend access)
        
        target_url = None
        if base_url.endswith('/api'):
            target_url = f"{base_url}/external/board/{config['target_event_id']}/{data_type}"
        else:
            target_url = f"{base_url}/api/external/board/{config['target_event_id']}/{data_type}"
            
        try:
            resp = requests.post(target_url, json=payload, timeout=60)
            
            # If 404 (Not Found) or 405 (Method Not Allowed - likely static file server), try fallback
            if (resp.status_code == 404 or resp.status_code == 405) and not base_url.endswith('/api'):
                fallback_url = f"{base_url}/external/board/{config['target_event_id']}/{data_type}"
                logging.info(f"[{data_type}] 路径 {target_url} 返回 {resp.status_code}，尝试回退路径: {fallback_url}")
                resp = requests.post(fallback_url, json=payload, timeout=60)

            if resp.status_code == 200:
                res_json = resp.json()
                if res_json.get('code') == 200:
                    logging.info(f"[{data_type}] 推送成功: {res_json.get('data')}")
                else:
                    logging.error(f"[{data_type}] 推送业务失败: {res_json.get('msg')}")
            else:
                logging.error(f"[{data_type}] 推送HTTP失败: {resp.status_code} {resp.text[:200]}")
        except Exception as e:
            logging.error(f"[{data_type}] 推送网络错误: {e}")

if __name__ == "__main__":
    root = tk.Tk()
    app = SpiderApp(root)
    root.mainloop()
