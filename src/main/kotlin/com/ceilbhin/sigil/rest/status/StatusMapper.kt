package com.ceilbhin.sigil.rest.status

class StatusMapper(val id: String, val statusTracker: StatusTracker) {

    fun setStatus(status: StatusEnum, message: String? = null, error: Throwable? = null) {
        statusTracker.setJobStatus(id, status, message, error)
    }
}