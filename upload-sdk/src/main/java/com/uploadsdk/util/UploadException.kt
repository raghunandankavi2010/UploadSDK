package com.uploadsdk.util

sealed class UploadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkException(message: String, cause: Throwable? = null) : UploadException(message, cause)
    class FileNotFoundException(path: String) : UploadException("File not found: $path")
    class InvalidFileException(message: String) : UploadException(message)
    class ServerException(message: String, val code: Int) : UploadException(message)
    class SessionExpiredException(sessionId: String) : UploadException("Session expired: $sessionId")
    class ChecksumMismatchException : UploadException("Chunk checksum verification failed")
    class QuotaExceededException : UploadException("Upload quota exceeded")
    class ThrottledException(retryAfter: Long) : UploadException("Request throttled. Retry after ${retryAfter}ms")
}
