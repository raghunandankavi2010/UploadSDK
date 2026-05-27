# Android File Upload SDK v2.0

Production-ready Android File Upload SDK implementing chunked resumable uploads with Jetpack Compose demo app.
Based on Rahul Ray's system design principles for mobile upload SDKs.

## System Design Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ANDROID APP LAYER                                │
│  (UI Components: UploadDetailScreen, UploadListScreen, ViewModel)           │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │ 1. uploadFile(file)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SDK PRESENTATION LAYER                           │
│  (UploadManager / UploadSdk / UploadSdkBuilder)                             │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │ 2. invoke(task)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SDK DOMAIN LAYER                                 │
│  (UploadUseCase: Orchestrates business logic and repository calls)          │
└───────────────────────┬─────────────────────────────────────────────────────┘
                        │ 3. enqueueUpload(task)
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SDK DATA LAYER (Internal)                        │
│                                                                             │
│  ┌───────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │
│  │ FilePreprocessor  │ ◄─── │UploadRepository  │ ───► │   ChunkEngine    │  │
│  │ Checksum/Thumb/   │      │      Impl        │      │ File Splitting   │  │
│  │ Validation        │      │ + Cache Cleanup  │      │ Adaptive Sizing  │  │
│  └───────────────────┘      └────────┬─────────┘      └──────────────────┘  │
│                                      │                                      │
│               4. Save Task/Chunks    │   5. Schedule via WorkManager        │
│                                      ▼                                      │
│  ┌───────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │
│  │   Room Database   │ ◄─── │   UploadWorker   │ ───► │  Notifications   │  │
│  │ Task/Chunk/Session│      │ Sequential or    │      │ Foreground Svc   │  │
│  │                   │      │ Parallel Chunks  │      │ Progress/ETA     │  │
│  └─────────▲─────────┘      └────────┬─────────┘      └──────────────────┘  │
│            │                         │                                      │
│            │ 7. Mark Chunk Done/Fail │ 6. Loop: uploadChunk()               │
│            └─────────────────────────┼──────────────────┐                   │
│                                      ▼                  │                   │
│  ┌───────────────────┐      ┌──────────────────┐        │                   │
│  │  SessionManager   │ ◄─── │  ChunkUploader   │ ◄──────┘                   │
│  │ Auth / Expiry     │      │ Retrofit Calls   │                            │
│  └───────────────────┘      └────────┬─────────┘                            │
│                                      │                                      │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ 8. HTTP Requests (Multipart)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SERVER (Flask / Your Backend)                    │
│                                                                             │
│  ┌───────────────────┐      ┌──────────────────┐      ┌──────────────────┐  │
│  │  /upload/init     │ ───► │  /upload/chunk   │ ───► │  /upload/commit  │  │
│  │ (Session Start)   │      │ (Save .tmp file) │      │ (Merge Chunks)   │  │
│  └───────────────────┘      └──────────────────┘      └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Features

### Core Upload Features
- **Chunked Uploads**: Files split into configurable chunks (default 8MB, adaptive sizing for files < 10MB)
- **Resumable**: Resume from last uploaded chunk after app kill, network loss, or crash
- **Parallel Chunk Uploads**: Configurable parallelism with `Semaphore`-based concurrency control
- **Persistent Queue**: Room database persists upload state (tasks, chunks, sessions) across reboots
- **Background Processing**: WorkManager handles uploads even when app is closed
- **Foreground Notifications**: Progress notification shown during active uploads with Android 12+ safety handling
- **ETA Calculation**: Real-time estimated time remaining based on rolling speed average

### Preprocessing
- **SHA-256 Checksum**: Integrity verification for full file (delegated to `ChecksumCalculator`)
- **MIME Extraction**: Automatic MIME type detection from file extension via `MimeTypeMap`
- **Thumbnail Generation**: Image/video thumbnail extraction for UI preview
- **File Validation**: File size limits (2GB), allowed extensions, empty file detection via `UploadFileValidator`

### Scheduling & Resource Management
- **Priority Queue**: `LOW`, `NORMAL`, `HIGH`, `CRITICAL` priorities with backoff policy per priority
- **Battery Awareness**: Defers non-critical uploads when battery < 15% and not charging
- **Thermal Throttling**: Real thermal monitoring via `PowerManager.currentThermalStatus` (API 29+), reduces parallelism under thermal stress
- **Network Type Enforcement**: Respects `UploadConfig.networkType` — supports `ANY`, `WIFI_ONLY`, and `UNMETERED_ONLY` mapped to WorkManager constraints
- **Concurrent Limits**: Configurable max parallel uploads with `UploadQueueManager`
- **Network Monitoring**: Auto-resumes uploads when connectivity restores via `NetworkChangeReceiver`

### Reliability
- **Retry Logic**: Exponential backoff (linear for CRITICAL) with configurable max retries
- **Session Management**: Backend session creation with expiry checks and auto-refresh
- **Progress Tracking**: Real-time progress, speed calculation (KB/s, MB/s), ETA
- **Error Handling**: Structured exceptions (`FileNotFoundException`, `InvalidFileException`) with retry classification
- **Analytics Interface**: Pluggable analytics for upload metrics (`UploadAnalytics`)
- **Cache Cleanup**: Automatic cleanup of cached file copies and thumbnails on upload delete/clear

### Configuration
```kotlin
data class UploadConfig(
    val baseUrl: String,
    val chunkSize: Int = 8 * 1024 * 1024,       // 8MB default
    val maxRetries: Int = 3,
    val parallelUploads: Int = 3,                 // Parallel chunk count
    val enableCompression: Boolean = false,
    val enableThumbnail: Boolean = true,
    val batteryAware: Boolean = true,
    val thermalThrottling: Boolean = true,
    val networkType: NetworkType = NetworkType.ANY, // ANY, WIFI_ONLY, UNMETERED_ONLY
    val timeoutMs: Long = 30000L,
    val useMockApi: Boolean = false,
    val authTokenProvider: (() -> String)? = null   // Dynamic auth token
)
```

### UI (Jetpack Compose Demo)
- **Material3 Design**: Modern UI with status-colored indicators
- **Upload List**: Real-time progress with speed, ETA, pause/resume/cancel/retry
- **Detail Screen**: Chunk-level progress inspection (individual chunk states)
- **File Picker**: Multi-select with per-file priority selection dropdown
- **Settings**: SDK configuration screen
- **Thumbnails**: Image/video previews via Coil
- **Navigation**: NavHost with list -> detail flow

## Quick Start

### Local Server Setup
```bash
cd local-server
pip3 install flask werkzeug
python3 upload_server.py
```
Server starts on `http://0.0.0.0:5000`

### 1. Add dependency
```kotlin
implementation(project(":upload-sdk"))
```

### 2. Initialize Application (Hilt)
```kotlin
@HiltAndroidApp
class MyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
}
```

### 3. Provide UploadConfig (Hilt module)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideUploadConfig(): UploadConfig = UploadConfig(
        baseUrl = "http://10.0.2.2:5000/api/v1/",
        parallelUploads = 3,
        networkType = UploadConfig.NetworkType.ANY,
        batteryAware = true,
        thermalThrottling = true,
        authTokenProvider = { "Bearer my-token" }
    )
}
```

### 4. Upload a file
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

    // Observe real-time progress with speed and ETA
    fun observeUpload(taskId: String) {
        viewModelScope.launch {
            uploadManager.observeUpload(taskId).collect { result ->
                when (result) {
                    is UploadResult.Progress -> {
                        // result.percent, result.speedKbps, result.etaSeconds
                    }
                    is UploadResult.Success -> {
                        // result.remoteUrl, result.fileId
                    }
                    is UploadResult.Failure -> {
                        // result.error, result.isRetryable
                    }
                    else -> {}
                }
            }
        }
    }
}
```

### 5. Control uploads
```kotlin
uploadManager.pause(taskId)
uploadManager.resume(taskId)
uploadManager.cancel(taskId)
uploadManager.retry(taskId)
uploadManager.clearCompleted()         // Also cleans up cached files + thumbnails
uploadManager.getChunkProgress(taskId) // Per-chunk upload state
```

### 6. Non-Hilt usage (Builder pattern)
```kotlin
val uploadSdk = UploadSdkBuilder(context)
    .baseUrl("https://api.example.com/v1/")
    .chunkSize(8 * 1024 * 1024)
    .maxRetries(5)
    .parallelUploads(3)
    .networkType(UploadConfig.NetworkType.WIFI_ONLY)
    .authTokenProvider { "Bearer ${getToken()}" }
    .build()

val taskId = uploadSdk.uploadFile(file, priority = UploadPriority.HIGH)
uploadSdk.observeUpload(taskId).collect { result -> ... }
```

## Upload Flow

```
1. Client calls uploadManager.uploadFile(file)
2. FilePreprocessor validates file, extracts MIME, generates checksum + thumbnail
3. ChunkEngine splits file into chunks (adaptive sizing)
4. Room DB persists task + chunk entities
5. UploadScheduler creates WorkManager request with:
   - Network constraint (CONNECTED or UNMETERED based on config)
   - Battery constraint (if battery-aware enabled)
   - Thermal check (defers if throttled)
   - Priority-based backoff policy
6. UploadWorker starts:
   a. Shows foreground notification
   b. Creates/resumes server session via SessionManager
   c. Uploads chunks (sequential or parallel based on config)
   d. Reports progress + speed + ETA on each chunk completion
   e. Commits upload on server
   f. Cleans up on success
7. UploadWorkObserver propagates real-time progress to UI via Flow
```

## UploadResult States

| State | Description | UI Actions |
|-------|-------------|------------|
| `Enqueued` | Task created, waiting to start | - |
| `Preprocessing` | Validating, checksumming, generating thumbnail | - |
| `Progress` | Actively uploading (percent, speed, ETA) | Pause |
| `Paused` | Upload paused by user | Resume |
| `Success` | Upload complete with remote URL | Remove |
| `Failure` | Upload failed with error and retry count | Retry, Remove |
| `Cancelled` | Upload cancelled by user | Retry, Remove |

## Project Structure

```
upload-sdk/
├── config/              # UploadConfig (baseUrl, chunkSize, networkType, etc.)
├── domain/
│   ├── model/           # UploadTask, UploadResult, ChunkInfo, UploadPriority
│   ├── repository/      # UploadRepository, ChunkRepository, SessionRepository
│   └── usecase/         # UploadUseCase
├── data/
│   ├── local/           # Room DB, DAOs, Entities (tasks, chunks, sessions)
│   ├── remote/          # Retrofit API, DTOs, Mock API, AuthInterceptor
│   ├── preprocessor/    # ChecksumCalculator, ThumbnailGenerator, FileValidator
│   ├── chunk/           # ChunkEngine (splitting), ChunkUploader (HTTP upload)
│   ├── scheduler/       # UploadScheduler, BatteryConstraint, ThermalMonitor,
│   │                    # UploadQueueManager, NetworkChangeReceiver
│   ├── coordinator/     # SessionManager (expiry), RetryCoordinator (backoff)
│   ├── repository/      # UploadRepositoryImpl (cache cleanup, cascade delete)
│   └── worker/          # UploadWorker (sequential + parallel), WorkObserver
├── di/                  # Hilt modules (Database, Network, Worker, Mock)
├── presentation/        # UploadManager, UploadSdk, UploadSdkBuilder
└── util/                # Logger, FileSizeFormatter (speed + ETA), SpeedCalculator,
                         # FileUtils, Exceptions, Analytics, Benchmark

app/
├── ui/
│   ├── screens/         # UploadListScreen, UploadDetailScreen, SettingsScreen
│   ├── components/      # UploadItem (progress + ETA), UploadThumbnail
│   └── theme/           # Material3 theme
├── viewmodel/           # UploadViewModel, UploadDetailViewModel
└── MainActivity.kt      # NavHost, permission handling
```

## Backend API Contract

Your backend needs these endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/upload/init` | POST | Create upload session |
| `/api/v1/upload/chunk` | POST | Upload chunk (multipart) |
| `/api/v1/upload/commit` | POST | Finalize and reassemble |
| `/api/v1/upload/status/{id}` | GET | Check upload status |
| `/api/v1/upload/session/refresh` | POST | Refresh expired session |

### Local Test Server (Python)
```bash
cd local-server
pip install flask werkzeug
python server.py
```

For emulator use `http://10.0.2.2:5000/api/v1/` as base URL.
For physical device use your machine's local IP.

See `local-server/README.md` for detailed setup including cleartext traffic config.

## Permissions

```xml
<!-- SDK Manifest (auto-merged) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

The app should request `POST_NOTIFICATIONS` at runtime on Android 13+ for completion/error notifications. Foreground service (progress) notifications are shown without this permission.

## Testing

### Unit Tests
```bash
./gradlew :upload-sdk:test
```

### Mock API (No backend needed)
Set `useMockApi = true` in `UploadConfig` to use the built-in `MockUploadApiService`.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build Tool | Gradle (Kotlin DSL) |
| UI Framework | Jetpack Compose (BOM) |
| Design System | Material Design 3 |
| Architecture | MVVM + Clean Architecture |
| Dependency Injection | Hilt |
| Background Task | WorkManager (CoroutineWorker) |
| Database | Room (export schema enabled) |
| Networking | Retrofit + OkHttp |
| Concurrency | Coroutines + Flow + Semaphore |
| Image Loading | Coil |
| JSON Parsing | Gson |

## License

MIT License
