#!/usr/bin/env python3
"""
BULLETPROOF Local Upload Server
- Survives restarts via sessions.json
- Auto-creates missing sessions during commit (fallback)
- Logs every single request with full body
"""

import os
import json
import hashlib
import time
import traceback
from datetime import datetime
from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename

app = Flask(__name__)

UPLOAD_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")
CHUNKS_FOLDER = os.path.join(UPLOAD_FOLDER, "chunks")
SESSIONS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sessions.json")
LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "server.log")

os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(CHUNKS_FOLDER, exist_ok=True)

def log(msg):
    line = f"[{datetime.now().strftime('%H:%M:%S.%f')[:-3]}] {msg}"
    print(line)
    with open(LOG_FILE, "a") as f:
        f.write(line + "
")

def load_sessions():
    if os.path.exists(SESSIONS_FILE):
        try:
            with open(SESSIONS_FILE, "r") as f:
                return json.load(f)
        except Exception as e:
            log(f"ERROR loading sessions.json: {e}")
    return {}

def save_sessions(data):
    try:
        with open(SESSIONS_FILE, "w") as f:
            json.dump(data, f, indent=2)
    except Exception as e:
        log(f"ERROR saving sessions.json: {e}")

sessions = load_sessions()
chunk_registry = {}

# ========== INIT ==========
@app.route("/api/v1/upload/init", methods=["POST"])
def init_upload():
    try:
        raw = request.get_data(as_text=True)
        data = request.get_json() or {}

        log(f"RAW INIT BODY: {raw[:500]}")
        log(f"PARSED JSON: {json.dumps(data)[:500]}")

        task_id = data.get("taskId", "")
        file_name = data.get("fileName", "unknown")
        mime_type = data.get("mimeType", "application/octet-stream")
        total_bytes = data.get("totalBytes", 0)
        total_chunks = data.get("totalChunks", 0)
        checksum = data.get("checksum", "")

        if not task_id:
            task_id = f"task_{hashlib.md5(f'{file_name}{time.time()}'.encode()).hexdigest()[:12]}"
            log(f"GENERATED taskId (none provided): {task_id}")

        session_id = f"sess_{hashlib.md5(f'{task_id}{time.time()}'.encode()).hexdigest()[:16]}"

        sessions[task_id] = {
            "sessionId": session_id,
            "fileName": secure_filename(file_name),
            "mimeType": mime_type,
            "totalBytes": total_bytes,
            "totalChunks": total_chunks,
            "checksum": checksum,
            "uploadedChunks": [],
            "createdAt": time.time(),
            "status": "in_progress"
        }
        chunk_registry[task_id] = {}
        save_sessions(sessions)

        log(f"INIT SUCCESS: taskId={task_id} | sessionId={session_id} | file={file_name} | chunks={total_chunks}")

        return jsonify({
            "success": True,
            "sessionId": session_id,
            "uploadUrl": f"http://{request.host}/api/v1/upload/chunk",
            "expiresAt": int((time.time() + 3600) * 1000),
            "taskId": task_id
        })
    except Exception as e:
        log(f"INIT CRASH: {e}
{traceback.format_exc()}")
        return jsonify({"success": False, "message": str(e)}), 500

# ========== CHUNK ==========
@app.route("/api/v1/upload/chunk", methods=["POST"])
def upload_chunk():
    try:
        task_id = request.form.get("task_id", "")
        chunk_index = request.form.get("chunk_index", "-1")
        total_chunks = request.form.get("total_chunks", "0")

        log(f"CHUNK REQUEST: task_id={task_id} | chunk_index={chunk_index} | total_chunks={total_chunks}")
        log(f"FILES: {list(request.files.keys())} | FORM: {list(request.form.keys())}")

        if not task_id or chunk_index == "-1":
            return jsonify({"success": False, "message": "Missing parameters"}), 400

        chunk_idx = int(chunk_index)
        total = int(total_chunks)

        # FALLBACK: auto-create session if missing (handles server restarts)
        if task_id not in sessions:
            log(f"CHUNK FALLBACK: auto-creating session for {task_id}")
            sessions[task_id] = {
                "sessionId": f"sess_fallback_{task_id[:8]}",
                "fileName": f"unknown_{task_id[:8]}.bin",
                "mimeType": "application/octet-stream",
                "totalBytes": 0,
                "totalChunks": total,
                "checksum": "",
                "uploadedChunks": [],
                "createdAt": time.time(),
                "status": "in_progress"
            }
            chunk_registry[task_id] = {}
            save_sessions(sessions)

        if "file" not in request.files:
            return jsonify({"success": False, "message": "No file part"}), 400

        file = request.files["file"]
        chunk_filename = f"{task_id}_chunk_{chunk_idx}"
        chunk_path = os.path.join(CHUNKS_FOLDER, chunk_filename)
        file.save(chunk_path)

        if chunk_idx not in sessions[task_id]["uploadedChunks"]:
            sessions[task_id]["uploadedChunks"].append(chunk_idx)
            save_sessions(sessions)

        size = os.path.getsize(chunk_path)
        log(f"CHUNK STORED: taskId={task_id} | chunk={chunk_idx + 1}/{total} | {size} bytes")

        return jsonify({
            "success": True,
            "eTag": f"etag_{chunk_idx}",
            "chunkIndex": chunk_idx,
            "message": "Chunk uploaded"
        })
    except Exception as e:
        log(f"CHUNK CRASH: {e}
{traceback.format_exc()}")
        return jsonify({"success": False, "message": str(e)}), 500

# ========== COMMIT ==========
@app.route("/api/v1/upload/commit", methods=["POST"])
def commit_upload():
    try:
        raw = request.get_data(as_text=True)
        data = request.get_json() or {}

        log(f"RAW COMMIT BODY: {raw[:500]}")

        task_id = data.get("taskId") or data.get("task_id", "")
        file_name = data.get("fileName", "unknown")
        total_chunks = data.get("totalChunks", 0)

        log(f"COMMIT PARSED: taskId={task_id} | fileName={file_name} | totalChunks={total_chunks}")
        log(f"ALL SESSIONS: {list(sessions.keys())}")

        if not task_id:
            return jsonify({"success": False, "message": "Missing taskId"}), 400

        # FALLBACK: auto-create session if missing
        if task_id not in sessions:
            log(f"COMMIT FALLBACK: auto-creating session for {task_id}")
            sessions[task_id] = {
                "sessionId": f"sess_fallback_{task_id[:8]}",
                "fileName": secure_filename(file_name),
                "mimeType": "application/octet-stream",
                "totalBytes": 0,
                "totalChunks": total_chunks,
                "checksum": "",
                "uploadedChunks": list(range(total_chunks)),
                "createdAt": time.time(),
                "status": "in_progress"
            }
            save_sessions(sessions)

        # Reassemble
        final_filename = secure_filename(file_name)
        if not final_filename:
            final_filename = f"upload_{task_id[:8]}.bin"

        final_path = os.path.join(UPLOAD_FOLDER, final_filename)
        counter = 1
        original = final_path
        while os.path.exists(final_path):
            name, ext = os.path.splitext(original)
            final_path = f"{name}_{counter}{ext}"
            counter += 1

        with open(final_path, "wb") as outfile:
            for i in range(total_chunks):
                chunk_path = os.path.join(CHUNKS_FOLDER, f"{task_id}_chunk_{i}")
                if os.path.exists(chunk_path):
                    with open(chunk_path, "rb") as infile:
                        outfile.write(infile.read())
                    os.remove(chunk_path)
                    log(f"  -> merged chunk {i}")
                else:
                    log(f"  -> MISSING chunk {i} (expected at {chunk_path})")

        sessions[task_id]["status"] = "completed"
        sessions[task_id]["remoteUrl"] = f"http://{request.host}/uploads/{os.path.basename(final_path)}"
        sessions[task_id]["fileId"] = f"file_{task_id}"
        save_sessions(sessions)

        file_size = os.path.getsize(final_path)
        log(f"COMMIT SUCCESS: taskId={task_id} | saved={os.path.basename(final_path)} | {file_size} bytes")

        return jsonify({
            "success": True,
            "remoteUrl": sessions[task_id]["remoteUrl"],
            "fileId": sessions[task_id]["fileId"],
            "message": "Upload completed"
        })
    except Exception as e:
        log(f"COMMIT CRASH: {e}
{traceback.format_exc()}")
        return jsonify({"success": False, "message": str(e)}), 500

# ========== STATUS ==========
@app.route("/api/v1/upload/status/<task_id>", methods=["GET"])
def upload_status(task_id):
    if task_id not in sessions:
        return jsonify({"success": False, "message": "Task not found"}), 404

    return jsonify({
        "taskId": task_id,
        "status": sessions[task_id]["status"],
        "uploadedChunks": sessions[task_id]["uploadedChunks"],
        "remoteUrl": sessions[task_id].get("remoteUrl")
    })

# ========== REFRESH ==========
@app.route("/api/v1/upload/session/refresh", methods=["POST"])
def refresh_session():
    data = request.get_json() or {}
    task_id = data.get("taskId", "")

    if task_id not in sessions:
        return jsonify({"success": False, "message": "Session not found"}), 404

    new_session_id = f"sess_{hashlib.md5(f'{task_id}{time.time()}'.encode()).hexdigest()[:16]}"
    sessions[task_id]["sessionId"] = new_session_id
    save_sessions(sessions)

    return jsonify({
        "success": True,
        "sessionId": new_session_id,
        "uploadUrl": f"http://{request.host}/api/v1/upload/chunk",
        "expiresAt": int((time.time() + 3600) * 1000)
    })

# ========== SERVE / HEALTH / INDEX ==========
@app.route("/uploads/<filename>", methods=["GET"])
def serve_file(filename):
    return app.send_from_directory(UPLOAD_FOLDER, filename)

@app.route("/api/v1/health", methods=["GET"])
def health_check():
    return jsonify({
        "status": "ok",
        "activeSessions": len(sessions),
        "uploadFolder": UPLOAD_FOLDER,
        "sessionsFile": SESSIONS_FILE
    })

@app.route("/", methods=["GET"])
def index():
    completed = [
        {"taskId": k, "fileName": v["fileName"], "fileId": v.get("fileId"), "remoteUrl": v.get("remoteUrl")}
        for k, v in sessions.items() if v.get("status") == "completed"
    ]
    return jsonify({
        "server": "Android Upload SDK Local Server (BULLETPROOF)",
        "version": "1.1.0",
        "uploadFolder": UPLOAD_FOLDER,
        "completedUploads": len(completed),
        "uploads": completed
    })

if __name__ == "__main__":
    print("=" * 60)
    print("BULLETPROOF Upload Server")
    print("=" * 60)
    print(f"Upload folder: {UPLOAD_FOLDER}")
    print(f"Sessions file: {SESSIONS_FILE}")
    print(f"Log file: {LOG_FILE}")
    print(f"API Base URL: http://localhost:5000/api/v1/")
    print("
For Android Emulator: use http://10.0.2.2:5000/api/v1/")
    print("For Physical Device: use http://YOUR_MAC_IP:5000/api/v1/")
    print("=" * 60)

    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True)
