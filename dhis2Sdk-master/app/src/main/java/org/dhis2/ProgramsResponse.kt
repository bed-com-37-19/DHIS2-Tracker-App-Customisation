package org.dhis2

import com.google.gson.annotations.SerializedName

data class ProgramResponse(
        @SerializedName("pager") val pager: Pager,
        @SerializedName("programs") val programs: List<Program>
)

data class Pager(
        @SerializedName("page") val page: Int,
        @SerializedName("pageCount") val pageCount: Int,
        @SerializedName("total") val total: Int,
        @SerializedName("pageSize") val pageSize: Int
)

data class Program(
        @SerializedName("id") val id: String,
        @SerializedName("displayName") val displayName: String
)

