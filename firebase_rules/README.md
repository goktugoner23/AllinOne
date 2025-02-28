# Firebase Setup Instructions

Follow these steps to properly configure Firebase for this application.

## 1. Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" and follow the steps to create a new project
3. Name your project (e.g., "AllInOne App")

## 2. Register Your Android App

1. In the Firebase console, click on your project
2. Click "Add app" button and select Android
3. Enter your app's package name: `com.example.allinone`
4. Download the `google-services.json` file
5. Replace the existing placeholder `google-services.json` file in your app folder with the downloaded file

## 3. Set Up Firestore Database

1. In the Firebase console, go to "Firestore Database"
2. Click "Create database"
3. Start in production mode
4. Choose a location close to your users
5. Upload these security rules from `firebase_rules/firestore.rules`

## 4. Set Up Firebase Storage

1. In the Firebase console, go to "Storage"
2. Click "Get started"
3. Start in production mode
4. Choose a location close to your users
5. Upload these security rules from `firebase_rules/storage.rules`

## 5. Update API Keys

Ensure that you replace all placeholder values in `google-services.json` with real values from your Firebase project.

## Common Issues

### Permission Denied Errors
If you're seeing "Permission denied on resource project" errors:
1. Make sure you've correctly uploaded the Firestore rules
2. Check that your device ID is properly set in the app
3. Verify that the project ID in `google-services.json` matches your actual Firebase project

### Google Play Services Errors
If you're seeing "Failed to get service from broker" or "Unknown calling package name 'com.google.android.gms'" errors:
1. Make sure Google Play Services is up to date on your device
2. Try clearing Google Play Services cache and data
3. If testing on an emulator, make sure the emulator has Google Play Services installed 