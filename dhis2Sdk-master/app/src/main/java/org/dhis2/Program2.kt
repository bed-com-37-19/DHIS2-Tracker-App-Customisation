package org.dhis2

import com.fasterxml.jackson.annotation.JsonProperty

data class Program2(
        @JsonProperty("id")
        val id: String,
        val name: String,

)
