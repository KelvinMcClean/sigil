package com.ceilbhin.sigil.batch

data class VideoJobContext(
    val jobId: String,
    val fileCount: Long,
    val fileDirectory: String? = null,
    val timestamps: List<Long>,
    val stabilize: Boolean
)