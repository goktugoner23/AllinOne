package com.example.allinone.utils

import android.content.Context
import android.util.Log
import com.example.allinone.data.model.SourceCodeModel
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference

object SourceCodeUtils {
    private const val TAG = "SourceCodeUtils"
    
    // Cache for loaded file content to prevent multiple loads
    private val contentCache = mutableMapOf<String, WeakReference<String>>()
    // Cache for file structure to prevent reloading
    private var fileStructureCache: SourceCodeModel? = null
    
    fun createSourceCodeModel(context: Context): SourceCodeModel {
        val projectRoot = File(context.filesDir, "source_code")
        return createModelFromDirectory(projectRoot)
    }

    private fun createModelFromDirectory(directory: File): SourceCodeModel {
        val children = mutableListOf<SourceCodeModel>()
        
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                children.add(createModelFromDirectory(file))
            } else {
                children.add(SourceCodeModel(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = false,
                    // Don't load content upfront
                    content = null
                ))
            }
        }

        return SourceCodeModel(
            name = directory.name,
            path = directory.absolutePath,
            isDirectory = true,
            children = children
        )
    }

    fun saveSourceCodeToJson(context: Context, model: SourceCodeModel) {
        val jsonFile = File(context.filesDir, "source_code.json")
        jsonFile.writeText(modelToJson(model).toString(2))
    }

    fun loadSourceCodeFromJson(context: Context): SourceCodeModel? {
        // Use cached structure if available
        fileStructureCache?.let { return it }
        
        return try {
            Log.d(TAG, "Attempting to load source_code.json from assets")
            
            // Check if the file exists in assets
            val assetsFiles = context.assets.list("")
            if (assetsFiles?.contains("source_code.json") != true) {
                Log.e(TAG, "source_code.json not found in assets: ${assetsFiles?.joinToString()}")
                return createEmptySourceCode()
            }
            
            Log.d(TAG, "source_code.json found in assets, reading content")
            
            // Read the entire JSON file as string
            val jsonString = context.assets.open("source_code.json").bufferedReader().use { it.readText() }
            Log.d(TAG, "JSON file size: ${jsonString.length} characters")
            
            // Parse JSON
            val jsonObject = JSONTokener(jsonString).nextValue() as JSONObject
            Log.d(TAG, "JSON parsed successfully")
            
            // Create model from JSON
            val model = parseDirectoryStructure(jsonObject)
            Log.d(TAG, "Model created: ${model.name}, isDirectory: ${model.isDirectory}, children: ${model.children?.size ?: 0}")
            
            // Cache the file structure
            fileStructureCache = model
            
            model
        } catch (e: Exception) {
            Log.e(TAG, "Error loading source code structure", e)
            createEmptySourceCode()
        }
    }
    
    private fun createEmptySourceCode(): SourceCodeModel {
        return SourceCodeModel(
            name = "root",
            path = "/",
            isDirectory = true,
            content = null,
            children = listOf(
                SourceCodeModel(
                    name = "Error loading source code",
                    path = "/error",
                    isDirectory = false,
                    content = "Unable to load source code. Please check logs for details."
                )
            )
        )
    }
    
    // Parse only the directory and file structure, without content
    private fun parseDirectoryStructure(json: JSONObject): SourceCodeModel {
        val name = json.optString("name", "unknown")
        val path = json.optString("path", "/unknown") 
        val isDirectory = json.optBoolean("isDirectory", false)
        
        Log.d(TAG, "Parsing: $name, path: $path, isDirectory: $isDirectory")
        
        val children = if (json.has("children")) {
            val childrenArray = json.getJSONArray("children")
            val childList = mutableListOf<SourceCodeModel>()
            
            Log.d(TAG, "Children count: ${childrenArray.length()}")
            
            for (i in 0 until childrenArray.length()) {
                try {
                    val childObj = childrenArray.getJSONObject(i)
                    childList.add(parseDirectoryStructure(childObj))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing child at index $i", e)
                }
            }
            
            childList
        } else null
        
        return SourceCodeModel(
            name = name,
            path = path,
            isDirectory = isDirectory,
            content = if (json.has("content")) json.optString("content") else null,  // Optionally load content
            children = children
        )
    }
    
    fun loadFileContent(context: Context, filePath: String): String? {
        // Check cache first
        contentCache[filePath]?.get()?.let { return it }
        
        Log.d(TAG, "Loading content for file: $filePath")
        
        try {
            // Try to load from the JSON file directly
            val jsonString = context.assets.open("source_code.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONTokener(jsonString).nextValue() as JSONObject
            
            // Navigate through the JSON structure to find the file
            val content = findFileContentInJson(jsonObject, filePath)
            
            // Cache the result
            content?.let { contentCache[filePath] = WeakReference(it) }
            
            return content
        } catch (e: Exception) {
            Log.e(TAG, "Error loading file content for $filePath", e)
            return "Error loading content: ${e.message}"
        }
    }
    
    private fun findFileContentInJson(json: JSONObject, targetPath: String): String? {
        // Check if this is the file we're looking for
        if (!json.optBoolean("isDirectory", true) && json.optString("path") == targetPath) {
            return json.optString("content")
        }
        
        // Check children if this is a directory
        if (json.has("children")) {
            val children = json.getJSONArray("children")
            for (i in 0 until children.length()) {
                val child = children.getJSONObject(i)
                val content = findFileContentInJson(child, targetPath)
                if (content != null) {
                    return content
                }
            }
        }
        
        return null
    }

    private fun modelToJson(model: SourceCodeModel): JSONObject {
        return JSONObject().apply {
            put("name", model.name)
            put("path", model.path)
            put("isDirectory", model.isDirectory)
            model.content?.let { put("content", it) }
            model.children?.let { children ->
                put("children", JSONArray().apply {
                    children.forEach { child ->
                        put(modelToJson(child))
                    }
                })
            }
        }
    }
} 