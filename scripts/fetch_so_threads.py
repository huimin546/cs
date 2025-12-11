#!/usr/bin/env python3
"""Stack Overflow Java 線程爬蟲：可直接寫入 PostgreSQL 或輸出 JSON。"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence

import requests
from dotenv import load_dotenv

try:
    import psycopg
except ImportError as exc:  # pragma: no cover - 只在缺少模組時觸發
    raise SystemExit(
        "請先安裝 psycopg： pip install psycopg[binary]"
    ) from exc

API_ROOT = "https://api.stackexchange.com/2.3"
DEFAULT_FILTER = "withbody"


def call_api(path: str, params: Dict[str, Any]) -> Dict[str, Any]:
    key = os.getenv("STACKOVERFLOW_KEY")
    token = os.getenv("STACKOVERFLOW_ACCESS_TOKEN")

    if not key:
        raise SystemExit("請設定 STACKOVERFLOW_KEY 環境變數後再執行。")

    full_params = {
        "site": "stackoverflow",
        "key": key,
        **params,
    }
    if token:
        full_params["access_token"] = token

    url = f"{API_ROOT}{path}"
    resp = requests.get(url, params={k: v for k, v in full_params.items() if v is not None}, timeout=30)
    if resp.status_code != 200:
        raise RuntimeError(f"Stack Overflow API 呼叫失敗: {resp.status_code} {resp.text}")
    return resp.json()


def fetch_questions(page: int, page_size: int, fromdate: Optional[int]) -> List[Dict[str, Any]]:
    payload = {
        "tagged": "java",
        "filter": DEFAULT_FILTER,
        "page": page,
        "pagesize": page_size,
        "order": "desc",
        "sort": "creation",
        "fromdate": fromdate,
    }
    data = call_api("/questions", payload)
    return data.get("items", [])


def fetch_answers(question_id: int) -> List[Dict[str, Any]]:
    payload = {
        "filter": DEFAULT_FILTER,
        "pagesize": 100,
        "order": "desc",
        "sort": "votes",
    }
    data = call_api(f"/questions/{question_id}/answers", payload)
    return data.get("items", [])


def fetch_question_comments(question_id: int) -> List[Dict[str, Any]]:
    payload = {
        "filter": DEFAULT_FILTER,
        "pagesize": 100,
        "order": "desc",
        "sort": "creation",
    }
    data = call_api(f"/questions/{question_id}/comments", payload)
    return data.get("items", [])


def fetch_answer_comments(answer_ids: Sequence[int]) -> Dict[str, List[Dict[str, Any]]]:
    comments: Dict[str, List[Dict[str, Any]]] = {}
    for answer_id in answer_ids:
        payload = {
            "filter": DEFAULT_FILTER,
            "pagesize": 100,
            "order": "desc",
            "sort": "creation",
        }
        data = call_api(f"/answers/{answer_id}/comments", payload)
        comments[str(answer_id)] = data.get("items", [])
        time.sleep(0.2)
    return comments


def persist_thread_json(output_dir: pathlib.Path, thread: Dict[str, Any]) -> None:
    question_id = thread.get("question", {}).get("question_id")
    if not question_id:
        return
    output_dir.mkdir(parents=True, exist_ok=True)
    output_file = output_dir / f"thread_{question_id}.json"
    with output_file.open("w", encoding="utf-8") as fh:
        json.dump(thread, fh, ensure_ascii=False, indent=2)


def parse_jdbc_url(jdbc_url: str) -> str:
    if jdbc_url.startswith("jdbc:"):
        return jdbc_url[5:]
    return jdbc_url


def to_instant(epoch: Optional[int]) -> Optional[dt.datetime]:
    if epoch is None:
        return None
    return dt.datetime.fromtimestamp(epoch, tz=dt.timezone.utc)


@dataclass
class DbConfig:
    url: str
    username: str
    password: str


def load_db_config(args: argparse.Namespace) -> DbConfig:
    url = args.db_url or os.getenv("SPRING_DATASOURCE_URL")
    user = args.db_user or os.getenv("SPRING_DATASOURCE_USERNAME")
    password = args.db_password or os.getenv("SPRING_DATASOURCE_PASSWORD")
    if not all([url, user, password]):
        raise SystemExit("請提供 PostgreSQL 連線資訊（可透過 --db-* 參數或 SPRING_DATASOURCE_* 環境變數）")
    return DbConfig(url=parse_jdbc_url(str(url)), username=str(user), password=str(password))


class ThreadDbWriter:
    def __init__(self, cfg: DbConfig):
        self.conn = psycopg.connect(cfg.url, user=cfg.username, password=cfg.password, autocommit=False)

    def close(self) -> None:
        self.conn.close()

    def store_thread(self, thread: Dict[str, Any]) -> None:
        question = thread.get("question")
        if not question or not question.get("question_id"):
            return

        answers = thread.get("answers", [])
        q_comments = thread.get("question_comments", [])
        a_comments = thread.get("answer_comments", {})

        with self.conn.cursor() as cur:
            self._insert_question(cur, question)
            tag_ids = self._ensure_tags(cur, question.get("tags", []))
            self._link_question_tags(cur, question["question_id"], tag_ids)

            for answer in answers:
                ans_id = answer.get("answer_id")
                if not ans_id:
                    continue
                self._insert_answer(cur, question["question_id"], answer)
                for comment in a_comments.get(str(ans_id), []):
                    self._insert_answer_comment(cur, ans_id, comment)

            for comment in q_comments:
                self._insert_question_comment(cur, question["question_id"], comment)

        self.conn.commit()

    def _insert_question(self, cur, question: Dict[str, Any]) -> None:
        owner = question.get("owner") or {}
        cur.execute(
            """
            INSERT INTO questions (
                id, title, body, answered, view_count, answer_count, score, question_link,
                creation_date, last_activity_date, closed_date, closed_reason, accepted_answer_id,
                owner_user_id, owner_reputation, owner_display_name, owner_profile_image, owner_link
            ) VALUES (
                %(id)s, %(title)s, %(body)s, %(answered)s, %(view_count)s, %(answer_count)s, %(score)s, %(link)s,
                %(creation)s, %(last_activity)s, %(closed)s, %(closed_reason)s, %(accepted_answer_id)s,
                %(owner_user_id)s, %(owner_reputation)s, %(owner_display_name)s, %(owner_profile_image)s, %(owner_link)s
            )
            ON CONFLICT (id) DO UPDATE SET last_activity_date = EXCLUDED.last_activity_date
            """,
            {
                "id": question.get("question_id"),
                "title": question.get("title"),
                "body": question.get("body"),
                "answered": question.get("is_answered"),
                "view_count": question.get("view_count"),
                "answer_count": question.get("answer_count"),
                "score": question.get("score"),
                "link": question.get("link"),
                "creation": to_instant(question.get("creation_date")),
                "last_activity": to_instant(question.get("last_activity_date")),
                "closed": to_instant(question.get("closed_date")),
                "closed_reason": question.get("closed_reason"),
                "accepted_answer_id": question.get("accepted_answer_id"),
                "owner_user_id": owner.get("user_id"),
                "owner_reputation": owner.get("reputation"),
                "owner_display_name": owner.get("display_name"),
                "owner_profile_image": owner.get("profile_image"),
                "owner_link": owner.get("link"),
            },
        )

    def _insert_answer(self, cur, question_id: int, answer: Dict[str, Any]) -> None:
        owner = answer.get("owner") or {}
        cur.execute(
            """
            INSERT INTO answers (
                id, question_id, body, accepted, score, creation_date, last_activity_date,
                owner_user_id, owner_reputation, owner_display_name, owner_profile_image, owner_link
            ) VALUES (
                %(id)s, %(question_id)s, %(body)s, %(accepted)s, %(score)s, %(creation)s, %(last_activity)s,
                %(owner_user_id)s, %(owner_reputation)s, %(owner_display_name)s, %(owner_profile_image)s, %(owner_link)s
            )
            ON CONFLICT (id) DO UPDATE SET last_activity_date = EXCLUDED.last_activity_date
            """,
            {
                "id": answer.get("answer_id"),
                "question_id": question_id,
                "body": answer.get("body"),
                "accepted": answer.get("is_accepted"),
                "score": answer.get("score"),
                "creation": to_instant(answer.get("creation_date")),
                "last_activity": to_instant(answer.get("last_activity_date")),
                "owner_user_id": owner.get("user_id"),
                "owner_reputation": owner.get("reputation"),
                "owner_display_name": owner.get("display_name"),
                "owner_profile_image": owner.get("profile_image"),
                "owner_link": owner.get("link"),
            },
        )

    def _insert_question_comment(self, cur, question_id: int, comment: Dict[str, Any]) -> None:
        owner = comment.get("owner") or {}
        cur.execute(
            """
            INSERT INTO question_comments (
                id, question_id, body, score, creation_date,
                owner_user_id, owner_reputation, owner_display_name, owner_profile_image, owner_link
            ) VALUES (
                %(id)s, %(question_id)s, %(body)s, %(score)s, %(creation)s,
                %(owner_user_id)s, %(owner_reputation)s, %(owner_display_name)s, %(owner_profile_image)s, %(owner_link)s
            )
            ON CONFLICT (id) DO NOTHING
            """,
            {
                "id": comment.get("comment_id"),
                "question_id": question_id,
                "body": comment.get("body"),
                "score": comment.get("score"),
                "creation": to_instant(comment.get("creation_date")),
                "owner_user_id": owner.get("user_id"),
                "owner_reputation": owner.get("reputation"),
                "owner_display_name": owner.get("display_name"),
                "owner_profile_image": owner.get("profile_image"),
                "owner_link": owner.get("link"),
            },
        )

    def _insert_answer_comment(self, cur, answer_id: int, comment: Dict[str, Any]) -> None:
        owner = comment.get("owner") or {}
        cur.execute(
            """
            INSERT INTO answer_comments (
                id, answer_id, body, score, creation_date,
                owner_user_id, owner_reputation, owner_display_name, owner_profile_image, owner_link
            ) VALUES (
                %(id)s, %(answer_id)s, %(body)s, %(score)s, %(creation)s,
                %(owner_user_id)s, %(owner_reputation)s, %(owner_display_name)s, %(owner_profile_image)s, %(owner_link)s
            )
            ON CONFLICT (id) DO NOTHING
            """,
            {
                "id": comment.get("comment_id"),
                "answer_id": answer_id,
                "body": comment.get("body"),
                "score": comment.get("score"),
                "creation": to_instant(comment.get("creation_date")),
                "owner_user_id": owner.get("user_id"),
                "owner_reputation": owner.get("reputation"),
                "owner_display_name": owner.get("display_name"),
                "owner_profile_image": owner.get("profile_image"),
                "owner_link": owner.get("link"),
            },
        )

    def _ensure_tags(self, cur, tags: Sequence[str]) -> List[int]:
        tag_ids: List[int] = []
        for tag in tags or []:
            if not tag:
                continue
            cur.execute(
                """
                INSERT INTO tags (name) VALUES (%(name)s)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """,
                {"name": tag.lower()},
            )
            tag_id = cur.fetchone()[0]
            tag_ids.append(tag_id)
        return tag_ids

    def _link_question_tags(self, cur, question_id: int, tag_ids: Sequence[int]) -> None:
        for tag_id in tag_ids:
            cur.execute(
                """
                INSERT INTO question_tags (question_id, tag_id)
                VALUES (%(question_id)s, %(tag_id)s)
                ON CONFLICT DO NOTHING
                """,
                {"question_id": question_id, "tag_id": tag_id},
            )


def build_thread_payload(question: Dict[str, Any], answers: List[Dict[str, Any]],
                         q_comments: List[Dict[str, Any]], a_comments: Dict[str, List[Dict[str, Any]]]) -> Dict[str, Any]:
    return {
        "question": question,
        "answers": answers,
        "question_comments": q_comments,
        "answer_comments": a_comments,
    }


def main() -> None:
    load_dotenv()

    parser = argparse.ArgumentParser(description="下載 Stack Overflow Java 線程資料")
    parser.add_argument("--target", type=int, default=1000, help="希望蒐集的 thread 數量 (預設 1000)")
    parser.add_argument("--page-size", type=int, default=50, help="每頁抓取問題數 (最大 100)")
    parser.add_argument("--fromdate", type=int, default=None, help="僅抓取該 Unix epoch 秒之後的問題")
    parser.add_argument("--store", choices=["db", "file"], default="db", help="資料儲存方式")
    parser.add_argument("--out", type=pathlib.Path, default=pathlib.Path("data/raw"), help="store=file 時輸出資料夾")
    parser.add_argument("--db-url", type=str, help="PostgreSQL 連線字串 (可直接使用 SPRING_DATASOURCE_URL)")
    parser.add_argument("--db-user", type=str, help="PostgreSQL 使用者")
    parser.add_argument("--db-password", type=str, help="PostgreSQL 密碼")
    args = parser.parse_args()

    writer: Optional[ThreadDbWriter] = None
    if args.store == "db":
        cfg = load_db_config(args)
        writer = ThreadDbWriter(cfg)

    collected = 0
    page = 1
    try:
        while collected < args.target:
            questions = fetch_questions(page, args.page_size, args.fromdate)
            if not questions:
                print("⚠️ 沒有更多問題可抓，提前結束")
                break
            for question in questions:
                if collected >= args.target:
                    break
                qid = question.get("question_id")
                if qid is None:
                    continue
                answers = fetch_answers(qid)
                answer_ids: List[int] = []
                for ans in answers:
                    ans_id = ans.get("answer_id")
                    if ans_id is None:
                        continue
                    answer_ids.append(int(ans_id))
                q_comments = fetch_question_comments(qid)
                a_comments = fetch_answer_comments(answer_ids) if answer_ids else {}
                thread_payload = build_thread_payload(question, answers, q_comments, a_comments)

                if args.store == "db" and writer:
                    writer.store_thread(thread_payload)
                else:
                    persist_thread_json(args.out, thread_payload)

                collected += 1
                print(f"✅ 下載 thread {qid} （目前 {collected}/{args.target}）")
                time.sleep(0.3)
            page += 1
            time.sleep(0.5)
    finally:
        if writer:
            writer.close()

    if args.store == "file":
        print(f"完成，共蒐集 {collected} 筆 thread。輸出資料夾：{args.out}")
    else:
        print(f"完成，共蒐集 {collected} 筆 thread，已直接寫入資料庫。")


if __name__ == "__main__":
    main()
