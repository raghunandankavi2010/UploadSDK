# Android File Upload SDK v1.0

Production-ready Android File Upload SDK implementing chunked resumable uploads with Jetpack Compose demo app.
Based on Rahul Ray's system design principles for mobile upload SDKs.

## System Design Architecture

```
+---------------------------------------------------------------+
|                     UPLOAD SDK LAYER                          |
+---------+-------------+-------------+------------+------------+
| SDK API |Preprocessing| Persistent  | Scheduler  | WorkManager|
|         |             | Queue       |            |            |
+---------+-------------+-------------+------------+------------+
| enqueue | SHA-256     | Room DB     | Priority   | Battery-   |
| pause   | MIME Extract| taskId      | Queue      | aware      |
| resume  | Thumbnail   | chunks      | Thermal    | Survives   |
| observe | Eligibility | status      | Throttle   | Reboot     |
| cancel  | Validation  | sessions    | Parallel   | Notifications
+---------+-------------+-------------+------------+------------+
                          |
                +---------+----------+
                |   COORDINATOR        |
                | Session Management   |
                | Token Refresh        |
                | Retry Logic          |
                | Auto-resume on       |
                | network restore      |
                +---------+----------+
                          |
                +---------+----------+
                |   CHUNK ENGINE       |
                | File -> [C1]->[C2]   |
                | 8MB Chunks           |
                | Adaptive Sizing      |
                | Offset Tracking      |
                | Checksum Verify      |
                +----------------------+

                ┌─────────────────────────────────────────────────────────────────────────────┐
│                            ANDROID APP LAYER                                │
│  (UI Components: UploadDetailScreen, UploadListScreen, ViewModel)           │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │ 1. uploadFile(file)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SDK PRESENTATION LAYER                           │
│  (UploadManager.kt: Entry point for the developer using the SDK)            │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │ 2. invoke(task)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SDK DOMAIN LAYER                                 │
│  (UploadUseCase.kt: Orchestrates business logic and repository calls)       │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │ 3. enqueueUpload(task)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SDK DATA LAYER (Internal)                        │
│                                                                             │
│  ┌───────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │
│  │ FilePreprocessor  │ ◄─── │UploadRepository  │ ───► │   ChunkEngine    │  │
│  │ (Checksum/Thumb)  │      │      Impl        │      │ (File Splitting) │  │
│  └───────────────────┘      └────────┬─────────┘      └──────────────────┘  │
│                                      │                                      │
│               4. Save Task/Chunks    │   5. Schedule via WorkManager        │
│                                      ▼                                      │
│  ┌───────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │
│  │   Room Database   │ ◄─── │   UploadWorker   │ ───► │  Notifications   │  │
│  │ (Task/Chunk DTOs) │      │ (CoroutineWorker)│      │  (Manager/UI)    │  │
│  └─────────▲─────────┘      └────────┬─────────┘      └──────────────────┘  │
│            │                         │                                      │
│            │ 7. Mark Chunk Done/Fail │ 6. Loop: uploadChunk()               │
│            └─────────────────────────┼──────────────────┐                   │
│                                      ▼                  │                   │
│  ┌───────────────────┐      ┌──────────────────┐        │                   │
│  │  SessionManager   │ ◄─── │  ChunkUploader   │ ◄──────┘                   │
│  │  (Auth/Sessions)  │      │ (Retrofit Calls) │                            │
│  └───────────────────┘      └────────┬─────────┘                            │
│                                      │                                      │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ 8. HTTP Requests (Multipart)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            TEST SERVER (Flask)                              │
│                                                                             │
│  ┌───────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │
│  │  /upload/init     │ ───► │  /upload/chunk   │ ───► │  /upload/commit  │  │
│  │ (Session Start)   │      │ (Save .tmp file) │      │ (Merge Chunks)   │  │
│  └───────────────────┘      └──────────────────┘      └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Features

### Core Upload Features
- **Chunked Uploads**: Files split into 8MB chunks (adaptive sizing for files < 10MB)
- **Resumable**: Resume from last uploaded chunk after app kill, network loss, or crash
- **Persistent Queue**: Room database persists upload state across reboots
- **Background Processing**: WorkManager handles uploads even when app is closed
- **Notifications**: Progress notifications during background uploads

### Preprocessing
- **SHA-256 Checksum**: Integrity verification for each chunk and full file
- **MIME Extraction**: Automatic MIME type detection from file extension
- **Thumbnail Generation**: Image/video thumbnail extraction for UI preview
- **Eligibility Checks**: File size limits, extension validation, empty file detection

### Scheduling & Resource Management
- **Priority Queue**: LOW, NORMAL, HIGH, CRITICAL priorities
- **Battery Awareness**: Defers uploads when battery < 15% and not charging
- **Thermal Throttling**: Reduces parallelism when device is thermally stressed
- **Concurrent Limits**: Max 3 parallel uploads with queue management
- **Network Monitoring**: Auto-resumes uploads when connectivity restores

### Reliability
- **Retry Logic**: Exponential backoff with configurable max retries
- **Session Management**: Backend session creation with refresh support
- **Progress Tracking**: Real-time progress, speed calculation (KB/s, MB/s)
- **Error Handling**: Structured exceptions for different failure modes
- **Analytics Interface**: Pluggable analytics for upload metrics

### UI (Jetpack Compose Demo)
- **Material3 Design**: Modern UI with dynamic colors
- **Upload List**: Real-time progress with speed, pause/resume/cancel/retry
- **Detail Screen**: Chunk-level progress inspection
- **File Picker**: Multi-select with priority selection
- **Settings**: SDK configuration screen
- **Navigation**: NavHost with list -> detail flow

## Quick Start

### 1. Add dependency
```kotlin
implementation(project(":upload-sdk"))
```

### 2. Initialize Application
```kotlin
@HiltAndroidApp
class MyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
}
```

### 3. Upload a file
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val uploadManager: UploadManager
) : ViewModel() {

    fun uploadFile(file: File) {
        viewModelScope.launch {
            val taskId = uploadManager.uploadFile(
                file = file,
                priority = UploadPriority.HIGH,
                metadata = mapOf("user_id" to "123")
            )
        }
    }
}
```

### 4. Control uploads
```kotlin
uploadManager.pause(taskId)
uploadManager.resume(taskId)
uploadManager.cancel(taskId)
uploadManager.retry(taskId)
```

### 5. Non-Hilt usage
```kotlin
val uploadSdk = UploadSdkBuilder(context)
    .baseUrl("https://api.example.com")
    .chunkSize(8 * 1024 * 1024)
    .maxRetries(5)
    .build()

val taskId = uploadSdk.uploadFile(file, priority = UploadPriority.HIGH)
```

## Project Structure

```
upload-sdk/
├── config/              # SDK Configuration
├── domain/
│   ├── model/           # UploadTask, UploadStatus, UploadResult, ChunkInfo
│   ├── repository/      # Repository interfaces
│   └── usecase/         # UploadUseCase
├── data/
│   ├── local/           # Room DB, DAOs, Entities
│   ├── remote/          # Retrofit API, DTOs, Mock API
│   ├── preprocessor/    # SHA-256, MIME, Thumbnail, Validation
│   ├── chunk/           # Chunk splitting, chunk upload
│   ├── scheduler/       # Battery-aware, thermal throttling, priority queue
│   ├── coordinator/     # Session manager, retry coordinator
│   ├── repository/      # Repository implementations
│   └── worker/          # UploadWorker, Notifications, WorkObserver
├── di/                  # Hilt modules
├── presentation/        # Public API (UploadManager, UploadSdk, Builder)
└── util/                # Logging, Formatting, Exceptions, Speed Calc, Analytics

app/
├── ui/
│   ├── screens/         # UploadListScreen, UploadDetailScreen, SettingsScreen
│   ├── components/      # UploadItem, UploadThumbnail, FilePickerButton
│   └── theme/           # Material3 theme
├── viewmodel/           # UploadViewModel, UploadDetailViewModel
└── MainActivity.kt      # NavHost
```

## Backend API Contract

Your backend needs these endpoints:

```
Install python server 
a. pip install flask werkzeug

b. put the below code in server.py files and run   
  python server.py


Here is the python code    

import os
from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
import uuid

app = Flask(__name__)
UPLOAD_FOLDER = 'uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

# Storage for active chunks
chunks = {} # taskId -> list of chunk file paths

@app.route('/api/v1/upload/init', methods=['POST'])
def init_upload():
    data = request.json
    task_id = str(uuid.uuid4())
    session_id = str(uuid.uuid4())
    total_chunks = data.get('totalChunks', 0)
    chunks[task_id] = [None] * total_chunks
    
    return jsonify({
        'success': True,
        'sessionId': session_id,
        'uploadUrl': f'http://10.0.2.2:5000/api/v1/upload/chunk',
        'expiresAt': 9999999999
    })

@app.route('/api/v1/upload/chunk', methods=['POST'])
def upload_chunk():
    task_id = request.form.get('task_id')
    chunk_index = int(request.form.get('chunk_index'))
    
    # --- SIMULATE FAILURE FOR TESTING ---
    # Uncomment the lines below to test how the SDK captures error messages:
    # if chunk_index == 1: # Fail the second chunk
    #     return jsonify({'success': False, 'message': 'Storage quota exceeded for chunk 1'}), 507
    
    file = request.files['file']
    filename = secure_filename(f"{task_id}_{chunk_index}.tmp")
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    file.save(filepath)
    
    if task_id not in chunks: chunks[task_id] = []
    chunks[task_id][chunk_index] = filepath
    
    return jsonify({'success': True, 'eTag': f"etag_{chunk_index}", 'chunkIndex': chunk_index})

@app.route('/api/v1/upload/commit', methods=['POST'])
def commit_upload():
    data = request.json
    task_id = data.get('taskId')
    file_name = secure_filename(data.get('fileName', 'output.dat'))
    final_path = os.path.join(UPLOAD_FOLDER, file_name)
    
    with open(final_path, 'wb') as outfile:
        for path in chunks[task_id]:
            with open(path, 'rb') as infile:
                outfile.write(infile.read())
            os.remove(path) # Clean up
            
    return jsonify({'success': True, 'remoteUrl': f'http://10.0.2.2:5000/uploads/{file_name}'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)

POST /api/v1/upload/init        -> Create upload session
POST /api/v1/upload/chunk       -> Upload chunk (multipart)
POST /api/v1/upload/commit      -> Finalize upload
GET  /api/v1/upload/status/{id} -> Check status
POST /api/v1/upload/session/refresh -> Refresh session
```

## Testing

### Unit Tests
```bash
./gradlew :upload-sdk:test
```

### Mock API (No backend needed)
The SDK includes MockUploadApiService for testing without a real backend.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9 |
| Async | Coroutines + Flow |
| UI | Jetpack Compose + Material3 |
| DI | Hilt 2.50 |
| Database | Room 2.6.1 |
| Background | WorkManager 2.9.0 |
| Network | Retrofit 2.9 + OkHttp 4.12 |

## License

MIT License
