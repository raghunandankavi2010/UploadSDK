#!/usr/bin/env python3
"""
BULLETPROOF Local Upload Server
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
    ts = datetime.now().strftime("%H:%M:%S")
    line = "[%s] %s" % (ts, msg)
    print(line)
    with open(LOG_FILE, "a") as f:
        f.write(line + "\n")

def load_sessions():
    if os.path.exists(SESSIONS_FILE):
        try:
            with open(SESSIONS_FILE, "r") as f:
                return json.load(f)
        except Exception as e:
            log("ERROR loading sessions.json: %s" % e)
    return {}

def save_sessions(data):
    try:
        with open(SESSIONS_FILE, "w") as f:
            json.dump(data, f, indent=2)
    except Exception as e:
        log("ERROR saving sessions.json: %s" % e)

sessions = load_sessions()
chunk_registry = {}

@app.route("/api/v1/upload/init", methods=["POST"])
def init_upload():
    try:
        raw = request.get_data(as_text=True)
        data = request.get_json() or {}

        log("RAW INIT BODY: %s" % raw[:500])
        log("PARSED KEYS: %s" % list(data.keys()))

        task_id = data.get("taskId", "")
        file_name = data.get("fileName", "unknown")
        mime_type = data.get("mimeType", "application/octet-stream")
        total_bytes = data.get("totalBytes", 0)
        total_chunks = data.get("totalChunks", 0)
        checksum = data.get("checksum", "")

        if not task_id:
            task_id = "task_%s" % hashlib.md5(("%s%s" % (file_name, time.time())).encode()).hexdigest()[:12]
            log("GENERATED taskId: %s" % task_id)

        session_id = "sess_%s" % hashlib.md5(("%s%s" % (task_id, time.time())).encode()).hexdigest()[:16]

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

        log("INIT SUCCESS: taskId=%s | sessionId=%s | file=%s | chunks=%s" % (task_id, session_id, file_name, total_chunks))

        return jsonify({
            "success": True,
            "sessionId": session_id,
            "uploadUrl": "http://%s/api/v1/upload/chunk" % request.host,
            "expiresAt": int((time.time() + 3600) * 1000),
            "taskId": task_id
        })
    except Exception as e:
        log("INIT CRASH: %s" % traceback.format_exc())
        return jsonify({"success": False, "message": str(e)}), 500

@app.route("/api/v1/upload/chunk", methods=["POST"])
def upload_chunk():
    try:
        task_id = request.form.get("task_id", "")
        chunk_index = request.form.get("chunk_index", "-1")
        total_chunks = request.form.get("total_chunks", "0")

        log("CHUNK REQUEST: task_id=%s | chunk_index=%s | total_chunks=%s" % (task_id, chunk_index, total_chunks))

        if not task_id or chunk_index == "-1":
            return jsonify({"success": False, "message": "Missing parameters"}), 400

        chunk_idx = int(chunk_index)
        total = int(total_chunks)

        # FALLBACK: auto-create session if missing
        if task_id not in sessions:
            log("CHUNK FALLBACK: auto-creating session for %s" % task_id)
            sessions[task_id] = {
                "sessionId": "sess_fallback_%s" % task_id[:8],
                "fileName": "unknown_%s.bin" % task_id[:8],
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
        chunk_filename = "%s_chunk_%s" % (task_id, chunk_idx)
        chunk_path = os.path.join(CHUNKS_FOLDER, chunk_filename)
        file.save(chunk_path)

        if chunk_idx not in sessions[task_id]["uploadedChunks"]:
            sessions[task_id]["uploadedChunks"].append(chunk_idx)
            save_sessions(sessions)

        size = os.path.getsize(chunk_path)
        log("CHUNK STORED: taskId=%s | chunk=%s/%s | %s bytes" % (task_id, chunk_idx + 1, total, size))

        return jsonify({
            "success": True,
            "eTag": "etag_%s" % chunk_idx,
            "chunkIndex": chunk_idx,
            "message": "Chunk uploaded"
        })
    except Exception as e:
        log("CHUNK CRASH: %s" % traceback.format_exc())
        return jsonify({"success": False, "message": str(e)}), 500

@app.route("/api/v1/upload/commit", methods=["POST"])
def commit_upload():
    try:
        raw = request.get_data(as_text=True)
        data = request.get_json() or {}

        log("RAW COMMIT BODY: %s" % raw[:500])

        task_id = data.get("taskId") or data.get("task_id", "")
        file_name = data.get("fileName", "unknown")
        total_chunks = data.get("totalChunks", 0)

        log("COMMIT PARSED: taskId=%s | fileName=%s | totalChunks=%s" % (task_id, file_name, total_chunks))
        log("ALL SESSIONS: %s" % list(sessions.keys()))

        if not task_id:
            return jsonify({"success": False, "message": "Missing taskId"}), 400

        # FALLBACK: auto-create session if missing
        if task_id not in sessions:
            log("COMMIT FALLBACK: auto-creating session for %s" % task_id)
            sessions[task_id] = {
                "sessionId": "sess_fallback_%s" % task_id[:8],
                "fileName": secure_filename(file_name) if file_name else "unknown_%s.bin" % task_id[:8],
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
            final_filename = "upload_%s.bin" % task_id[:8]

        final_path = os.path.join(UPLOAD_FOLDER, final_filename)
        counter = 1
        original = final_path
        while os.path.exists(final_path):
            name, ext = os.path.splitext(original)
            final_path = "%s_%s%s" % (name, counter, ext)
            counter += 1

        with open(final_path, "wb") as outfile:
            for i in range(total_chunks):
                chunk_path = os.path.join(CHUNKS_FOLDER, "%s_chunk_%s" % (task_id, i))
                if os.path.exists(chunk_path):
                    with open(chunk_path, "rb") as infile:
                        outfile.write(infile.read())
                    os.remove(chunk_path)
                    log("  -> merged chunk %s" % i)
                else:
                    log("  -> MISSING chunk %s" % i)

        sessions[task_id]["status"] = "completed"
        sessions[task_id]["remoteUrl"] = "http://%s/uploads/%s" % (request.host, os.path.basename(final_path))
        sessions[task_id]["fileId"] = "file_%s" % task_id
        save_sessions(sessions)

        file_size = os.path.getsize(final_path)
        log("COMMIT SUCCESS: taskId=%s | saved=%s | %s bytes" % (task_id, os.path.basename(final_path), file_size))

        return jsonify({
            "success": True,
            "remoteUrl": sessions[task_id]["remoteUrl"],
            "fileId": sessions[task_id]["fileId"],
            "message": "Upload completed"
        })
    except Exception as e:
        log("COMMIT CRASH: %s" % traceback.format_exc())
        return jsonify({"success": False, "message": str(e)}), 500

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

@app.route("/api/v1/upload/session/refresh", methods=["POST"])
def refresh_session():
    data = request.get_json() or {}
    task_id = data.get("taskId", "")

    if task_id not in sessions:
        return jsonify({"success": False, "message": "Session not found"}), 404

    new_session_id = "sess_%s" % hashlib.md5(("%s%s" % (task_id, time.time())).encode()).hexdigest()[:16]
    sessions[task_id]["sessionId"] = new_session_id
    save_sessions(sessions)

    return jsonify({
        "success": True,
        "sessionId": new_session_id,
        "uploadUrl": "http://%s/api/v1/upload/chunk" % request.host,
        "expiresAt": int((time.time() + 3600) * 1000)
    })

@app.route("/uploads/<filename>", methods=["GET"])
def serve_file(filename):
    return app.send_from_directory(UPLOAD_FOLDER, filename)

@app.route("/api/v1/health", methods=["GET"])
def health_check():
    return jsonify({
        "status": "ok",
        "activeSessions": len(sessions),
        "uploadFolder": UPLOAD_FOLDER
    })

@app.route("/", methods=["GET"])
def index():
    completed = [
        {"taskId": k, "fileName": v["fileName"], "fileId": v.get("fileId"), "remoteUrl": v.get("remoteUrl")}
        for k, v in sessions.items() if v.get("status") == "completed"
    ]
    return jsonify({
        "server": "Android Upload SDK Local Server",
        "version": "1.1.0",
        "uploadFolder": UPLOAD_FOLDER,
        "completedUploads": len(completed),
        "uploads": completed
    })

if __name__ == "__main__":
    print("=" * 60)
    print("BULLETPROOF Upload Server")
    print("=" * 60)
    print("Upload folder: %s" % UPLOAD_FOLDER)
    print("Sessions file: %s" % SESSIONS_FILE)
    print("Log file: %s" % LOG_FILE)
    print("API Base URL: http://localhost:5000/api/v1/")
    print("")
    print("For Android Emulator: use http://10.0.2.2:5000/api/v1/")
    print("For Physical Device: use http://YOUR_MAC_IP:5000/api/v1/")
    print("=" * 60)

    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True)