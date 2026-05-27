const express = require("express");
const multer = require("multer");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const app = express();
app.use(express.json());

const UPLOAD_FOLDER = path.join(__dirname, "uploads");
const CHUNKS_FOLDER = path.join(UPLOAD_FOLDER, "chunks");

// Ensure folders exist
[UPLOAD_FOLDER, CHUNKS_FOLDER].forEach((dir) => {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
});

// In-memory storage
const sessions = {};
const chunkRegistry = {};

// Configure multer for chunk uploads
const upload = multer({ dest: CHUNKS_FOLDER });

// Helper: Generate IDs
function generateId(prefix) {
  return prefix + "_" + crypto.randomBytes(8).toString("hex");
}

// Helper: Get timestamp
function now() {
  return new Date().toLocaleTimeString();
}

// 1. Initialize Upload
app.post("/api/v1/upload/init", (req, res) => {
  const { fileName, mimeType, totalBytes, totalChunks, checksum } = req.body;

  const sessionId = generateId("sess");
  const taskId = generateId("task");

  sessions[taskId] = {
    sessionId,
    fileName: path.basename(fileName),
    mimeType,
    totalBytes,
    totalChunks,
    checksum,
    uploadedChunks: [],
    createdAt: Date.now(),
    status: "in_progress",
  };
  chunkRegistry[taskId] = {};

  console.log(`[${now()}] INIT: ${taskId} | ${fileName} | ${totalBytes} bytes`);

  res.json({
    success: true,
    sessionId,
    uploadUrl: `http://${req.headers.host}/api/v1/upload/chunk`,
    expiresAt: Date.now() + 3600000,
    taskId,
  });
});

// 2. Upload Chunk
app.post("/api/v1/upload/chunk", upload.single("file"), (req, res) => {
  const { task_id, chunk_index, total_chunks, session_id } = req.body;

  if (!task_id || chunk_index === undefined) {
    return res.status(400).json({ success: false, message: "Missing parameters" });
  }

  const chunkIdx = parseInt(chunk_index);
  const chunkFileName = `${task_id}_chunk_${chunkIdx}`;
  const chunkPath = path.join(CHUNKS_FOLDER, chunkFileName);

  // Move uploaded file to proper chunk name
  if (req.file) {
    fs.renameSync(req.file.path, chunkPath);
  }

  // Register chunk
  if (!chunkRegistry[task_id]) chunkRegistry[task_id] = {};
  chunkRegistry[task_id][chunkIdx] = `etag_${chunkIdx}`;

  if (sessions[task_id]) {
    if (!sessions[task_id].uploadedChunks.includes(chunkIdx)) {
      sessions[task_id].uploadedChunks.push(chunkIdx);
    }
  }

  const size = fs.existsSync(chunkPath) ? fs.statSync(chunkPath).size : 0;
  console.log(`[${now()}] CHUNK: ${task_id} | chunk ${parseInt(chunk_index) + 1}/${total_chunks} | ${size} bytes`);

  res.json({
    success: true,
    eTag: `etag_${chunkIdx}`,
    chunkIndex: chunkIdx,
    message: "Chunk uploaded",
  });
});

// 3. Commit Upload
app.post("/api/v1/upload/commit", (req, res) => {
  const { taskId, sessionId, fileName, totalChunks } = req.body;

  if (!sessions[taskId]) {
    return res.status(404).json({ success: false, message: "Session not found" });
  }

  const finalName = path.basename(fileName);
  let finalPath = path.join(UPLOAD_FOLDER, finalName);

  // Handle duplicates
  let counter = 1;
  const originalPath = finalPath;
  while (fs.existsSync(finalPath)) {
    const ext = path.extname(originalPath);
    const base = path.basename(originalPath, ext);
    finalPath = path.join(UPLOAD_FOLDER, `${base}_${counter}${ext}`);
    counter++;
  }

  // Reassemble chunks
  const writeStream = fs.createWriteStream(finalPath);
  for (let i = 0; i < totalChunks; i++) {
    const chunkPath = path.join(CHUNKS_FOLDER, `${taskId}_chunk_${i}`);
    if (fs.existsSync(chunkPath)) {
      const data = fs.readFileSync(chunkPath);
      writeStream.write(data);
      fs.unlinkSync(chunkPath); // Clean up
    }
  }
  writeStream.end();

  sessions[taskId].status = "completed";
  sessions[taskId].remoteUrl = `http://${req.headers.host}/uploads/${path.basename(finalPath)}`;
  sessions[taskId].fileId = `file_${taskId}`;

  const stats = fs.statSync(finalPath);
  console.log(`[${now()}] COMMIT: ${taskId} | ${path.basename(finalPath)} | ${stats.size} bytes | SAVED`);

  res.json({
    success: true,
    remoteUrl: sessions[taskId].remoteUrl,
    fileId: sessions[taskId].fileId,
    message: "Upload completed",
  });
});

// 4. Check Status
app.get("/api/v1/upload/status/:taskId", (req, res) => {
  const { taskId } = req.params;

  if (!sessions[taskId]) {
    return res.status(404).json({ success: false, message: "Task not found" });
  }

  res.json({
    taskId,
    status: sessions[taskId].status,
    uploadedChunks: sessions[taskId].uploadedChunks,
    remoteUrl: sessions[taskId].remoteUrl || null,
  });
});

// 5. Refresh Session
app.post("/api/v1/upload/session/refresh", (req, res) => {
  const { sessionId, taskId } = req.body;

  if (!sessions[taskId]) {
    return res.status(404).json({ success: false, message: "Session not found" });
  }

  const newSessionId = generateId("sess");
  sessions[taskId].sessionId = newSessionId;

  console.log(`[${now()}] REFRESH: ${taskId} | new session ${newSessionId}`);

  res.json({
    success: true,
    sessionId: newSessionId,
    uploadUrl: `http://${req.headers.host}/api/v1/upload/chunk`,
    expiresAt: Date.now() + 3600000,
  });
});

// Serve uploaded files
app.get("/uploads/:filename", (req, res) => {
  const filePath = path.join(UPLOAD_FOLDER, req.params.filename);
  if (fs.existsSync(filePath)) {
    res.sendFile(filePath);
  } else {
    res.status(404).json({ error: "File not found" });
  }
});

// Health check
app.get("/api/v1/health", (req, res) => {
  res.json({
    status: "ok",
    activeSessions: Object.keys(sessions).length,
    uploadFolder: UPLOAD_FOLDER,
  });
});

// Info page
app.get("/", (req, res) => {
  const uploads = Object.entries(sessions)
    .filter(([_, s]) => s.status === "completed")
    .map(([taskId, s]) => ({
      taskId,
      fileName: s.fileName,
      fileId: s.fileId,
      remoteUrl: s.remoteUrl,
    }));

  res.json({
    server: "Android Upload SDK Local Server (Node.js)",
    version: "1.0.0",
    uploadFolder: UPLOAD_FOLDER,
    completedUploads: uploads.length,
    uploads,
  });
});

const PORT = process.env.PORT || 5000;
app.listen(PORT, "0.0.0.0", () => {
  console.log("=".repeat(60));
  console.log("Android Upload SDK - Local Server (Node.js)");
  console.log("=".repeat(60));
  console.log(`Upload folder: ${UPLOAD_FOLDER}`);
  console.log(`API Base URL: http://localhost:${PORT}/api/v1/`);
  console.log("");
  console.log(`For Android Emulator: use http://10.0.2.2:${PORT}/api/v1/`);
  console.log(`For Physical Device: use http://YOUR_MAC_IP:${PORT}/api/v1/`);
  console.log("=".repeat(60));
});
