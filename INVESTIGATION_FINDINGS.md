# Investigation Findings: Remaining Work

## Current assessment

The earlier report described several defects that have since been fixed. The current code passes the sort preference to the backend, extracts timestamps before video processing, tracks uploads with unique filenames derived from their original names, validates that processed outputs exist, and emits the escaped colon in the FFmpeg drawtext expression. The old `Invalid delta` and `moov atom not found` entries in `debug.log` are historical evidence; they do not establish that those failures still occur.

No confirmed implementation defect from the old report remains. The following items are still worth completing to close the investigation and reduce ambiguity.

## Remaining work

### 1. Verify sorting end to end with real uploads

The backend's timestamp step extracts a timestamp for each stored upload before processing. When `sortByTimestamp` is enabled, `VideoService.concat` orders processed outputs using those extracted timestamps. When disabled, it follows the stored upload order.

Run a focused manual check with at least three files whose upload order differs from their timestamp order. Confirm both checkbox settings produce the expected output order. Include a filename-derived date and a file whose timestamp comes from metadata so both extraction paths are covered. The existing `debug.log` is from an earlier run and cannot verify current behavior.

### 2. Make equal or unavailable timestamp ordering deterministic

When timestamp sorting is enabled, equal timestamps are broken by the stored filename. Stored names begin with random UUIDs, so ties do not preserve upload order and may appear in a different order on another run. Missing timestamp mappings are assigned `Long.MAX_VALUE`, so those files sort last, also with UUID-based ordering.

If stable ordering is desired, retain each file's upload position and use it as the secondary sort key. Otherwise, document that ties and missing timestamps have no meaningful order.

### 3. Review obsolete test/helper assumptions

Some tests and helpers still refer to `_input_<index>.mp4` and `_processed_<index>.mp4`, while the active pipeline uses filename-based storage and `processed_<stored-filename>.mp4`. Update or remove stale expectations where they no longer describe production behavior, especially in `FileUtilsSpec`, `FileServiceSpec`, and `VideoServiceSpec`.

## Closed findings

- The sort checkbox is sent to the backend and controls concatenation order.
- Timestamps are collected before preprocessing; sorting is applied when building the final concat list.
- The drawtext filter includes the escaped separator between timestamp and format.
- Uploads are tracked by unique sanitized filenames, and missing processed outputs fail the job before concat.

## Evidence limits

This assessment is based on the current source and checked-in tests/logs. No tests or new uploads were run as part of this review. The historical FFmpeg errors should only be reopened if they recur in a fresh run against the current code.
