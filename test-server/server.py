import os
from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
import uuid

app = Flask(__name__)
UPLOAD_FOLDER = 'uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

# In-memory storage for active sessions and chunks
sessions = {}
chunks = {} # taskId -> list of chunk files

@app.route('/api/v1/upload/init', methods=['POST'])
def init_upload():
    data = request.json
    if not data:
        return jsonify({'success': False, 'message': 'No data provided'}), 400

    task_id = str(uuid.uuid4())
    session_id = str(uuid.uuid4())

    total_chunks = data.get('totalChunks', 0)
    sessions[session_id] = {
        'task_id': task_id,
        'file_name': data.get('fileName'),
        'total_chunks': total_chunks,
        'total_bytes': data.get('totalBytes')
    }
    chunks[task_id] = [None] * total_chunks

    print(f"Initialized upload: task_id={task_id}, session_id={session_id}, total_chunks={total_chunks}")

    return jsonify({
        'success': True,
        'sessionId': session_id,
        'uploadUrl': f'http://10.0.2.2:5000/api/v1/upload/chunk',
        'expiresAt': 9999999999
    })

@app.route('/api/v1/upload/chunk', methods=['POST'])
def upload_chunk():
    # Multi-part form data
    task_id = request.form.get('task_id')
    chunk_index_str = request.form.get('chunk_index')
    total_chunks_str = request.form.get('total_chunks')
    session_id = request.form.get('session_id')

    if not all([task_id, chunk_index_str, total_chunks_str, session_id]):
         return jsonify({'success': False, 'message': 'Missing required fields'}), 400

    chunk_index = int(chunk_index_str)
    total_chunks = int(total_chunks_str)

    if 'file' not in request.files:
        return jsonify({'success': False, 'message': 'No file part'}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({'success': False, 'message': 'No selected file'}), 400

    # Simulate random failures for testing (optional, disabled by default)
    # import random
    # if random.random() < 0.2:
    #     print(f"Simulating failure for chunk {chunk_index}")
    #     return jsonify({'success': False, 'message': f'Simulated random server error for chunk {chunk_index}'}), 500

    filename = secure_filename(f"{task_id}_{chunk_index}.tmp")
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    file.save(filepath)

    if task_id not in chunks:
        chunks[task_id] = [None] * total_chunks

    chunks[task_id][chunk_index] = filepath

    print(f"Uploaded chunk {chunk_index}/{total_chunks} for task {task_id}")

    return jsonify({
        'success': True,
        'eTag': f"etag_{task_id}_{chunk_index}",
        'chunkIndex': chunk_index
    })

@app.route('/api/v1/upload/commit', methods=['POST'])
def commit_upload():
    data = request.json
    if not data:
         return jsonify({'success': False, 'message': 'No data provided'}), 400

    task_id = data.get('taskId')

    if task_id not in chunks:
        return jsonify({'success': False, 'message': 'Task not found'}), 404

    # Combine chunks
    file_name = data.get('fileName', 'uploaded_file')
    final_filename = secure_filename(file_name)
    final_path = os.path.join(UPLOAD_FOLDER, final_filename)

    print(f"Committing upload for task {task_id}, final file: {final_filename}")

    try:
        with open(final_path, 'wb') as outfile:
            for i, chunk_path in enumerate(chunks[task_id]):
                if chunk_path is None or not os.path.exists(chunk_path):
                    return jsonify({'success': False, 'message': f'Missing chunk {i}'}), 400
                with open(chunk_path, 'rb') as infile:
                    outfile.write(infile.read())
                os.remove(chunk_path) # Clean up chunk
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

    return jsonify({
        'success': True,
        'remoteUrl': f'http://10.0.2.2:5000/uploads/{final_filename}',
        'fileId': str(uuid.uuid4())
    })

@app.route('/api/v1/upload/status/<task_id>', methods=['GET'])
def get_status(task_id):
    uploaded = []
    if task_id in chunks:
        for i, c in enumerate(chunks[task_id]):
            if c is not None:
                uploaded.append(i)

    return jsonify({
        'taskId': task_id,
        'status': 'UPLOADING',
        'uploadedChunks': uploaded
    })

if __name__ == '__main__':
    print("Starting Upload SDK Test Server on port 5000...")
    print("If running on Emulator, use http://10.0.2.2:5000/api/v1/ as base URL")
    app.run(host='0.0.0.0', port=5000, debug=True)
