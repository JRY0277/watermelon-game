# -*- coding: utf-8 -*-
"""
合成大西瓜 · 排行榜后端
零依赖：只用 Python 标准库 + SQLite（无需 pip install）

启动：
    python server.py            默认 8000 端口
    python server.py 8080       指定端口

启动后：
    本机玩       http://localhost:8000
    手机玩       连同一个 WiFi，浏览器打开 http://<你电脑的IP>:8000
    排行榜数据    game.db（Navicat 可直接打开：新建连接 -> SQLite -> 选 game.db）

接口：
    GET  /api/rank?limit=20     排行榜前 N 名
    POST /api/score             提交成绩 {"nickname":"小明","score":1234,"max_level":9,"duration":95}
    GET  /api/stats             汇总统计（人数、局数、平均分、最高分）
"""
import os
import sys
import json
import sqlite3
import urllib.parse
from datetime import datetime
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, 'game.db')
MAX_LIMIT = 100


def find_html():
    for name in sorted(os.listdir(BASE_DIR)):
        if name.lower().endswith('.html'):
            return name
    return None


HTML_FILE = find_html()


def connect():
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    return con


def init_db():
    con = connect()
    con.execute('''CREATE TABLE IF NOT EXISTS scores (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        nickname   TEXT    NOT NULL DEFAULT '匿名玩家',
        score      INTEGER NOT NULL DEFAULT 0,
        max_level  INTEGER NOT NULL DEFAULT 0,
        duration   INTEGER NOT NULL DEFAULT 0,
        created_at TEXT    NOT NULL
    )''')
    con.execute('CREATE INDEX IF NOT EXISTS idx_scores_score ON scores(score DESC)')
    con.commit()
    con.close()


def query(sql, args=()):
    con = connect()
    try:
        cur = con.execute(sql, args)
        rows = [dict(r) for r in cur.fetchall()]
        return rows
    finally:
        con.close()


def execute(sql, args=()):
    con = connect()
    try:
        cur = con.execute(sql, args)
        con.commit()
        return cur.lastrowid
    finally:
        con.close()


class Handler(SimpleHTTPRequestHandler):
    server_version = 'MergeGame/1.0'

    def __init__(self, *args, **kwargs):
        kwargs['directory'] = BASE_DIR
        super().__init__(*args, **kwargs)

    # ---------- 工具 ----------
    def _cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.send_header('Access-Control-Allow-Methods', 'GET,POST,OPTIONS')

    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _path(self):
        return urllib.parse.urlparse(self.path).path

    def log_message(self, fmt, *args):
        # 只打印 API 请求，静态文件太吵
        if '/api/' in (self.path or ''):
            sys.stderr.write('%s - %s\n' % (self.address_string(), fmt % args))

    # ---------- 路由 ----------
    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        p = self._path()
        if p.endswith('/api/rank'):
            return self.api_rank()
        if p.endswith('/api/stats'):
            return self.api_stats()
        if p in ('/', ''):
            if HTML_FILE:
                self.path = '/' + urllib.parse.quote(HTML_FILE)
            else:
                return self._json({'error': '目录里没有 html 文件'}, 404)
        return super().do_GET()

    def do_POST(self):
        p = self._path()
        if p.endswith('/api/score'):
            return self.api_submit()
        return self._json({'error': 'not found'}, 404)

    # ---------- API ----------
    def api_rank(self):
        q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        try:
            limit = min(int(q.get('limit', ['20'])[0]), MAX_LIMIT)
        except ValueError:
            limit = 20
        rows = query(
            'SELECT nickname, score, max_level, duration, created_at '
            'FROM scores ORDER BY score DESC, id ASC LIMIT ?', (limit,))
        for i, r in enumerate(rows):
            r['rank'] = i + 1
        return self._json({'ok': True, 'list': rows})

    def api_stats(self):
        row = query('SELECT COUNT(*) AS rounds, COUNT(DISTINCT nickname) AS players, '
                    'COALESCE(MAX(score),0) AS best, COALESCE(CAST(AVG(score) AS INTEGER),0) AS avg '
                    'FROM scores')
        return self._json({'ok': True, 'stats': row[0] if row else {}})

    def api_submit(self):
        try:
            length = int(self.headers.get('Content-Length') or 0)
            data = json.loads(self.rfile.read(length).decode('utf-8')) if length else {}
        except Exception:
            return self._json({'ok': False, 'error': '数据格式不对'}, 400)

        nickname = str(data.get('nickname', '')).strip()[:12] or '匿名玩家'
        try:
            score = int(data.get('score', 0))
            max_level = int(data.get('max_level', 0))
            duration = int(data.get('duration', 0))
        except (TypeError, ValueError):
            return self._json({'ok': False, 'error': '分数必须是数字'}, 400)

        if score < 0 or score > 10 ** 7 or max_level < 0 or max_level > 99:
            return self._json({'ok': False, 'error': '分数超出合理范围'}, 400)

        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        new_id = execute(
            'INSERT INTO scores (nickname, score, max_level, duration, created_at) '
            'VALUES (?,?,?,?,?)', (nickname, score, max_level, duration, now))

        better = query('SELECT COUNT(*) AS c FROM scores WHERE score > ?', (score,))[0]['c']
        return self._json({'ok': True, 'id': new_id, 'rank': better + 1})


def main():
    init_db()
    port = int(sys.argv[1]) if len(sys.argv) > 1 else int(os.environ.get('PORT', 8000))
    httpd = ThreadingHTTPServer(('0.0.0.0', port), Handler)

    import socket
    try:
        lan = socket.gethostbyname(socket.gethostname())
    except Exception:
        lan = '127.0.0.1'

    print('=' * 52)
    print(' 合成大西瓜 · 排行榜服务已启动')
    print(' 数据库: %s' % DB_PATH)
    print(' 本机玩: http://localhost:%d' % port)
    print(' 手机玩: http://%s:%d  (手机连同一个 WiFi)' % (lan, port))
    print(' 停止:   Ctrl+C')
    print('=' * 52)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print('\n已停止')
        httpd.server_close()


if __name__ == '__main__':
    main()
