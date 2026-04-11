#!/usr/bin/env python3
"""
Crawl image/video from Bing, upload to S3, then update story items in DB.

Requirements:
    pip install psycopg2-binary boto3 yt-dlp

Usage:
    python scripts/crawl_story_media_from_bing.py --dry-run --limit 20
    python scripts/crawl_story_media_from_bing.py

Database env vars (with defaults):
    DB_HOST=localhost
    DB_PORT=5432
    DB_NAME=media_service
    DB_USER=postgres
    DB_PASSWORD=postgres

S3 env vars:
    AWS_S3_BUCKET=<bucket_name>                     (required)
    AWS_DEFAULT_REGION=ap-southeast-1               (default)
    AWS_ACCESS_KEY_ID=<access_key>                  (required)
    AWS_SECRET_ACCESS_KEY=<secret_key>              (required)
    AWS_S3_USE_LOCAL=false                          (default)
    AWS_S3_ENDPOINT_URL=http://localhost:4566       (used when AWS_S3_USE_LOCAL=true)
"""

from __future__ import annotations

import argparse
import html
import io
import mimetypes
import os
import re
import sys
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Optional


VIDEO_SEARCH_TEMPLATE = "https://www.bing.com/videos/search?q={query}&FORM=HDRSC4"
IMAGE_SEARCH_TEMPLATE = "https://www.bing.com/images/search?q={query}&form=HDRSC3&first=1"

IMAGE_QUERY_VARIANTS = [
    "{keyword}",
    "{keyword} photo",
    "{keyword} wallpaper",
    "beautiful landscape",
]

VIDEO_QUERY_VARIANTS = [
    "{keyword}",
    "{keyword} stock footage",
    "{keyword} mp4",
    "sample video mp4",
]

FALLBACK_IMAGE_URLS = [
    "https://picsum.photos/seed/story-image-1/1080/1920",
    "https://picsum.photos/seed/story-image-2/1080/1920",
]

FALLBACK_VIDEO_URLS = [
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
]

REQUEST_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/123.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "vi,en-US;q=0.9,en;q=0.8",
}


@dataclass
class StoryMediaItem:
    item_id: str
    item_type: str
    item_name: str


@dataclass
class DownloadedMedia:
    content: bytes
    content_type: str
    extension: str


APP_PROPERTIES_PATH = os.path.join(
    os.path.dirname(os.path.dirname(__file__)),
    "src",
    "main",
    "resources",
    "application.properties",
)


def clean_keyword(value: str) -> str:
    value = (value or "").strip()
    if not value:
        return "story"

    value = re.sub(r"\s+", " ", value)
    return value


def parse_properties_file(path: str) -> dict[str, str]:
    props: dict[str, str] = {}
    if not os.path.exists(path):
        return props

    with open(path, "r", encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, val = line.split("=", 1)
            props[key.strip()] = val.strip()

    return props


def parse_jdbc_url(jdbc_url: str) -> dict[str, str]:
    # Example: jdbc:postgresql://host:5432/db?sslmode=require
    prefix = "jdbc:postgresql://"
    if not jdbc_url or not jdbc_url.startswith(prefix):
        return {}

    raw = jdbc_url[len(prefix):]
    if "/" not in raw:
        return {}

    host_port, db_and_query = raw.split("/", 1)
    if "?" in db_and_query:
        dbname, query = db_and_query.split("?", 1)
    else:
        dbname, query = db_and_query, ""

    if ":" in host_port:
        host, port = host_port.rsplit(":", 1)
    else:
        host, port = host_port, "5432"

    out = {
        "host": host,
        "port": port,
        "dbname": dbname,
    }

    if query:
        query_params = urllib.parse.parse_qs(query, keep_blank_values=True)
        sslmode = query_params.get("sslmode", [""])[0]
        if sslmode:
            out["sslmode"] = sslmode

    return out


def resolve_app_config() -> dict[str, str]:
    return parse_properties_file(APP_PROPERTIES_PATH)


def fetch_html(url: str, timeout: int = 15) -> str:
    req = urllib.request.Request(url, headers=REQUEST_HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    return raw.decode("utf-8", errors="ignore")


def fetch_binary(url: str, timeout: int = 30) -> tuple[bytes, str]:
    req = urllib.request.Request(url, headers=REQUEST_HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        content_type = resp.headers.get("Content-Type", "application/octet-stream")
    return raw, content_type


def decode_escaped_url(raw: str) -> str:
    value = html.unescape(raw)
    value = value.replace("\\/", "/")
    value = value.replace("\\u002f", "/")
    value = value.replace("\\u003a", ":")
    return value


def sanitize_file_stem(value: str, fallback: str = "story") -> str:
    val = clean_keyword(value).lower()
    val = re.sub(r"[^a-z0-9]+", "-", val)
    val = re.sub(r"-{2,}", "-", val).strip("-")
    return val or fallback


def guess_extension_from_content_type(content_type: str) -> str:
    main = content_type.split(";", 1)[0].strip().lower()
    if main == "image/jpeg":
        return ".jpg"
    if main == "image/png":
        return ".png"
    if main == "image/webp":
        return ".webp"
    if main == "image/gif":
        return ".gif"
    if main == "video/mp4":
        return ".mp4"
    if main in {"video/quicktime", "video/mov"}:
        return ".mov"
    if main == "video/webm":
        return ".webm"
    guessed = mimetypes.guess_extension(main)
    return guessed or ""


def guess_extension_from_url(raw_url: str) -> str:
    path = urllib.parse.urlparse(raw_url).path
    ext = PurePosixPath(path).suffix.lower()
    if len(ext) > 8:
        return ""
    return ext


def normalize_content_type(content_type: str) -> str:
    base = (content_type or "").split(";", 1)[0].strip().lower()
    return base or "application/octet-stream"


def download_media(url: str, expected: str) -> DownloadedMedia:
    data, content_type = fetch_binary(url)
    normalized = normalize_content_type(content_type)

    if expected == "image" and not normalized.startswith("image/"):
        raise RuntimeError(f"URL is not image content: {url} ({normalized})")
    if expected == "video" and not normalized.startswith("video/"):
        raise RuntimeError(f"URL is not video content: {url} ({normalized})")

    ext = guess_extension_from_content_type(normalized) or guess_extension_from_url(url)
    if not ext:
        ext = ".bin"

    return DownloadedMedia(content=data, content_type=normalized, extension=ext)


def resolve_video_url_for_download(url: str) -> str:
    """
    Some Bing results return webpage URLs (e.g., YouTube watch page) instead of direct video.
    Try extracting a direct media URL using yt-dlp.
    """
    try:
        import yt_dlp  # type: ignore
    except ImportError as exc:
        raise RuntimeError(
            "Video URL is not direct media. Install yt-dlp: pip install yt-dlp"
        ) from exc

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "format": "best[ext=mp4]/best",
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)

    if info is None:
        raise RuntimeError(f"Cannot extract playable stream from URL: {url}")

    if "entries" in info and info["entries"]:
        first = info["entries"][0]
        if first and first.get("url"):
            return str(first["url"])

    direct = info.get("url")
    if direct:
        return str(direct)

    raise RuntimeError(f"Cannot resolve direct stream URL: {url}")


def download_video_with_fallback(url: str) -> DownloadedMedia:
    try:
        return download_media(url, expected="video")
    except RuntimeError as first_error:
        # Fallback for webpage URLs (youtube, etc.)
        direct_url = resolve_video_url_for_download(url)
        try:
            return download_media(direct_url, expected="video")
        except Exception as second_error:
            raise RuntimeError(
                f"Failed to download video from '{url}'. First error: {first_error}; "
                f"Fallback error: {second_error}"
            ) from second_error


def extract_first_image_url(page_html: str) -> Optional[str]:
    candidates = extract_image_candidates(page_html)
    return candidates[0] if candidates else None


def extract_image_candidates(page_html: str, max_candidates: int = 30) -> list[str]:
    patterns = [
        r'"murl":"(https?://[^"\\]+(?:\\.[^"\\]+)*)"',
        r"murl&quot;:&quot;(https?://[^&]+)&quot;",
        r'<img[^>]+src="(https?://[^"]+)"',
    ]
    out: list[str] = []
    seen: set[str] = set()
    for pattern in patterns:
        for match in re.finditer(pattern, page_html):
            url = decode_escaped_url(match.group(1)).strip()
            if not url or url in seen:
                continue
            seen.add(url)
            out.append(url)
            if len(out) >= max_candidates:
                return out
    return out


def extract_first_video_url(page_html: str) -> Optional[str]:
    candidates = extract_video_candidates(page_html)
    return candidates[0] if candidates else None


def extract_video_candidates(page_html: str, max_candidates: int = 20) -> list[str]:
    patterns = [
        r'"contentUrl":"(https?://[^"\\]+(?:\\.[^"\\]+)*)"',
        r'"mediaUrl":"(https?://[^"\\]+(?:\\.[^"\\]+)*)"',
        r'class="[^"]*mc_vtvc_link[^"]*"[^>]*href="(https?://[^"]+)"',
    ]
    out: list[str] = []
    seen: set[str] = set()
    for pattern in patterns:
        for match in re.finditer(pattern, page_html):
            url = decode_escaped_url(match.group(1)).strip()
            if not url or url in seen:
                continue
            seen.add(url)
            out.append(url)
            if len(out) >= max_candidates:
                return out
    return out


def looks_like_direct_video_url(url: str) -> bool:
    path = urllib.parse.urlparse(url).path.lower()
    return path.endswith((".mp4", ".webm", ".mov", ".m4v", ".avi", ".mkv"))


def get_db_connection():
    try:
        import psycopg2  # type: ignore
    except ImportError as exc:
        raise RuntimeError(
            "Missing dependency psycopg2-binary. Install with: pip install psycopg2-binary"
        ) from exc

    app = resolve_app_config()
    jdbc_url = app.get("spring.datasource.url", "")
    jdbc = parse_jdbc_url(jdbc_url)

    host = os.getenv("DB_HOST", jdbc.get("host", "localhost"))
    port = int(os.getenv("DB_PORT", jdbc.get("port", "5432")))
    dbname = os.getenv("DB_NAME", jdbc.get("dbname", "media_service"))
    user = os.getenv("DB_USER", app.get("spring.datasource.username", "postgres"))
    password = os.getenv("DB_PASSWORD", app.get("spring.datasource.password", "postgres"))
    sslmode = os.getenv("DB_SSLMODE", jdbc.get("sslmode", ""))

    connect_kwargs = {
        "host": host,
        "port": port,
        "dbname": dbname,
        "user": user,
        "password": password,
    }
    if sslmode:
        connect_kwargs["sslmode"] = sslmode

    return psycopg2.connect(**connect_kwargs)


def get_s3_client_and_bucket():
    try:
        import boto3  # type: ignore
    except ImportError as exc:
        raise RuntimeError(
            "Missing dependency boto3. Install with: pip install boto3"
        ) from exc

    app = resolve_app_config()

    bucket = os.getenv("AWS_S3_BUCKET", app.get("aws.s3.bucket-name", "")).strip()
    if not bucket:
        raise RuntimeError("Missing AWS_S3_BUCKET environment variable")

    region = os.getenv("AWS_DEFAULT_REGION", app.get("aws.s3.region", "ap-southeast-1"))
    access_key = os.getenv("AWS_ACCESS_KEY_ID", app.get("aws.s3.access-key", "")).strip()
    secret_key = os.getenv("AWS_SECRET_ACCESS_KEY", app.get("aws.s3.secret-key", "")).strip()
    use_local_raw = os.getenv("AWS_S3_USE_LOCAL", app.get("aws.s3.use-local", "false"))
    use_local = use_local_raw.strip().lower() == "true"
    endpoint = os.getenv("AWS_S3_ENDPOINT_URL", app.get("aws.s3.local-endpoint", "http://localhost:4566")).strip()

    if not access_key or not secret_key:
        raise RuntimeError(
            "Missing AWS credentials. Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY"
        )

    kwargs = {
        "region_name": region,
        "aws_access_key_id": access_key,
        "aws_secret_access_key": secret_key,
    }

    if use_local:
        kwargs["endpoint_url"] = endpoint

    s3 = boto3.client("s3", **kwargs)
    return s3, bucket


def upload_to_s3(s3_client, bucket: str, key: str, media: DownloadedMedia) -> str:
    s3_client.put_object(
        Bucket=bucket,
        Key=key,
        Body=io.BytesIO(media.content),
        ContentType=media.content_type,
    )
    return key


def load_story_media_items(conn, limit: Optional[int]) -> list[StoryMediaItem]:
    sql = """
    SELECT
        si.id AS item_id,
        si.type AS item_type,
        COALESCE(
            NULLIF(TRIM(s.highlight_name), ''),
            NULLIF(TRIM(st.story_text), ''),
            NULLIF(TRIM(si.id), ''),
            'story'
        ) AS item_name
    FROM story_items si
    JOIN stories s ON s.id = si.story_id
    LEFT JOIN LATERAL (
        SELECT ti.content AS story_text
        FROM story_items tsi
        JOIN text_items ti ON ti.id = tsi.id
        WHERE tsi.story_id = si.story_id
          AND tsi.type = 'TEXT_ITEM'
        ORDER BY tsi.z_index ASC
        LIMIT 1
    ) st ON TRUE
    WHERE si.type IN ('IMAGE_ITEM', 'VIDEO_ITEM')
    ORDER BY si.id ASC
    """

    if limit and limit > 0:
        sql += " LIMIT %s"
        params = (limit,)
    else:
        params = None

    with conn.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()

    return [StoryMediaItem(item_id=r[0], item_type=r[1], item_name=r[2]) for r in rows]


def build_search_url(template: str, keyword: str) -> str:
    encoded = urllib.parse.quote_plus(clean_keyword(keyword))
    return template.format(query=encoded)


def update_image_item(conn, item_id: str, new_url: str) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE image_items SET url = %s WHERE id = %s",
            (new_url, item_id),
        )


def update_video_item(conn, item_id: str, video_url: str, thumb_url: str) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE video_items SET url = %s, thumbnail_url = %s WHERE id = %s",
            (video_url, thumb_url, item_id),
        )


def crawl_image_url(keyword: str) -> tuple[str, bool]:
    search_url = build_search_url(IMAGE_SEARCH_TEMPLATE, keyword)
    try:
        page = fetch_html(search_url)
        extracted = extract_first_image_url(page)
        if extracted:
            return extracted, True
    except Exception:
        pass
    return search_url, False


def crawl_image_candidates(keyword: str, max_candidates: int = 30) -> list[str]:
    all_candidates: list[str] = []
    seen: set[str] = set()

    for pattern in IMAGE_QUERY_VARIANTS:
        query = pattern.format(keyword=clean_keyword(keyword))
        search_url = build_search_url(IMAGE_SEARCH_TEMPLATE, query)
        try:
            page = fetch_html(search_url)
            candidates = extract_image_candidates(page, max_candidates=max_candidates)
            for c in candidates:
                if c not in seen:
                    seen.add(c)
                    all_candidates.append(c)
                if len(all_candidates) >= max_candidates:
                    return all_candidates
        except Exception:
            continue

    for fallback in FALLBACK_IMAGE_URLS:
        if fallback not in seen:
            all_candidates.append(fallback)
            seen.add(fallback)

    return all_candidates


def crawl_video_url(keyword: str) -> tuple[str, bool]:
    search_url = build_search_url(VIDEO_SEARCH_TEMPLATE, keyword)
    try:
        page = fetch_html(search_url)
        extracted = extract_first_video_url(page)
        if extracted:
            return extracted, True
    except Exception:
        pass
    return search_url, False


def crawl_video_candidates(keyword: str, max_candidates: int = 20) -> list[str]:
    all_candidates: list[str] = []
    seen: set[str] = set()

    for pattern in VIDEO_QUERY_VARIANTS:
        query = pattern.format(keyword=clean_keyword(keyword))
        search_url = build_search_url(VIDEO_SEARCH_TEMPLATE, query)
        try:
            page = fetch_html(search_url)
            candidates = extract_video_candidates(page, max_candidates=max_candidates)
            for c in candidates:
                if c not in seen:
                    seen.add(c)
                    all_candidates.append(c)
                if len(all_candidates) >= max_candidates:
                    break
        except Exception:
            continue

    for fallback in FALLBACK_VIDEO_URLS:
        if fallback not in seen:
            all_candidates.append(fallback)
            seen.add(fallback)

    return all_candidates


def try_download_image_from_candidates(candidates: list[str]) -> tuple[DownloadedMedia, str]:
    errors: list[str] = []
    for candidate in candidates:
        try:
            return download_media(candidate, expected="image"), candidate
        except Exception as exc:
            errors.append(f"{candidate} -> {exc}")
            continue

    if errors:
        short = "; ".join(errors[:3])
        raise RuntimeError(f"No downloadable image candidate worked: {short}")

    raise RuntimeError("No image candidates found")


def try_download_video_from_candidates(candidates: list[str]) -> tuple[DownloadedMedia, str]:
    errors: list[str] = []
    yt_missing = False

    for candidate in candidates:
        try:
            if looks_like_direct_video_url(candidate):
                return download_media(candidate, expected="video"), candidate

            return download_video_with_fallback(candidate), candidate
        except Exception as exc:
            err = str(exc)
            if "Install yt-dlp" in err:
                yt_missing = True
            errors.append(f"{candidate} -> {err}")
            continue

    if yt_missing:
        raise RuntimeError("No downloadable video found. Install yt-dlp: pip install yt-dlp")

    if errors:
        short = "; ".join(errors[:3])
        raise RuntimeError(f"No downloadable video candidate worked: {short}")

    raise RuntimeError("No video candidates found from Bing")


def build_s3_key(folder: str, keyword: str, item_id: str, ext: str) -> str:
    stem = sanitize_file_stem(keyword)
    clean_ext = ext if ext.startswith(".") else f".{ext}"
    return f"{folder}/{stem}-{item_id}{clean_ext}"


def resolve_story_folders() -> tuple[str, str, str]:
    app = resolve_app_config()
    stories_base = app.get("aws.s3.folder.stories", "stories").strip() or "stories"
    return (
        f"{stories_base}/images",
        f"{stories_base}/videos",
        f"{stories_base}/video-thumbnails",
    )


def process_items(dry_run: bool, limit: Optional[int]) -> int:
    conn = get_db_connection()
    conn.autocommit = False
    s3_client = None
    bucket = None
    if not dry_run:
        s3_client, bucket = get_s3_client_and_bucket()

    image_folder, video_folder, thumb_folder = resolve_story_folders()

    updated = 0
    failed = 0

    try:
        items = load_story_media_items(conn, limit)
        print(f"Loaded {len(items)} story media items")

        for item in items:
            keyword = item.item_name

            try:
                if item.item_type == "IMAGE_ITEM":
                    image_candidates = crawl_image_candidates(keyword, max_candidates=30)
                    image_url = image_candidates[0] if image_candidates else build_search_url(IMAGE_SEARCH_TEMPLATE, keyword)
                    image_direct = bool(image_candidates)
                    image_media = None
                    s3_key = None

                    print(
                        f"[IMAGE] {item.item_id} | keyword='{keyword}' | "
                        f"{'direct' if image_direct else 'search'} -> {image_url}"
                    )

                    if not dry_run:
                        if not image_direct:
                            raise RuntimeError(
                                f"Cannot upload IMAGE_ITEM {item.item_id}: no direct image URL from Bing"
                            )
                        image_media, used_image_url = try_download_image_from_candidates(image_candidates)
                        s3_key = build_s3_key(image_folder, keyword, item.item_id, image_media.extension)
                        print(f"[IMAGE-SELECTED] {item.item_id} -> {used_image_url}")
                        uploaded_key = upload_to_s3(s3_client, bucket, s3_key, image_media)
                        update_image_item(conn, item.item_id, uploaded_key)

                    updated += 1

                elif item.item_type == "VIDEO_ITEM":
                    video_candidates = crawl_video_candidates(keyword, max_candidates=15)
                    video_url = (
                        video_candidates[0]
                        if video_candidates
                        else build_search_url(VIDEO_SEARCH_TEMPLATE, keyword)
                    )
                    video_direct = bool(video_candidates)
                    thumb_url, thumb_direct = crawl_image_url(keyword)
                    thumb_candidates = crawl_image_candidates(keyword, max_candidates=20)
                    if thumb_candidates:
                        thumb_url = thumb_candidates[0]
                        thumb_direct = True
                    video_media = None
                    thumb_media = None
                    video_key = None
                    thumb_key = None

                    print(
                        f"[VIDEO] {item.item_id} | keyword='{keyword}' | "
                        f"video({'direct' if video_direct else 'search'}) -> {video_url} | "
                        f"thumb({'direct' if thumb_direct else 'search'}) -> {thumb_url}"
                    )

                    if not dry_run:
                        if not video_direct:
                            raise RuntimeError(
                                f"Cannot upload VIDEO_ITEM {item.item_id}: no direct video URL from Bing"
                            )
                        if not thumb_direct:
                            raise RuntimeError(
                                f"Cannot upload thumbnail for VIDEO_ITEM {item.item_id}: no direct image URL from Bing"
                            )

                        video_media, used_video_url = try_download_video_from_candidates(video_candidates)
                        thumb_media, used_thumb_url = try_download_image_from_candidates(thumb_candidates)
                        video_key = build_s3_key(video_folder, keyword, item.item_id, video_media.extension)
                        thumb_key = build_s3_key(thumb_folder, keyword, item.item_id, thumb_media.extension)

                        print(f"[VIDEO-SELECTED] {item.item_id} -> {used_video_url}")
                        print(f"[THUMB-SELECTED] {item.item_id} -> {used_thumb_url}")

                        uploaded_video_key = upload_to_s3(s3_client, bucket, video_key, video_media)
                        uploaded_thumb_key = upload_to_s3(s3_client, bucket, thumb_key, thumb_media)
                        update_video_item(conn, item.item_id, uploaded_video_key, uploaded_thumb_key)

                    updated += 1

            except Exception as item_error:
                failed += 1
                print(f"[SKIP] {item.item_id} -> {item_error}")
                continue

        if dry_run:
            conn.rollback()
            print(f"Dry-run mode: rolled back all DB changes | success={updated} failed={failed}")
        else:
            conn.commit()
            print(f"Committed updates for {updated} rows | failed={failed}")

        return updated

    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Crawl video/image from Bing for story items, upload to S3, "
            "then save S3 keys to DB."
        )
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run without committing DB updates",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Limit number of story items to process",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        process_items(dry_run=args.dry_run, limit=args.limit)
        return 0
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
