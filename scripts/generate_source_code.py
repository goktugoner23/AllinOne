#!/usr/bin/env python3

import os
import json
import shutil
import sys
from pathlib import Path

# Maximum file size in bytes to include content directly (100 KB)
MAX_FILE_SIZE = 100 * 1024  

def should_include_file(file_path):
    """Check if the file should be included in the source code viewer."""
    # List of file extensions to include
    include_extensions = {
        '.kt', '.xml', '.gradle', '.properties', '.md', '.txt', '.json',
        '.java', '.py', '.c', '.cpp', '.h', '.hpp', '.js', '.css', '.html'
    }
    
    # List of directories to exclude
    exclude_dirs = {
        'build', '.gradle', '.idea', '.git', 'node_modules', 'dist',
        'generated', 'bin', 'obj', '__pycache__', '.DS_Store'
    }
    
    # List of files to exclude
    exclude_files = {
        'local.properties', 'gradle.properties', 'gradlew', 'gradlew.bat',
        'settings.gradle', 'build.gradle', 'proguard-rules.pro',
        'source_code.json'  # Don't include the generated file itself
    }
    
    # Get file extension and name
    ext = file_path.suffix.lower()
    name = file_path.name
    
    # Check if file should be excluded
    if name in exclude_files:
        return False
    
    # Check if file is in excluded directory
    for part in file_path.parts:
        if part in exclude_dirs:
            return False
    
    # Check if file extension should be included
    return ext in include_extensions

def safe_read_file_content(file_path):
    """Read file content with proper encoding handling and size limits."""
    try:
        # Check file size first
        file_size = file_path.stat().st_size
        if file_size > MAX_FILE_SIZE:
            return f"[File too large to display - {file_size/1024:.1f} KB]"
            
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            # Validate the content can be properly represented in JSON
            # This prevents issues with invalid escape sequences
            json.dumps(content)
            return content
    except UnicodeDecodeError:
        # If UTF-8 fails, try with a different encoding
        try:
            with open(file_path, 'r', encoding='latin-1') as f:
                content = f.read()
                # Validate that the content can be properly represented in JSON
                json.dumps(content)
                return content
        except:
            return f"Error: Could not read file {file_path} with UTF-8 or Latin-1 encoding"
    except json.JSONDecodeError as e:
        # If the content can't be represented in JSON, return a simplified version
        print(f"Warning: File content can't be properly JSON-encoded: {file_path} - {e}")
        return f"[File content contains characters that cannot be properly encoded in JSON]"
    except Exception as e:
        print(f"Error reading file {file_path}: {e}")
        return f"Error: {str(e)}"

def create_source_code_model(directory, relative_path=""):
    """Create a source code model from a directory."""
    children = []
    
    # Sort items to ensure consistent order
    try:
        items = sorted(directory.iterdir())
    except Exception as e:
        print(f"Error reading directory {directory}: {e}")
        return {
            'name': directory.name,
            'path': relative_path or directory.name,
            'isDirectory': True,
            'children': []
        }
    
    for item in items:
        try:
            # Use relative path for all files to ensure proper navigation
            item_relative_path = os.path.join(relative_path, item.name)
            
            if item.is_dir():
                # Recursively process directories
                child_model = create_source_code_model(item, item_relative_path)
                if child_model['children']:  # Only add non-empty directories
                    children.append(child_model)
            elif should_include_file(item):
                # Process files
                content = safe_read_file_content(item)
                children.append({
                    'name': item.name,
                    'path': item_relative_path,
                    'isDirectory': False,
                    'content': content
                })
        except Exception as e:
            print(f"Error processing {item}: {e}")
            # Skip this item and continue with others
    
    return {
        'name': directory.name,
        'path': relative_path or directory.name,
        'isDirectory': True,
        'children': children
    }

def main():
    # Get the project root directory (parent of the scripts directory)
    project_root = Path(__file__).parent.parent
    
    print(f"Generating source code model from: {project_root}")
    
    try:
        # Create source code model
        source_code_model = create_source_code_model(project_root)
        
        # Validate the root has children
        if not source_code_model['children']:
            print("Warning: No source files found! Check your include/exclude patterns.")
        
        # Create assets directory if it doesn't exist
        assets_dir = project_root / 'app' / 'src' / 'main' / 'assets'
        assets_dir.mkdir(parents=True, exist_ok=True)
        
        # Write the JSON file
        output_file = assets_dir / 'source_code.json'
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(source_code_model, f, ensure_ascii=False)
        
        print(f"Source code JSON file generated at: {output_file}")
        print(f"File size limits applied: Files larger than {MAX_FILE_SIZE/1024:.1f} KB will show a placeholder")
        
        # Try parsing the generated file to verify it's valid
        with open(output_file, 'r', encoding='utf-8') as f:
            try:
                json.load(f)
                print("✅ Generated JSON file is valid")
            except json.JSONDecodeError as e:
                print(f"❌ Generated JSON file is NOT valid: {e}")
                print("This may cause issues in the app when loading the file")
        
    except Exception as e:
        print(f"Error generating source code model: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main() 