package com.ceilbhin.sigil.batch

interface VideoJobContext {
    val fileCount: Long
    val fileDirectory: String
    val timestamps: List<Long>
    val stabilize: Boolean
}

data class VideoJobContextImpl(
    override val fileCount: Long,
    override val fileDirectory: String,
    override val timestamps: List<Long>,
    override val stabilize: Boolean
) : VideoJobContext