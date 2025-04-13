# AllInOne App

A comprehensive personal management app built with Kotlin for Android that integrates Firebase for cloud storage and synchronization.

## Overview

AllInOne is an Android application designed to centralize various aspects of personal management into a single, cohesive platform. Built with modern Android development practices, it leverages Firebase services for real-time data synchronization while maintaining robust offline capabilities.

## Features

### Transaction Tracking
- **Comprehensive Financial Management**: Track all personal and business expenses with categorization, tags, and custom attributes
- **Multi-currency Support**: Handle transactions in different currencies with automatic conversion based on daily rates
- **Budget Planning**: Set monthly budgets by category with visual progress indicators and alerts
- **Recurring Transactions**: Schedule recurring transactions with flexible frequency options
- **Expense Analytics**: Visual breakdown of spending patterns by category, time period, and tags
- **Receipt Capture**: Attach photos of receipts to transactions for record-keeping
- **Export Capabilities**: Export transaction history to CSV/PDF for accounting purposes
- **Split Transactions**: Divide expenses among multiple categories or accounts

### Investment Management
- **Portfolio Dashboard**: At-a-glance view of your entire investment portfolio
- **Performance Tracking**: Track returns over time with annualized ROI calculations
- **Asset Allocation**: Visual breakdown of investments by type, risk level, and industry
- **Investment History**: Record of all transactions including buys, sells, dividends, and splits
- **Goal Setting**: Set investment targets with progress tracking
- **Market Integration**: Optional connection to market data for real-time valuation (premium feature)
- **Tax Lot Tracking**: Track tax lots for optimized tax planning
- **Performance Charts**: Visual representation of growth using MPAndroidChart library

### Note Taking
- **Rich Text Editor**: Comprehensive formatting including bold, italic, underline, headings, and lists
- **Image Support**: Embed and resize images within notes
- **Drawing Tool**: Create and embed drawings directly in notes with customizable brush size and color picker
- **Organization System**: Organize notes with folders, tags, and color-coding
- **Search Functionality**: Full-text search across all notes with highlighted results
- **Markdown Support**: Write and preview in Markdown format
- **Auto-save**: Continuous saving to prevent data loss
- **Version History**: Track changes to notes over time with restore capabilities
- **Sharing Options**: Share notes as text, HTML, or PDF
- **Templates**: Create and use templates for frequently used note structures

### Drawing Features
- **Interactive Canvas**: Responsive drawing surface with smooth line rendering
- **Circular Color Picker**: Intuitive color wheel with brightness adjustment for precise color selection
- **Adjustable Brush Size**: Customize brush thickness for different drawing styles
- **Gallery Integration**: Save drawings directly to device gallery or embed in notes
- **Background Preservation**: Maintains drawing background when saving to ensure visual consistency
- **Clear Canvas**: One-touch reset to start fresh
- **Real-time Preview**: See changes immediately as you draw
- **Non-destructive Editing**: Add to existing drawings without losing previous work

### Wing Tzun Student Management
- **Student Profiles**: Comprehensive student information including contact details and training history
- **Attendance Tracking**: Record attendance for classes, seminars, and private lessons
- **Rank Progression**: Track advancement through the Wing Tzun system
- **Skill Assessment**: Record and evaluate specific skill proficiencies
- **Payment Tracking**: Monitor student payments and membership status
- **Event Management**: Schedule and manage classes, seminars, and special events
- **Communication Tools**: Send notifications and updates to students or groups
- **Progress Reports**: Generate detailed progress reports for students
- **Certification Management**: Track and issue rank certificates

### Firebase Integration
- **Real-time Synchronization**: Instant data updates across all devices
- **User Authentication**: Secure access with Firebase Authentication
- **Cloud Storage**: Store and retrieve binary data efficiently
- **Offline Capabilities**: Full functionality without internet connection
- **Security Rules**: Granular access control for all data
- **Crash Reporting**: Automatic crash reporting and analysis
- **Analytics Integration**: Track user behavior and feature usage
- **Remote Configuration**: Dynamically update app settings without releases

### Offline Support
- **Seamless Offline/Online Transition**: Continue working without interruption regardless of connectivity
- **Background Synchronization**: Automatically sync data when connection is restored
- **Conflict Resolution**: Smart handling of conflicts between offline and server data
- **Operation Queue Management**: View and manage pending operations
- **Bandwidth Optimization**: Efficient data transfer with compression and delta updates
- **Partial Sync**: Download only required data to save bandwidth and storage
- **Sync Status Indicators**: Clear visual feedback about synchronization status
- **Priority-based Syncing**: Critical data synchronizes first when connection is limited

### Backup and Restore
- **Scheduled Automated Backups**: Configure daily, weekly, or monthly automatic backups
- **Selective Backup Options**: Choose which data types to include in backups
- **Cloud Integration**: Optional backup to Google Drive, Dropbox, or other cloud services
- **Encryption**: AES-256 encryption for all backup files
- **Incremental Backups**: Efficient storage with incremental backup support
- **Version Management**: Maintain multiple backup versions with easy browsing
- **Cross-device Restoration**: Restore data to any device with the app installed
- **Backup Verification**: Automatic integrity checks for backup files
- **Export Formats**: Standard formats (JSON, CSV) for data portability

### UI/UX Design
- **Material Design 3 Components**: Modern UI following latest Material Design guidelines
- **Dynamic Theming**: Adapt colors based on user preference or system theme
- **Responsive Layouts**: Optimal display on phones, tablets, and foldables
- **Gesture Navigation**: Intuitive gesture controls for common actions
- **Accessibility Features**: Full support for screen readers, large text, and contrast settings
- **Night Mode**: Optimized dark theme for low-light environments
- **Customizable Home Screen**: Configurable dashboard with most important information
- **Animations**: Smooth, purposeful animations for enhanced user experience
- **Localization**: Support for multiple languages and regional formats

## Technology Stack

- **Kotlin**: 100% Kotlin codebase with coroutines for asynchronous operations
- **MVVM Architecture**: Clean separation of UI, business logic, and data layers
- **Jetpack Components**:
  - ViewModel & LiveData for reactive UI updates
  - ViewBinding for type-safe view access
  - Navigation Component for fragment management
  - WorkManager for background tasks
  - Paging for efficient large dataset handling
- **Dependency Injection**: Hilt for dependency management
- **Firebase Suite**:
  - Firestore for NoSQL database
  - Firebase Storage for binary data
  - Firebase Authentication for user identity
  - Firebase Crashlytics for error reporting
- **Custom Components**:
  - ColorPickerView for interactive color selection
  - DrawingView for canvas-based drawing functionality
  - KnifeText for rich text editing
- **Third-Party Libraries**:
  - Glide for image loading and caching
  - MPAndroidChart for data visualization
  - Timber for enhanced logging
  - PhotoView for image interaction

## Firebase Setup

### 1. Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" and follow the setup wizard
3. Enable Google Analytics if desired

### 2. Add Your Android App to Firebase

1. In the Firebase Console, click on your project
2. Click the Android icon to add an Android app
3. Enter your package name: `com.example.allinone`
4. Download the `google-services.json` file and place it in the `app/` directory

### 3. Set Up Firebase Services

#### Firestore Database

1. In the Firebase Console, go to "Firestore Database"
2. Click "Create database"
3. Choose "Start in production mode" or "Start in test mode" (for development)
4. Select a location for your database
5. Upload the security rules from `firebase_rules/firestore.rules`

#### Firebase Storage

1. In the Firebase Console, go to "Storage"
2. Click "Get started"
3. Choose "Start in production mode" or "Start in test mode" (for development)
4. Select a location for your storage
5. Upload the security rules from `firebase_rules/storage.rules`

## Project Structure

The project follows a modular structure organized by feature and layer:

```
allinone/
├── adapters/      # RecyclerView and other adapters
├── backup/        # Backup and restore functionality
├── cache/         # Local data caching mechanisms
├── config/        # App configuration settings
├── data/          # Data models and repositories
├── di/            # Dependency injection modules
├── firebase/      # Firebase service integrations
├── glide/         # Custom Glide configurations
├── ui/            # UI components and fragments
├── utils/         # Utility classes and extensions
├── viewmodels/    # ViewModels for UI state management
├── views/         # Custom views
└── workers/       # WorkManager background tasks
```

## Building the App

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 17 or later
- Gradle 7.0 or later
- Internet connection for initial Firebase setup

### Cross-Platform Java Configuration

The project includes an `init.gradle` file that automatically detects your operating system and sets the appropriate Java home path. This enables seamless switching between development environments (Windows, macOS, Linux).

To customize the Java paths for your specific environment:

1. Open the `init.gradle` file in the project root
2. Update the following variables with your specific Java installations:
   ```groovy
   def windowsJavaHome = "C:\\Program Files\\Java\\jdk-17" // Update for Windows
   def macJavaHome = "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" // Update for macOS
   def linuxJavaHome = "/usr/lib/jvm/java-17-openjdk" // Update for Linux
   ```
3. Save the file, and Gradle will automatically use the correct path based on your current OS

The script also includes fallback logic to use the system's default Java installation if the specified path doesn't exist.

### Steps

1. Clone the repository
2. Open the project in Android Studio
3. Make sure you have the `google-services.json` file in the `app/` directory
4. Sync Gradle files
5. Build and run the app

## Data Structure

The app uses the following collections in Firestore:

- `transactions`: Financial transactions with categories, amounts, dates, and notes
- `investments`: Investment records including name, value, returns, and history
- `notes`: Text notes with rich formatting and optional images
- `students`: Wing Tzun student records with attendance and progress tracking
- `counters`: Sequential ID counters for all resources

### Sequential IDs

All resources in the app use sequential numeric IDs rather than random UUIDs. This approach provides several benefits:

1. **User-Friendly**: Sequential IDs are more user-friendly and easier to reference (e.g., "Invoice #125")
2. **More Efficient**: Numeric IDs require less storage space compared to UUIDs
3. **Better Sorting**: Natural ordering for display in lists, reports, and exports
4. **Consistency**: Predictable ID generation across all resource types

#### How Sequential IDs Work

1. The app maintains counter documents in the `counters` collection, one for each resource type
2. When a new resource is created, the app:
   - Reads the current counter value in a transaction
   - Increments the counter
   - Assigns the new value as the resource ID
   - Saves both the counter and the new resource
3. This process ensures ID uniqueness even with concurrent operations and offline usage

#### Counter Documents Structure

Each counter document has this structure:
```json
{
  "count": 125,
  "last_updated": "2023-11-28T14:32:45Z"
}
```

## Offline Support

The app implements a sophisticated offline-first approach:

1. **Local Caching**: All data is cached locally in structured storage for offline access
2. **Operation Queuing**: Changes made offline are serialized, queued, and synchronized when the network is available
3. **Conflict Resolution**: Smart merging strategies when conflicts occur between local and server data
4. **Status Indicators**: The app shows the number of pending operations and sync status
5. **Error Handling**: Comprehensive error handling with user-friendly notifications

### How Offline Support Works

1. When changes are made while offline:
   - They are applied immediately to the local cache
   - Operations are serialized and added to a persistent queue
   - A background service monitors network connectivity
   - Changes are synchronized with Firebase when the network becomes available

2. The operation queue is stored in the device's encrypted SharedPreferences and persists across app restarts

3. A WorkManager periodic task attempts to process the queue at regular intervals

## Backup and Restore

The app includes a comprehensive backup and restore system:

1. **Local Backups**: Create ZIP backups of all your data stored on your device
2. **Scheduled Backups**: Configurable automatic backups on daily, weekly, or monthly schedules
3. **Selective Backups**: Choose which data types to include in backups
4. **Restore from Backup**: Easily restore your data from any backup file with conflict resolution
5. **Backup Management**: View, share, delete, and verify backup files

### How Backups Work

1. Backups are created using a structured approach:
   - Data is exported from local cache and Firebase to JSON format
   - Binary files (images) are included in their original format
   - All data is compressed into a single ZIP file with metadata
   - Encryption is applied to protect sensitive information

2. Each backup contains:
   - Timestamped JSON files for each data collection
   - Binary assets organized by reference
   - Metadata file with version information and contents
   - Checksum verification data

3. Backups can be:
   - Stored in the app's external files directory
   - Shared via email, cloud storage, or any sharing method
   - Password-protected for additional security

## Security

The app implements multiple layers of security:

1. **Device Authentication**: Device-specific IDs ensure data privacy
2. **Data Encryption**: Sensitive local data is encrypted using AndroidX Security library
3. **Network Security**: All communications with Firebase are encrypted using TLS
4. **Firestore Rules**: Comprehensive security rules control access to cloud data

### Firestore Security Rules

The app includes security rules that:

- Restrict access to data based on the device ID
- Prevent unauthorized modifications
- Validate data structure and content
- Limit query sizes to maintain performance
- Implement rate limiting to prevent abuse

### Storage Security Rules

The app includes storage rules that:

- Allow read access to files with the correct URL
- Restrict file uploads to specific types and sizes
- Enforce maximum storage quotas
- Prevent unauthorized deletions
- Validate file metadata

## Performance Optimization

The app is optimized for performance through:

1. **Lazy Loading**: Data is loaded on-demand with paging support
2. **Efficient Caching**: Smart caching strategies reduce network calls
3. **Image Optimization**: Images are resized and compressed before storage
4. **Background Processing**: Heavy operations run in background threads
5. **Memory Management**: Careful resource handling to prevent leaks

## Testing

The project includes comprehensive tests:

1. **Unit Tests**: Tests for business logic and data processing
2. **Integration Tests**: Tests for component interactions
3. **UI Tests**: Espresso tests for user interface functionality
4. **Firebase Emulator Tests**: Tests using local Firebase emulators

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Recent Changes

### Data Model Updates

#### Note Model
- **Removed Single Image Field**: The `imageUri` field has been removed from the `Note` data class to standardize on the plural `imageUris` field for handling multiple images.
- **Backward Compatibility**: The Firebase Manager includes migration code to handle old notes by checking for the `imageUri` field when loading notes from Firestore.
- **Improved Consistency**: All code now consistently uses the `imageUris` field (a comma-separated list) for both single and multiple images.
