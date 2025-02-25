# AllInOne App

A comprehensive personal management app that uses Firebase for data storage.

## Features

- Transaction tracking
- Investment management
- Note taking with rich text
- Wing Tzun student management
- Firebase integration for cloud storage
- Robust offline support with operation queuing
- Local backup and restore functionality

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

## Building the App

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 17 or later
- Gradle 7.0 or later

### Steps

1. Clone the repository
2. Open the project in Android Studio
3. Make sure you have the `google-services.json` file in the `app/` directory
4. Build and run the app

## Data Structure

The app uses the following collections in Firestore:

- `transactions`: Financial transactions
- `investments`: Investment records
- `notes`: Text notes with optional images
- `students`: Wing Tzun student records

## Offline Support

The app has robust offline support:

1. **Local Caching**: All data is cached locally for offline access
2. **Operation Queuing**: Changes made offline are queued and synchronized when the network is available
3. **Status Indicators**: The app shows the number of pending operations
4. **Error Handling**: Users are informed about network status and operation results

### How Offline Support Works

1. When you make changes while offline, they are:

   - Applied immediately to the local cache
   - Added to a persistent queue
   - Synchronized with Firebase when the network becomes available

2. The queue is stored in the device's SharedPreferences and persists across app restarts

## Backup and Restore

The app includes a backup and restore system to protect your data:

1. **Local Backups**: Create ZIP backups of all your data stored on your device
2. **Scheduled Backups**: Option to automatically create backups on a regular schedule
3. **Restore from Backup**: Easily restore your data from any backup file
4. **Backup Management**: View, share, and delete backup files

### How Backups Work

1. Backups are stored as ZIP files containing JSON representations of your data
2. Each backup is timestamped for easy identification
3. Backups are stored in the app's external files directory
4. Backups can be shared via email, cloud storage, or any sharing method

## Security

The app uses device-specific IDs to ensure data privacy. Each device can only access its own data.

### Firestore Security Rules

The app includes security rules that:

- Restrict access to data based on the device ID
- Prevent unauthorized modifications
- Limit query sizes to maintain performance

### Storage Security Rules

The app includes storage rules that:

- Allow read access to files with the correct URL
- Restrict file uploads to specific types and sizes
- Prevent unauthorized deletions

## License

This project is licensed under the MIT License - see the LICENSE file for details.
