package com.ceilbhin.sigil.rest.status

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
class JobStatus(
    val id: String,
    val status: StatusEnum = StatusEnum.PENDING,
    val message: String? = null,
    val error: Throwable? = null)