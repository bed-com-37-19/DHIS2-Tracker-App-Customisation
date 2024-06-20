package org.dhis2.usescases.settings

import org.hisp.dhis.android.core.program.Program
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface ProgramService {
    //abstract fun uid(): String?

    @GET("/api/programs/{id}/metadata.json")
    fun getProgram(@Path("id") programId: String?): Call<Program?>?
    //fun getProgram(@Path("id") programId: String?): Call<Metadata>
}