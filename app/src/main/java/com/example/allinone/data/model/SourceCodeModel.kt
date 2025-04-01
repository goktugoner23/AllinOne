package com.example.allinone.data.model

data class SourceCodeModel(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val content: String? = null,
    val children: List<SourceCodeModel>? = null
) 