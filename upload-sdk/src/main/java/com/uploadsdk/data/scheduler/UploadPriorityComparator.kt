package com.uploadsdk.data.scheduler

import com.uploadsdk.data.local.entity.UploadTaskEntity
import com.uploadsdk.domain.model.UploadPriority

object UploadPriorityComparator : Comparator<UploadTaskEntity> {
    override fun compare(a: UploadTaskEntity, b: UploadTaskEntity): Int {
        val priorityA = UploadPriority.valueOf(a.priority).ordinal
        val priorityB = UploadPriority.valueOf(b.priority).ordinal
        return if (priorityA != priorityB) {
            priorityB - priorityA // Higher priority first
        } else {
            (a.createdAt - b.createdAt).toInt() // Older first
        }
    }
}
