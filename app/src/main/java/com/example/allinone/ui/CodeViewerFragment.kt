package com.example.allinone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.allinone.R
import com.example.allinone.utils.SourceCodeUtils
import java.util.regex.Pattern

class CodeViewerFragment : Fragment() {

    private val TAG = "CodeViewerFrag"
    private lateinit var codeTextView: TextView
    private lateinit var fileNameText: TextView
    private lateinit var toolbar: Toolbar
    
    // Pre-compile regex patterns for better performance
    companion object {
        // Regex patterns for code highlighting (compiled once for reuse)
        private val KEYWORD_PATTERN = Pattern.compile(
            "\\b(val|var|fun|class|object|interface|override|private|public|protected|internal|" +
            "return|if|else|when|for|while|do|break|continue|as|is|in|package)\\b"
        )
        private val IMPORT_PATTERN = Pattern.compile("\\b(import)\\b.*")
        private val STRING_PATTERN = Pattern.compile("\".*?\"")
        private val COMMENT_PATTERN = Pattern.compile("//.*|/\\*.*?\\*/", Pattern.DOTALL)
        private val NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b")
        private val XML_TAG_PATTERN = Pattern.compile("</?[a-z][^>]*>")
        private val XML_ATTR_PATTERN = Pattern.compile("\\s[a-z-]+\\s*=")
        
        // Colors
        private val KEYWORD_COLOR = Color.parseColor("#0000FF")  // Blue
        private val IMPORT_COLOR = Color.parseColor("#FF8800")   // Orange
        private val STRING_COLOR = Color.parseColor("#008000")   // Green
        private val COMMENT_COLOR = Color.parseColor("#808080")  // Gray
        private val NUMBER_COLOR = Color.parseColor("#FF0000")   // Red
        private val ATTRIBUTE_COLOR = Color.parseColor("#FF00FF") // Purple
    }
    
    // Use strings for the arguments instead of navArgs since we're having issues with safe args
    private var filePath: String = ""
    private var fileName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get the arguments passed from SourceCodeViewerFragment
        arguments?.let { bundle ->
            filePath = bundle.getString("filePath", "")
            fileName = bundle.getString("fileName", "")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_code_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        codeTextView = view.findViewById(R.id.codeTextView)
        setupToolbar()
        setupCopyOnLongPress()
        loadFileContent()
    }

    private fun setupViews(view: View) {
        codeTextView = view.findViewById(R.id.codeTextView)
    }
    
    private fun setupToolbar() {
        // Just configure the activity's toolbar
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = fileName
            
            // Set the home button as back arrow
            setHomeAsUpIndicator(R.drawable.ic_back)
        }
        
        // Get parent activity's toolbar
        val activityToolbar = (requireActivity() as AppCompatActivity).findViewById<Toolbar>(R.id.toolbar)
        
        // Set up navigation icon click listener for back navigation
        activityToolbar?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupCopyOnLongPress() {
        codeTextView.setOnLongClickListener {
            context?.let { context ->
                val content = SourceCodeUtils.loadFileContent(context, filePath) ?: return@setOnLongClickListener false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Source Code", content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun loadFileContent() {
        // Show loading state
        codeTextView.text = "Loading..."
        
        Log.d(TAG, "Loading content for file: $filePath")
        
        // Optimize TextView for better selection performance
        codeTextView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isLongClickable = true
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = true
            setHorizontallyScrolling(true)
            // Don't use software layer type as it can actually slow down text selection
            // Use hardware acceleration for better performance
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Load file content in background thread
        Thread {
            try {
                // Load content lazily
                val content = context?.let { 
                    Log.d(TAG, "Getting context to load file")
                    SourceCodeUtils.loadFileContent(it, filePath) 
                } ?: ""
                
                Log.d(TAG, "Content loaded, length: ${content.length}")
                
                // Process syntax highlighting in background thread to prevent UI lag
                val highlightedText = if (content.isNotEmpty()) {
                    highlightSyntax(content, fileName)
                } else {
                    SpannableString("[Empty file or content not available]")
                }
                
                // Update UI on main thread
                activity?.runOnUiThread {
                    if (isAdded) {
                        Log.d(TAG, "Updating UI with content")
                        if (content.isNotEmpty()) {
                            // Set the text on main thread, already highlighted
                            codeTextView.setTextKeepState(highlightedText)
                            Log.d(TAG, "Content displayed with highlighting")
                        } else {
                            Log.w(TAG, "Empty content returned")
                            codeTextView.text = "[Empty file or content not available]"
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory error", e)
                activity?.runOnUiThread {
                    if (isAdded) {
                        codeTextView.text = "File too large to display. Try looking at smaller files."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading file", e)
                activity?.runOnUiThread {
                    if (isAdded) {
                        codeTextView.text = "Error loading file: ${e.message}"
                    }
                }
            }
        }.start()
    }
    
    private fun highlightSyntax(code: String, fileName: String): SpannableString {
        // For larger files, we'll limit syntax highlighting to improve performance
        if (code.length > 30000) {
            return SpannableString(code)
        }
        
        val spannableString = SpannableString(code)
        
        // Only apply highlighting for specific file types
        if (!fileName.endsWith(".kt") && !fileName.endsWith(".java") && !fileName.endsWith(".xml")) {
            return spannableString
        }
        
        try {
            // Apply highlighting based on file type
            if (fileName.endsWith(".kt") || fileName.endsWith(".java")) {
                // Highlight imports first (priority over keywords)
                highlightPattern(spannableString, IMPORT_PATTERN, IMPORT_COLOR, true)
                highlightPattern(spannableString, KEYWORD_PATTERN, KEYWORD_COLOR, true)
                highlightPattern(spannableString, STRING_PATTERN, STRING_COLOR, false)
                highlightPattern(spannableString, COMMENT_PATTERN, COMMENT_COLOR, false)
                
                // Only highlight numbers if the file is small enough for performance
                if (code.length < 10000) {
                    highlightPattern(spannableString, NUMBER_PATTERN, NUMBER_COLOR, false)
                }
            } else if (fileName.endsWith(".xml")) {
                highlightPattern(spannableString, XML_TAG_PATTERN, KEYWORD_COLOR, false)
                highlightPattern(spannableString, XML_ATTR_PATTERN, ATTRIBUTE_COLOR, false)
                highlightPattern(spannableString, STRING_PATTERN, STRING_COLOR, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during syntax highlighting", e)
        }
        
        return spannableString
    }
    
    private fun highlightPattern(spannable: SpannableString, pattern: Pattern, color: Int, isBold: Boolean) {
        val matcher = pattern.matcher(spannable)
        while (matcher.find()) {
            spannable.setSpan(
                ForegroundColorSpan(color),
                matcher.start(),
                matcher.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            if (isBold) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    matcher.start(),
                    matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
} 