package org.dhis2.export

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.dhis2.Program2
import org.dhis2.R
import org.dhis2.commons.viewmodel.DispatcherProvider
import org.dhis2.usescases.settings.ProgramService

import org.dhis2.usescases.settings.SyncManagerPresenter
import org.dhis2.usescases.settings.models.ExportDbModel
import org.hisp.dhis.android.core.D2
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ProgramList : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: ProgramAdapter
    private var fileToSave: File? = null
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_program_list, container, false)

        recyclerView = view.findViewById(R.id.programListRecyclerView)
        fab = view.findViewById(R.id.fab)

        recyclerView.layoutManager = LinearLayoutManager(context)

        fetchPrograms()

        return view
    }

    private fun fetchPrograms() {
        CoroutineScope(Dispatchers.IO).launch {
            val username = "admin"
            val password = "district"
            val auth = "Basic " + android.util.Base64.encodeToString("$username:$password".toByteArray(), android.util.Base64.NO_WRAP)
            val url = URL("https://play.im.dhis2.org/stable-2-38-6/api/programs")
            var urlConnection: HttpURLConnection? = null

            try {
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Authorization", auth)
                urlConnection.connect()

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = urlConnection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    val programs = parsePrograms(response)

                    withContext(Dispatchers.Main) {
                        setupRecyclerView(programs)
                    }
                } else {
                    // Handle error response
                }
            } catch (e: Exception) {
                // Handle network failure
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    private fun parsePrograms(response: String): List<Program2> {
        val programs = mutableListOf<Program2>()
        val jsonArray = JSONObject(response).getJSONArray("programs")

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val id = jsonObject.getString("id")
            val name = jsonObject.getString("displayName")
            programs.add(Program2(id, name))
        }

        return programs
    }

    private fun setupRecyclerView(programs: List<Program2>) {
        adapter = ProgramAdapter(programs) { program ->
            adapter.toggleSelection(program)
            updateFabVisibility()
        }

        recyclerView.adapter = adapter
    }

    private fun exportSelectedPrograms(context: Context, adapter: ProgramAdapter) {
        val selectedPrograms = adapter.getSelectedPrograms()
        if (selectedPrograms.isNotEmpty()) {
            try {
                // Extract all unique program IDs from selected programs
                val programIds = selectedPrograms.map { it.id }.distinct()

                if (programIds.isNotEmpty()) {
                    val username = "admin"
                    val password = "district"
                    val auth = "Basic " + android.util.Base64.encodeToString("$username:$password".toByteArray(), android.util.Base64.NO_WRAP)
                    val apiUrl = "https://play.im.dhis2.org/stable-2-38-6/api/metadata?programs:filter=id:in:[${programIds.joinToString(",")}]"

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val response = getUrlContent(apiUrl, auth)
                            withContext(Dispatchers.Main) {
                                Timber.d("API response: $response")
                                try {
                                    val jsonFile = createJsonFile(response, context)
                                    Timber.d("JSON File created at: ${jsonFile.absolutePath}")
                                    downloadJsonFile(jsonFile, "selected_programs.json", context)

                                    Toast.makeText(context, "Exported selected programs as JSON", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Timber.e(e, "Error creating JSON file")
                                    Toast.makeText(context, "Error creating JSON file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Timber.e(e, "Error making API request: ${e.localizedMessage ?: e.toString()}")
                                Toast.makeText(context, "Error making API request: ${e.localizedMessage ?: e.toString()}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "No programs selected to export", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error exporting programs")
                Toast.makeText(context, "Error exporting programs: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No programs selected to export", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun getUrlContent(apiUrl: String, auth: String): String {
        val url = URL(apiUrl)
        val urlConnection = url.openConnection() as HttpURLConnection
        urlConnection.requestMethod = "GET"
        urlConnection.setRequestProperty("Authorization", auth)
        urlConnection.connect()

        val responseCode = urlConnection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = urlConnection.inputStream
            return inputStream.bufferedReader().use { it.readText() }
        } else {
            throw Exception("Failed to retrieve data from API. Response code: $responseCode")
        }
    }
    private fun createJsonFile(json: String, context: Context): File {
        val jsonFile = File(context.filesDir, "selected_programs.json")
        try {
            FileWriter(jsonFile).use { writer ->
                writer.write(json)
                writer.flush()
            }
            Timber.d("JSON file written successfully: ${jsonFile.absolutePath}")
            verifyFileContent(jsonFile); // Check file content after writing
        } catch (e: Exception) {
            Timber.e(e, "Error writing JSON file: ${e.message}")
        }
        return jsonFile
    }
    private fun downloadJsonFile(file: File, fileName: String, context: Context) {
        fileToSave = file // Store the file to save
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
            )

            intent.putExtra(Intent.EXTRA_STREAM, contentUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Start the activity to save the file
            startActivityForResult(intent, REQUEST_CODE_SAVE_FILE)

            // Handle the result in onActivityResult
        } catch (e: Exception) {
            Timber.e(e, "Error downloading file")
            Toast.makeText(context, "Error downloading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SAVE_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri!= null && fileToSave!= null) {
                try {
                    context?.contentResolver?.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            fileToSave!!.inputStream().copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, "File saved successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "Error saving file")
                    Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private companion object {
        const val REQUEST_CODE_SAVE_FILE = 1001
    }

    private fun verifyFileContent(file: File) {
        if (file.exists()) {
            try {
                val content = file.readText()
                if (content.isEmpty()) {
                    Timber.w("Downloaded JSON file is empty!")
                } else {
                    Timber.d("File Content: $content")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reading file content")
            }
        } else {
            Timber.w("File doesn't exist: ${file.absolutePath}")
        }
    }

    private fun updateFabVisibility() {
        if (adapter.getSelectedPrograms().isNotEmpty()) {
            fab.visibility = View.VISIBLE
            fab.setOnClickListener {
                exportSelectedPrograms(requireContext(), adapter)
            }
        } else {
            fab.visibility = View.GONE
        }
    }
}
