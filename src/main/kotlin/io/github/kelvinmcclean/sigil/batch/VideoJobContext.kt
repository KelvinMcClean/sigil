package io.github.kelvinmcclean.sigil.batch

interface VideoJobContext {
    val fileCount: Long
    val fileDirectory: String
    val title: String
    var timestamps: List<Long>
    val stabilize: Boolean
    val originalFilenames: List<String>
}

data class VideoJobContextImpl(
    override val fileCount: Long,
    override val fileDirectory: String,
    override val title: String,
    override var timestamps: List<Long>,
    override val stabilize: Boolean,
    override val originalFilenames: List<String>
) : VideoJobContext
