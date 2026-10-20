# Investigation Findings: Video Reordering and Timestamp Issues

## Executive Summary

After analyzing the codebase and the provided debug log, I've identified **four critical issues** that explain the reported problems:

1. **Frontend-Backend State Mismatch**: The `sortByTimestamp` checkbox state is not being passed to the backend
2. **Premature Processing / Wrong Order**: Files are processed before timestamp-based sorting is complete (likely introduced when trying to strip excess `:` for date-only formatting)
3. **Malformed FFmpeg Drawtext Filter**: Timestamp format strings are incorrectly constructed, causing FFmpeg to fail
4. **Index-Based Filename Issues**: Using numeric indexes for file naming causes problems with reordering and tracking

---

## Issue 1: Reordering When Unselected

### Problem
The user reports that "videos are being reordered when the reorder button is unselected."

### Root Cause
In `VideoController.kt` (lines 33-37):
```kotlin
val orderedFiles = if (timestamps != null && timestamps.size == files.size) {
    files.indices
        .sortedBy { timestamps[it] }
        .map { files[it] }
        .toTypedArray()
} else {
    files
}
```

**The controller always reorders files if timestamps are provided**, regardless of whether the user wants them sorted or not. The frontend's `sortByTimestamp` state (from the checkbox in `App.js` line 14) is **never passed to the backend**.

### Impact
- Even when the user unchecks "Sort by timestamp", if the frontend extracted timestamps from filenames, those timestamps are sent to the backend
- The backend then reorders files based on those timestamps anyway
- User has no control over whether reordering happens

### Evidence from Log
```
2026-10-20 00:13:20 - Processing timestamps for file: _input_0.mp4
2026-10-20 00:13:20 - Final filtergraph for file _input_0.mp4: ...
```
Files are being processed with timestamps, but there's no indication of user preference.

---

## Issue 2: Incorrect Timestamp Ordering

### Problem
The user reports videos "possibly not always being put in the correct order by timestamp."

### Root Cause 1: Premature Processing
In `VideoBatchConfig.kt`, the job flow is:
1. `buildTimestampStep` - Extracts timestamps from files
2. `processFilesStep` - Processes each file individually
3. `concatStep` - Concatenates all processed files

However, in `TimestampService.kt` (lines 143-160 in current code), the `sortInputsByTimestamp` function is called **inside** `buildTimestamps`:
```kotlin
if (index == videoJobContext.fileCount.toInt() - 1) {
    sortInputsByTimestamp(videoJobContext)
}
```

This means:
- Files are processed in their original order (0, 1, 2, 3...) during `processFilesStep`
- The sorting only happens AFTER all timestamps are collected
- The processed files (`_processed_*.mp4`) are already created in the wrong order
- The sorting renames the INPUT files (`_input_*.mp4`) but the processed files remain in original order

### Root Cause 2: Sorting Logic Flaw
In `TimestampService.kt` (lines 143-160):
```kotlin
private fun sortInputsByTimestamp(videoJobContext: VideoJobContext) {
    if (videoJobContext.timestamps.size != videoJobContext.fileCount.toInt()) return

    val workingDir = File(videoJobContext.fileDirectory)
    val sortedIndices = videoJobContext.timestamps.indices.sortedBy { videoJobContext.timestamps[it] }
    val temporaryFiles = sortedIndices.mapIndexed { sortedIndex, originalIndex ->
        val source = File(workingDir, "_input_$originalIndex.mp4")
        val temporary = File(workingDir, "_sorted_input_$sortedIndex.mp4")
        if (!source.renameTo(temporary)) {
            throw IllegalStateException("Unable to stage ${source.name} for timestamp sorting")
        }
        temporary
    }
    temporaryFiles.forEachIndexed { sortedIndex, temporary ->
        if (!temporary.renameTo(File(workingDir, "_input_$sortedIndex.mp4"))) {
            throw IllegalStateException("Unable to apply timestamp order to ${temporary.name}")
        }
    }
    videoJobContext.timestamps = sortedIndices.map { videoJobContext.timestamps[it] }
}
```

**This sorting happens TOO LATE** - after files have already been processed. The `_processed_*.mp4` files are created in the original order, not the sorted order.

### User Insight
This issue likely came through from a recent change, an attempt to strip excess `:` when we're just trying to use the date without seconds. This is related to the date-only formatting feature.

### Impact
- Processed files are in the wrong order
- Concatenation produces videos in incorrect chronological order
- Even though input files get renamed, the processed versions don't match

### Git History Note
The sorting logic was added in commit `a3406d8` ("Some final changes") which came after commit `f474508` ("Add sort by timestamp feature with checkbox in GUI"). This suggests the sorting implementation was added after the frontend feature, but the timing is wrong.

---

## Issue 3: FFmpeg Drawtext Filter Failure

### Problem
The user reports "clips being entirely missing from the whole" - this is because FFmpeg processing fails.

### Root Cause
In `TimestampService.kt` (lines 74-78):
```kotlin
fun processTimestamps(timestamp: Long, fontOption: String?, isDateOnly: Boolean): String {
    val size = fontConfiguration.size
    val location = fontConfiguration.location
    val format = if (isDateOnly) getDateOnlyFormat(fontConfiguration.format) else fontConfiguration.format
    return StringBuilder().append(",drawtext=fontfile=$fontOption: text='%{pts\\:localtime\\:")
        .append(timestamp)
        .append(format)
        .append("': x=${location.x}: y=${location.y}: fontcolor=white: fontsize=${size}: box=1: boxcolor=black@0.5").toString()
}
```

The function appends the timestamp value directly to the format string. For example:
- Timestamp: `1773446400`
- Format: `%a %Y-%m-%d`
- Result: `%{pts\:localtime\:1773446400%a %Y-%m-%d}`

**This is malformed!** The correct FFmpeg syntax should be:
```
%{pts\:localtime\:1773446400\:%a %Y-%m-%d}
```

Note the missing `\:` separator between the timestamp and the format string.

### Evidence from Log
```
[Parsed_drawtext_4 @ 000001fd0f45c080] Invalid delta '1773446400%a %Y-%m-%d'
[vf#0:0 @ 000001fd0ebb00c0] Error while filtering: Invalid argument
```

FFmpeg cannot parse the malformed filter string, causing the entire processing to fail.

### Evidence from Tests
Looking at `TimestampServiceSpec.groovy` (line 68), the expected format is:
```groovy
filter == ",drawtext=fontfile=app-font.ttf: text='%{pts\\:localtime\\:${JUN_15_2024}\\:%Y}':" +
        " x=w-tw-20: y=h-th-20: fontcolor=white: fontsize=48: box=1: boxcolor=black@0.5"
```

**The test expects `\\:` (escaped colon) between the timestamp and format!** This confirms the current implementation is missing this separator.

### Git History Note
This bug has been present since at least commit `825c3ba` ("Stitching enhancements") and persists through all subsequent commits including the current HEAD (`451c0f1`). The test was likely written with the correct format but the implementation was never fixed.

### Impact
- FFmpeg exits with code -22 (Invalid argument)
- No processed file is created (`_processed_*.mp4` is missing)
- The repair mechanism tries to fix the input file, but the timestamp filter is still malformed
- Concatenation fails because processed files don't exist

---

## Issue 4: Index-Based Filename Issues

### Problem
The user reports "clips being entirely missing from the whole." Additionally, the user wants to move away from using indexes as any part of video processing/saving and prefers to keep sub-video filenames if possible.

### Current Situation
The system currently uses numeric indexes for all file operations:
- Input files: `_input_0.mp4`, `_input_1.mp4`, etc.
- Processed files: `_processed_0.mp4`, `_processed_1.mp4`, etc.
- The `VideoJobContext` tracks files by index

### Root Cause
This is a **cascade effect** from Issues 2 and 3, but also a design issue:

1. FFmpeg processing fails due to malformed drawtext filter (Issue 3)
2. `_processed_0.mp4` (and others) are never created
3. During concatenation in `VideoService.kt` (lines 67-72):
```kotlin
val files = workingDir.listFiles()?.filter { 
    it.isFile && it.name.endsWith(".mp4") && it.name.startsWith("_processed_") 
}?.sortedBy { it.name } ?: emptyList()
```

The concat step tries to find all `_processed_*.mp4` files, but some are missing.

4. The file list for concatenation is incomplete
5. FFmpeg concat demuxer fails with:
```
[mov,mp4,m4a,3gp,3g2,mj2 @ 00000260cd380b40] moov atom not found
[in#0 @ 00000260cd312b40] Impossible to open '_processed_0.mp4'
```

### User Requirement
**Move away from using indexes as any part of video processing/saving. Keep sub-video filenames if possible.**

This means:
- Instead of `_input_0.mp4`, use the original filename or a derived name
- Instead of `_processed_0.mp4`, use a name based on the original file
- Track files by their actual names rather than numeric indexes
- This will make reordering more robust and easier to debug

### Impact
- Some clips are entirely missing from the final output
- The final video is incomplete
- Debugging is harder because files lose their original identities

---

## Recommended Fixes

### Fix 1: Pass sortByTimestamp to Backend
**File**: `frontend/src/App.js`

Add the `sortByTimestamp` state to the FormData (around line 73):
```javascript
const formData = new FormData();
files.forEach((file, index) => {
  formData.append('files', file);
});
formData.append('sortByTimestamp', sortByTimestamp);  // ADD THIS
```

**File**: `VideoController.kt`

Update to respect the sort preference:
```kotlin
@PostMapping("/process", consumes = ["multipart/form-data"])
fun process(
    @RequestParam("files") files: Array<MultipartFile>,
    @RequestParam(value = "timestamps", required = false) timestamps: Array<Long>?,
    @RequestParam(value = "title", required = false) title: String?,
    @RequestParam(value = "stabilize", defaultValue = "false", required = false) stabilize: Boolean,
    @RequestParam(value = "sortByTimestamp", defaultValue = "false", required = false) sortByTimestamp: Boolean  // ADD THIS
) {
    val orderedFiles = if (sortByTimestamp && timestamps != null && timestamps.size == files.size) {
        files.indices
            .sortedBy { timestamps[it] }
            .map { files[it] }
            .toTypedArray()
    } else {
        files
    }
    val orderedTimestamps = if (sortByTimestamp && timestamps != null && timestamps.size == files.size) {
        files.indices.sortedBy { timestamps[it] }.map { timestamps[it] }.toTypedArray()
    } else {
        timestamps
    }
    // ...
}
```

### Fix 2: Fix Drawtext Filter Syntax
**File**: `TimestampService.kt`

Update the `processTimestamps` function to add the `\:` separator (line 78):
```kotlin
fun processTimestamps(timestamp: Long, fontOption: String?, isDateOnly: Boolean): String {
    val size = fontConfiguration.size
    val location = fontConfiguration.location
    val format = if (isDateOnly) getDateOnlyFormat(fontConfiguration.format) else fontConfiguration.format
    return StringBuilder().append(",drawtext=fontfile=$fontOption: text='%{pts\\:localtime\\:")
        .append(timestamp)
        .append("\\:")  // ADD THIS - the colon separator required by FFmpeg
        .append(format)
        .append("': x=${location.x}: y=${location.y}: fontcolor=white: fontsize=${size}: box=1: boxcolor=black@0.5").toString()
}
```

This matches the expected format in the test file (`TimestampServiceSpec.groovy` line 68).

### Fix 3: Use Original Filenames Instead of Indexes
**Architectural Change** - Move away from index-based file naming:

**File**: `FileUtils.kt`

Change the file transfer to preserve original filenames:
```kotlin
@JvmStatic
@Throws(IOException::class)
fun transfer(files: Array<MultipartFile>, tmpPath: Path) {
    for ((i, element) in files.withIndex()) {
        val file = element
        // Use a sanitized version of the original filename instead of _input_$i.mp4
        val safeName = sanitizeFilename(file.originalFilename ?: "video_$i.mp4")
        val destinationPath: Path = tmpPath.resolve(safeName)
        file.transferTo(destinationPath)
    }
}

private fun sanitizeFilename(filename: String): String {
    // Remove path separators and invalid characters
    return filename.replace("[/\\:*?\"<>|]".toRegex(), "_")
}
```

**File**: `VideoJobContext.kt`

Update to track original filenames:
```kotlin
interface VideoJobContext {
    val fileCount: Long
    val fileDirectory: String
    val title: String
    var timestamps: List<Long>
    val stabilize: Boolean
    val originalFilenames: List<String>
    // ADD: Map from original filename to timestamp
    var filenameToTimestamp: Map<String, Long> = emptyMap()
}
```

**File**: `TimestampService.kt`

Update to work with filenames instead of indexes:
```kotlin
fun buildTimestamps(videoJobContext: VideoJobContext, index: Int) {
    val workingDir = File(videoJobContext.fileDirectory)
    val originalFilename = if (index < videoJobContext.originalFilenames.size) 
        videoJobContext.originalFilenames[index] else null
    
    if (originalFilename != null) {
        val inputFilePath = workingDir.listFiles()?.find { it.name == originalFilename }?.name
        if (inputFilePath != null) {
            val timestamp = ffmpegUtils.getTimestamp(inputFilePath, workingDir, originalFilename)
            // Store mapping of filename to timestamp
            videoJobContext.filenameToTimestamp += (originalFilename to timestamp)
        }
    }
}

fun getTimestampFilter(videoJobContext: VideoJobContext, filename: String): String {
    val workingDir = File(videoJobContext.fileDirectory)
    val userFont = fontConfiguration.path
    val fontOption: String = FontResolver().resolveFont(workingDir.toPath(), userFont)
    val timestamp = videoJobContext.filenameToTimestamp[filename] ?: 0L
    val isDateOnly = isDateOnlyTimestamp(timestamp)
    return processTimestamps(timestamp, fontOption, isDateOnly)
}
```

**File**: `VideoService.kt`

Update to process files by name:
```kotlin
fun preprocess(videoJobContext: VideoJobContext, filename: String) {
    val stabilize = videoJobContext.stabilize
    val workingDir = File(videoJobContext.fileDirectory)
    val file = workingDir.listFiles()?.find { it.isFile && it.name == filename } ?: return
    
    logger.info { "Processing file: ${file.name}" }
    val inputFilePath = filename
    val outputFilePath = "processed_${filename}"  // Keep original name
    
    // ... rest of processing
}

fun concat(videoJobContext: VideoJobContext) {
    val finalPath = fileService.getFinalPath()
    val workingDir = File(videoJobContext.fileDirectory)
    
    // Get all processed files, sorted by timestamp
    val processedFiles = workingDir.listFiles()?.filter { 
        it.isFile && it.name.endsWith(".mp4") && it.name.startsWith("processed_")
    }?.toList() ?: emptyList()
    
    // Sort by timestamp using the filename mapping
    val sortedFiles = processedFiles.sortedBy { file ->
        val originalName = file.name.removePrefix("processed_")
        videoJobContext.filenameToTimestamp[originalName] ?: Long.MAX_VALUE
    }
    
    // Create the file list for concat demuxer
    val fileListPath = workingDir.resolve("_fileList.txt")
    fileListPath.writeText(sortedFiles.joinToString("\n") { "file '${it.name}'" })
    
    val exitCode = ffmpegUtils.concat(fileListPath.toPath(), finalPath, workingDir)
    logger.info { "FFmpeg concatenation process exited with code: $exitCode" }
}
```

**File**: `VideoBatchConfig.kt`

Update to process files by name instead of index:
```kotlin
@Bean
@StepScope
fun ffmpegProcessor(
    videoJobContext: VideoJobContext,
    videoService: VideoService
): FunctionItemProcessor<String, String> {  // Change from Int to String
    return FunctionItemProcessor { filename: String ->
        videoService.preprocess(videoJobContext, filename)
        filename
    }
}

@Bean
@StepScope
fun indexReader(jobConfig: VideoJobContext): ListItemReader<String> {  // Change from Int to String
    // Read the actual filenames from the directory
    val workingDir = File(jobConfig.fileDirectory)
    val files = workingDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.map { it.name } ?: emptyList()
    return ListItemReader(files)
}
```

### Fix 4: Sort Before Processing (If Still Using Indexes)
If you decide to keep using indexes temporarily, ensure sorting happens BEFORE processing:

**File**: `VideoBatchConfig.kt`

Change the job flow to add a dedicated sort step BEFORE processing:
```kotlin
@Bean
fun videoProcessingJob(jobRepository: JobRepository,
                       buildTimestampStep: Step,
                       sortFilesStep: Step,
                       processFilesStep: Step,
                       concatStep: Step,
                       listener: CleanupJobListener): Job {
    return JobBuilder("videoProcessingJob", jobRepository)
        .start(buildTimestampStep)
        .next(sortFilesStep)  // Sort before processing
        .next(processFilesStep)
        .next(concatStep)
        .listener(listener)
        .build()
}

@Bean
fun sortFilesStep(
    jobRepository: JobRepository,
    indexReader: ListItemReader<Int>,
    sortProcessor: ItemProcessor<Int, Int>,
    noOpWriter: ItemWriter<Int>
): Step {
    return StepBuilder("sortFilesStep", jobRepository)
        .chunk<Int, Int>(1)
        .reader(indexReader)
        .processor(sortProcessor)
        .writer(noOpWriter)
        .build()
}

@Bean
@StepScope
fun sortProcessor(
    videoJobContext: VideoJobContext,
    timestampService: TimestampService
): FunctionItemProcessor<Int, Int> {
    return FunctionItemProcessor { index: Int ->
        if (index == videoJobContext.fileCount.toInt() - 1) {
            timestampService.sortInputsByTimestamp(videoJobContext)
        }
        index
    }
}
```

**File**: `TimestampService.kt`

Remove the sorting from `buildTimestamps`:
```kotlin
fun buildTimestamps(videoJobContext: VideoJobContext, index: Int) {
    if (videoJobContext.timestamps.size <= index) {
        val inputFilePath = "_input_$index.mp4"
        val workingDir = File(videoJobContext.fileDirectory)
        val originalFilename = if (index < videoJobContext.originalFilenames.size) 
            videoJobContext.originalFilenames[index] else null
        val timestamp = ffmpegUtils.getTimestamp(inputFilePath, workingDir, originalFilename)
        videoJobContext.timestamps += timestamp
    }
    // REMOVED: The sorting logic that was here
}
```

### Fix 5: Better Error Handling for Missing Files
**File**: `VideoService.kt`

Update the concat function to handle missing files gracefully:
```kotlin
fun concat(videoJobContext: VideoJobContext) {
    val finalPath = fileService.getFinalPath()
    val workingDir = File(videoJobContext.fileDirectory)
    val files = workingDir.listFiles()?.filter { 
        it.isFile && it.name.endsWith(".mp4") && it.name.startsWith("processed_")
    }?.sortedBy { it.name } ?: emptyList()
    
    logger.info { "Final output file path: $finalPath" }
    
    // Check if we have all expected processed files
    val expectedCount = videoJobContext.fileCount.toInt()
    if (files.size < expectedCount) {
        logger.error { "Missing processed files: expected $expectedCount, found ${files.size}" }
        val expectedFiles = (0 until expectedCount).map { "processed_$it.mp4" }
        val missingFiles = expectedFiles.filter { expected -> 
            files.none { it.name == expected }
        }
        logger.error { "Missing files: ${missingFiles.joinToString(", ")}" }
        throw IllegalStateException("Missing processed files: expected $expectedCount, found ${files.size}. Missing: ${missingFiles.joinToString(", ")}")
    }
    
    // Create the file list for concat demuxer
    val fileListPath = workingDir.resolve("_fileList.txt")
    fileListPath.writeText(files.joinToString("\n") { "file '${it.name}'" })
    
    // Use concat demuxer
    val exitCode = ffmpegUtils.concat(fileListPath.toPath(), finalPath, workingDir)
    logger.info { "FFmpeg concatenation process exited with code: $exitCode" }
}
```

---

## Priority Order

| Priority | Issue | Impact | Fix Complexity |
|----------|-------|--------|----------------|
| **P0** | FFmpeg Drawtext Filter Failure | Critical - causes complete processing failure | Low - simple string fix |
| **P0** | Index-Based Filenames | Critical - causes tracking/reordering issues | Medium - architectural change |
| **P0** | Missing Clips | Critical - data loss | Low - better error handling |
| **P1** | Reordering When Unselected | Medium - UX issue | Medium - frontend/backend changes |
| **P1** | Incorrect Timestamp Ordering | Medium - wrong output order | Medium - workflow restructuring |

---

## Recommended Implementation Order

1. **Fix 2 (Drawtext Filter)** - This is the most critical and easiest to fix. Will immediately stop FFmpeg failures.
2. **Fix 5 (Error Handling)** - Better error messages will help diagnose remaining issues.
3. **Fix 3 (Use Original Filenames)** - This is the architectural change the user requested. Will make the system more robust.
4. **Fix 1 (Pass sortByTimestamp)** - UX improvement.
5. **Fix 4 (Sort Before Processing)** - Only needed if still using indexes temporarily.

---

## Testing Recommendations

After implementing fixes, test with:

1. **Unsorted upload**: Upload files without checking "Sort by timestamp", verify they maintain original order
2. **Sorted upload**: Upload files with "Sort by timestamp" checked, verify they're ordered by timestamp
3. **Mixed timestamps**: Upload files with various timestamp formats (filename, metadata, modification time)
4. **Edge cases**: Empty timestamps, duplicate timestamps, single file upload
5. **FFmpeg validation**: Verify the drawtext filter string is correctly formatted before execution
6. **Existing tests**: The test in `TimestampServiceSpec.groovy` line 68 already validates the correct format - ensure it passes
7. **Filename preservation**: Verify original filenames are preserved throughout the pipeline

---

## Files to Modify

### Backend (Kotlin)
- `src/main/kotlin/io/github/kelvinmcclean/sigil/timestamp/TimestampService.kt`
- `src/main/kotlin/io/github/kelvinmcclean/sigil/batch/VideoBatchConfig.kt`
- `src/main/kotlin/io/github/kelvinmcclean/sigil/batch/VideoJobContext.kt`
- `src/main/kotlin/io/github/kelvinmcclean/sigil/rest/controller/VideoController.kt`
- `src/main/kotlin/io/github/kelvinmcclean/sigil/media/VideoService.kt`
- `src/main/kotlin/io/github/kelvinmcclean/sigil/files/FileUtils.kt`

### Frontend (JavaScript)
- `frontend/src/App.js`

---

## Additional Observations

1. **Timestamp Extraction Logic**: The `extractDateFromFilename` function in `FfmpegUtils.kt` looks robust and handles multiple date formats well (YYYYMMDD, YYYY-MM-DD, YYYY/MM/DD).

2. **Fallback Chain**: The timestamp extraction has a good fallback chain:
   - Try creation_time from metadata
   - Try extracting from filename
   - Fall back to file modification time
   - Ultimate fallback to epoch (0)

3. **Date-Only Detection**: The `isDateOnlyTimestamp` function correctly identifies timestamps that represent midnight UTC (divisible by 86400).

4. **Format Cleaning**: The `getDateOnlyFormat` function does a reasonable job of removing time-related specifiers from the format string. However, this is where the `:` stripping issue likely originated.

5. **Test Coverage**: The existing test in `TimestampServiceSpec.groovy` actually validates the CORRECT format with the `\\:` separator. This suggests the bug was introduced after the test was written, or the test is not being run in CI.

---

## Git History Analysis

Looking at the recent commits on the `feature/stitch-enhancements` branch:

```
451c0f1 Add debug log
 a3406d8 Some final changes
 3068663 Fix encoding
 f474508 Add sort by timestamp feature with checkbox in GUI
 825c3ba Stitching enhancements
```

- **f474508**: Added the frontend checkbox for "Sort by timestamp"
- **a3406d8**: Added the backend sorting logic (`sortInputsByTimestamp`)
- **3068663**: Fixed encoding issues
- **451c0f1**: Added debug log (current HEAD)

The sorting feature was added in two parts, but the backend sorting happens too late in the pipeline. The date-only formatting (and likely the `:` stripping issue) was probably introduced in the encoding fix or stitching enhancements.

---

## Questions for Clarification

1. Should the "Sort by timestamp" checkbox default to checked or unchecked?
2. When moving to filename-based tracking, should we:
   - Use the original filenames as-is (with sanitization)?
   - Create new unique names based on timestamps?
   - Use a combination (timestamp + original name)?
3. Should we add validation to reject jobs if timestamps cannot be reliably determined?
4. Should we add a dry-run mode that shows the proposed order before processing?
5. Why are the existing tests (which expect the correct format) passing if the code has the bug?
6. The test in `TimestampServiceSpec.groovy` expects the colon separator - has this test been passing? If so, the implementation might have the bug but the test uses a different code path.
