# AllInOne App Architecture - Component Relationships

## Main Application Components

### AllinOneApplication

- **Purpose**: Main control center of the entire app
- **Properties**:
  - `onCreate()`
  - `getFirebaseRepository() FirebaseRepository`
  - `getCacheManager() CacheManager`
  - `getNetworkUtils() NetworkUtils`
  - `getFirebaseIdManager() FirebaseIdManager`
  - `getFirebaseStorageUtil() FirebaseStorageUtil`
  - `getDataChangeNotifier() DataChangeNotifier`

### MainActivity

- **Purpose**: Main lobby of a building - where users start and navigate
- **Properties**:
  - `onCreate()`
  - `setupNavigation()`
  - `handleFragmentNavigation()`
  - `setupBottomNavigation()`
  - `handleBackPress()`
  - `showNetworkStatus()`
  - `setupWorkers()`

## Core Data Models

### Transaction

- **Purpose**: Records of money coming in and going out
- **Properties**:
  - `Long id`
  - `Double amount`
  - `String type`
  - `String description`
  - `Boolean isIncome`
  - `Date date`
  - `String category`
  - `Long? relatedRegistrationId`

### Investment

- **Purpose**: Records of money invested in various opportunities
- **Properties**:
  - `Long id`
  - `String name`
  - `Double amount`
  - `String type`
  - `String? description`
  - `String? imageUri`
  - `Date date`
  - `Boolean isPast`
  - `Double profitLoss`
  - `Double currentValue`

### Note

- **Purpose**: Digital notes and documents
- **Properties**:
  - `Long id`
  - `String title`
  - `String content`
  - `Date date`
  - `String? imageUris`
  - `String? videoUris`
  - `String? voiceNoteUris`
  - `Date lastEdited`
  - `Boolean isRichText`

### WTStudent

- **Purpose**: Student information for Wing Tzun martial arts school
- **Properties**:
  - `Long id`
  - `String name`
  - `String phoneNumber`
  - `String? email`
  - `String? instagram`
  - `Boolean isActive`
  - `String? profileImageUri`
  - `Date? startDate`
  - `Date? endDate`
  - `Double amount`
  - `Boolean isPaid`
  - `Date? paymentDate`
  - `String? attachmentUri`
  - `String? deviceId`
  - `String? notes`
  - `String? photoUri`

### WTRegistration

- **Purpose**: Course registration records for students
- **Properties**:
  - `Long id`
  - `Long studentId`
  - `Double amount`
  - `String? attachmentUri`
  - `Date? startDate`
  - `Date? endDate`
  - `Date paymentDate`
  - `String? notes`
  - `Boolean isPaid`

### WTLesson

- **Purpose**: Martial arts lesson schedules
- **Properties**:
  - `Long id`
  - `String title`
  - `String description`
  - `Date date`
  - `String type`
  - `String? location`
  - `String? instructor`

### WTSeminar

- **Purpose**: Special martial arts events and seminars
- **Properties**:
  - `Long id`
  - `String title`
  - `String description`
  - `Date date`
  - `String location`
  - `String instructor`
  - `Double price`
  - `Int maxParticipants`
  - `List<String> participants`
  - `Boolean isActive`

### Task

- **Purpose**: Personal and business tasks
- **Properties**:
  - `Long id`
  - `String name`
  - `String? description`
  - `Boolean completed`
  - `Date date`
  - `Date? dueDate`
  - `Long? groupId`

### TaskGroup

- **Purpose**: Categories for organizing tasks
- **Properties**:
  - `Long id`
  - `String title`
  - `String? description`
  - `String color`
  - `Date createdAt`
  - `Boolean isCompleted`

### Event

- **Purpose**: Calendar events and appointments
- **Properties**:
  - `Long id`
  - `String title`
  - `String? description`
  - `Date date`
  - `Date? endDate`
  - `String type`

### HistoryItem

- **Purpose**: Historical records of all activities
- **Properties**:
  - `Long id`
  - `String title`
  - `String description`
  - `Date date`
  - `Double amount`
  - `String type`
  - `String? imageUri`
  - `ItemType itemType`

### Program

- **Purpose**: Workout exercise programs
- **Properties**:
  - `Long id`
  - `String name`
  - `String? description`
  - `List<ProgramExercise> exercises`
  - `Date createdDate`
  - `Date lastModifiedDate`

### Workout

- **Purpose**: Individual workout sessions
- **Properties**:
  - `Long id`
  - `Long? programId`
  - `String? programName`
  - `Date startTime`
  - `Date? endTime`
  - `Long duration`
  - `List<WorkoutExercise> exercises`
  - `String? notes`

### Exercise

- **Purpose**: Individual exercises in workout programs
- **Properties**:
  - `Long id`
  - `String name`
  - `String? muscleGroup`
  - `String? description`
  - `String? instructions`

### VoiceNote

- **Purpose**: Audio recordings and voice memos
- **Properties**:
  - `Long id`
  - `String filePath`
  - `Long duration`
  - `Date createdAt`
  - `String? noteId`

## Firebase Layer - The Cloud Storage and Sync System

### FirebaseRepository

- **Purpose**: The main data manager that handles all information
- **Properties**:
  - `StateFlow<List<Transaction>> transactions`
  - `StateFlow<List<Investment>> investments`
  - `StateFlow<List<Note>> notes`
  - `StateFlow<List<Task>> tasks`
  - `StateFlow<List<TaskGroup>> taskGroups`
  - `StateFlow<List<WTStudent>> students`
  - `StateFlow<List<Event>> events`
  - `StateFlow<List<WTLesson>> wtLessons`
  - `StateFlow<List<WTRegistration>> registrations`
  - `StateFlow<List<Program>> programs`
  - `StateFlow<List<Workout>> workouts`
  - `LiveData<Boolean> isNetworkAvailable`
  - `LiveData<String?> errorMessage`
  - `LiveData<Boolean> isLoading`
  - `suspend refreshAllData()`
  - `suspend insertTransaction(Transaction)`
  - `suspend updateTransaction(Transaction)`
  - `suspend deleteTransaction(Transaction)`
  - `suspend insertInvestment(Investment)`
  - `suspend updateInvestment(Investment)`
  - `suspend deleteInvestment(Investment)`
  - `suspend insertNote(Note)`
  - `suspend updateNote(Note)`
  - `suspend deleteNote(Note)`
  - `suspend insertTask(Task)`
  - `suspend updateTask(Task)`
  - `suspend deleteTask(Task)`
  - `suspend insertTaskGroup(TaskGroup)`
  - `suspend updateTaskGroup(TaskGroup)`
  - `suspend deleteTaskGroup(TaskGroup)`
  - `suspend insertStudent(WTStudent)`
  - `suspend updateStudent(WTStudent) Boolean`
  - `suspend deleteStudent(WTStudent)`
  - `suspend insertRegistration(WTRegistration)`
  - `suspend updateRegistration(WTRegistration)`
  - `suspend deleteRegistration(WTRegistration)`
  - `suspend saveProgram(Program) Long`
  - `suspend saveWorkout(Workout) Long`
  - `suspend uploadFile(Uri, String, String?) String?`
  - `suspend deleteFile(String) Boolean`
  - `suspend getNextId(String) Long`

### FirebaseManager

- **Purpose**: The technical interface to cloud storage
- **Properties**:
  - `FirebaseFirestore firestore`
  - `FirebaseStorage storage`
  - `FirebaseIdManager idManager`
  - `String deviceId`
  - `suspend saveTransaction(Transaction) Boolean`
  - `suspend getTransactions() List<Transaction>`
  - `suspend deleteTransaction(Transaction)`
  - `suspend saveInvestment(Investment) Boolean`
  - `suspend getInvestments() List<Investment>`
  - `suspend deleteInvestment(Investment)`
  - `suspend saveNote(Note)`
  - `suspend getNotes() List<Note>`
  - `suspend deleteNote(Note)`
  - `suspend saveTask(Task)`
  - `suspend getTasks() List<Task>`
  - `suspend deleteTask(Task)`
  - `suspend saveTaskGroup(TaskGroup)`
  - `suspend getTaskGroups() List<TaskGroup>`
  - `suspend deleteTaskGroup(TaskGroup)`
  - `suspend saveStudent(WTStudent) Boolean`
  - `suspend getStudents() List<WTStudent>`
  - `suspend deleteStudent(WTStudent)`
  - `suspend saveRegistration(WTRegistration) Task<Void>`
  - `suspend getRegistrations() List<WTRegistration>`
  - `suspend deleteRegistration(Long) Task<Void>`
  - `suspend saveProgram(Program) Long`
  - `suspend getPrograms() List<Program>`
  - `suspend deleteProgram(Long)`
  - `suspend saveWorkout(Workout) Long`
  - `suspend getWorkouts() List<Workout>`
  - `suspend deleteWorkout(Long)`
  - `suspend uploadImage(Uri) String?`
  - `suspend uploadAttachment(Uri) String?`
  - `suspend deleteAllData()`

### FirebaseIdManager

- **Purpose**: Creates unique identifiers for all data items
- **Properties**:
  - `Map<String, Long> counters`
  - `suspend getNextId(String) Long`
  - `suspend initializeCounters()`
  - `suspend updateCounter(String, Long)`

### FirebaseStorageUtil

- **Purpose**: Manages file uploads and downloads
- **Properties**:
  - `FirebaseStorage storage`
  - `suspend uploadFile(Uri, String, String?) String?`
  - `suspend deleteFile(String) Boolean`
  - `suspend deleteFolder(String, String) Boolean`
  - `suspend getFileUrl(String) String?`

### DataChangeNotifier

- **Purpose**: Notifies different parts of the app when data changes
- **Properties**:
  - `MutableLiveData<Boolean> transactionsChanged`
  - `MutableLiveData<Boolean> investmentsChanged`
  - `MutableLiveData<Boolean> notesChanged`
  - `MutableLiveData<Boolean> tasksChanged`
  - `MutableLiveData<Boolean> taskGroupsChanged`
  - `MutableLiveData<Boolean> studentsChanged`
  - `MutableLiveData<Boolean> eventsChanged`
  - `MutableLiveData<Boolean> lessonsChanged`
  - `MutableLiveData<Boolean> registrationsChanged`
  - `MutableLiveData<Boolean> programsChanged`
  - `MutableLiveData<Boolean> workoutsChanged`
  - `static notifyTransactionsChanged()`
  - `static notifyInvestmentsChanged()`
  - `static notifyNotesChanged()`
  - `static notifyTasksChanged()`
  - `static notifyTaskGroupsChanged()`
  - `static notifyStudentsChanged()`
  - `static notifyEventsChanged()`
  - `static notifyLessonsChanged()`
  - `static notifyRegistrationsChanged()`
  - `static notifyProgramsChanged()`
  - `static notifyWorkoutsChanged()`

### OfflineQueue

- **Purpose**: Stores actions when internet is unavailable
- **Properties**:
  - `enum DataType`
  - `enum Operation`
  - `data class QueueItem`
  - `suspend enqueue(DataType, Operation, String)`
  - `suspend processQueue()`
  - `suspend getQueue() List<QueueItem>`
  - `suspend clearQueue()`

## Cache Layer - Local Storage for Faster Access

### CacheManager

- **Purpose**: Stores frequently used data locally for quick access
- **Properties**:
  - `SharedPreferences preferences`
  - `suspend cacheTransactions(List<Transaction>)`
  - `suspend getCachedTransactions() List<Transaction>`
  - `suspend cacheInvestments(List<Investment>)`
  - `suspend getCachedInvestments() List<Investment>`
  - `suspend cacheNotes(List<Note>)`
  - `suspend getCachedNotes() List<Note>`
  - `suspend cacheTasks(List<Task>)`
  - `suspend getCachedTasks() List<Task>`
  - `suspend cacheTaskGroups(List<TaskGroup>)`
  - `suspend getCachedTaskGroups() List<TaskGroup>`
  - `suspend cacheStudents(List<WTStudent>)`
  - `suspend getCachedStudents() List<WTStudent>`
  - `suspend cacheEvents(List<Event>)`
  - `suspend getCachedEvents() List<Event>`
  - `suspend cacheLessons(List<WTLesson>)`
  - `suspend getCachedLessons() List<WTLesson>`
  - `suspend cacheRegistrations(List<WTRegistration>)`
  - `suspend getCachedRegistrations() List<WTRegistration>`
  - `suspend cachePrograms(List<Program>)`
  - `suspend getCachedPrograms() List<Program>`
  - `suspend cacheWorkouts(List<Workout>)`
  - `suspend getCachedWorkouts() List<Workout>`
  - `suspend clearAllCache()`
  - `suspend clearCache(String)`

## Utility Classes - Helper Functions

### NetworkUtils

- **Purpose**: Monitors internet connectivity
- **Properties**:
  - `ConnectivityManager connectivityManager`
  - `LiveData<Boolean> isNetworkAvailable`
  - `LiveData<Boolean> isOnline`
  - `Boolean isActiveNetworkConnected()`
  - `suspend checkNetworkConnectivity()`
  - `suspend waitForNetworkConnection()`

### BackupHelper

- **Purpose**: Manages data backup and restore
- **Properties**:
  - `suspend exportData(Context, Uri) Boolean`
  - `suspend importData(Context, Uri) Boolean`
  - `suspend validateBackupFile(Uri) String?`
  - `suspend createBackupFile() File?`
  - `suspend restoreFromBackup(List<Transaction>, List<Investment>, List<Note>, List<WTStudent>)`

### LogcatHelper

- **Purpose**: Records app activity for troubleshooting
- **Properties**:
  - `suspend captureLogcat() String`
  - `suspend saveLogcatToFile(String) Boolean`
  - `suspend getLogcatFile() File?`
  - `suspend clearLogcatFile()`
  - `suspend getLogcatContent() String`

### ApiKeyManager

- **Purpose**: Securely stores API keys and credentials
- **Properties**:
  - `suspend saveApiKey(String, String)`
  - `suspend getApiKey(String) String?`
  - `suspend deleteApiKey(String)`
  - `suspend getAllApiKeys() Map<String, String>`
  - `Boolean isApiKeyValid(String)`

### TradingUtils

- **Purpose**: Calculates trading and investment metrics
- **Properties**:
  - `suspend calculateProfitLoss(Investment) Double`
  - `suspend calculateTotalPortfolioValue() Double`
  - `suspend getInvestmentSummary() Map<String, Double>`
  - `suspend validateTradeParameters(Double, String) Boolean`
  - `suspend formatCurrency(Double) String`

## ViewModels - Business Logic Controllers

### HomeViewModel

- **Purpose**: Manages the home screen data and calculations
- **Properties**:
  - `LiveData<List<Transaction>> recentTransactions`
  - `LiveData<Double> totalIncome`
  - `LiveData<Double> totalExpense`
  - `LiveData<Double> balance`
  - `LiveData<List<CategorySummary>> categorySpending`
  - `suspend refreshData()`
  - `suspend getTransactionSummary()`
  - `suspend getCategorySpending()`

### InvestmentsViewModel

- **Purpose**: Manages investment portfolio and data
- **Properties**:
  - `LiveData<List<Investment>> investments`
  - `LiveData<Double> totalValue`
  - `LiveData<Double> totalProfitLoss`
  - `LiveData<List<Investment>> pastInvestments`
  - `LiveData<List<Investment>> activeInvestments`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshInvestments()`
  - `suspend addInvestment(Investment)`
  - `suspend updateInvestment(Investment)`
  - `suspend deleteInvestment(Investment)`
  - `suspend calculatePortfolioMetrics()`
  - `suspend getInvestmentById(Long) Investment?`

### NotesViewModel

- **Purpose**: Manages note-taking functionality
- **Properties**:
  - `LiveData<List<Note>> notes`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshNotes()`
  - `suspend addNote(Note)`
  - `suspend updateNote(Note)`
  - `suspend deleteNote(Note)`
  - `suspend searchNotes(String) List<Note>`

### TasksViewModel

- **Purpose**: Manages task and project organization
- **Properties**:
  - `LiveData<List<Task>> tasks`
  - `LiveData<List<TaskGroup>> taskGroups`
  - `LiveData<List<Task>> completedTasks`
  - `LiveData<List<Task>> pendingTasks`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshTasks()`
  - `suspend addTask(Task)`
  - `suspend updateTask(Task)`
  - `suspend deleteTask(Task)`
  - `suspend addTaskGroup(TaskGroup)`
  - `suspend updateTaskGroup(TaskGroup)`
  - `suspend deleteTaskGroup(TaskGroup)`
  - `suspend getTasksByGroup(Long) List<Task>`

### WTRegisterViewModel

- **Purpose**: Manages Wing Tzun school operations
- **Properties**:
  - `LiveData<List<WTStudent>> allStudents`
  - `LiveData<List<WTStudent>> activeStudents`
  - `LiveData<List<WTStudent>> unpaidStudents`
  - `LiveData<List<WTStudent>> paidStudents`
  - `LiveData<List<WTStudent>> registeredStudents`
  - `LiveData<List<WTLesson>> lessonSchedule`
  - `LiveData<Boolean> isNetworkAvailable`
  - `LiveData<String?> errorMessage`
  - `suspend refreshData()`
  - `suspend addStudent(WTStudent)`
  - `suspend updateStudent(WTStudent)`
  - `suspend deleteStudent(WTStudent)`
  - `suspend registerStudentForCourse(WTStudent, Date, Date, Double, Boolean)`
  - `suspend markAsPaid(WTStudent)`
  - `suspend markAsUnpaid(WTStudent)`
  - `suspend calculateEndDateBasedOnLessons(Date) Date?`

### WTLessonsViewModel

- **Purpose**: Manages martial arts lesson scheduling
- **Properties**:
  - `LiveData<List<WTLesson>> lessons`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshLessons()`
  - `suspend addLesson(WTLesson)`
  - `suspend updateLesson(WTLesson)`
  - `suspend deleteLesson(WTLesson)`
  - `suspend getLessonsByDate(Date) List<WTLesson>`
  - `suspend getLessonsByType(String) List<WTLesson>`

### WTSeminarsViewModel

- **Purpose**: Manages special martial arts events
- **Properties**:
  - `LiveData<List<WTSeminar>> seminars`
  - `LiveData<List<WTSeminar>> activeSeminars`
  - `LiveData<List<WTSeminar>> pastSeminars`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshSeminars()`
  - `suspend addSeminar(WTSeminar)`
  - `suspend updateSeminar(WTSeminar)`
  - `suspend deleteSeminar(WTSeminar)`
  - `suspend registerParticipant(Long, String)`
  - `suspend unregisterParticipant(Long, String)`

### CalendarViewModel

- **Purpose**: Manages calendar and scheduling
- **Properties**:
  - `LiveData<List<Event>> events`
  - `LiveData<List<WTLesson>> lessons`
  - `LiveData<List<WTSeminar>> seminars`
  - `LiveData<Date> selectedDate`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshEvents()`
  - `suspend addEvent(Event)`
  - `suspend updateEvent(Event)`
  - `suspend deleteEvent(Event)`
  - `suspend getEventsByDate(Date) List<Event>`
  - `suspend getLessonsByDate(Date) List<WTLesson>`
  - `suspend getSeminarsByDate(Date) List<WTSeminar>`

### HistoryViewModel

- **Purpose**: Manages historical data and records
- **Properties**:
  - `LiveData<List<HistoryItem>> historyItems`
  - `LiveData<List<HistoryItem>> filteredItems`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshHistory()`
  - `suspend filterByType(String)`
  - `suspend filterByDateRange(Date, Date)`
  - `suspend filterByAmountRange(Double, Double)`
  - `suspend clearFilters()`
  - `suspend getHistorySummary() Map<String, Double>`

### WorkoutViewModel

- **Purpose**: Manages fitness and workout tracking
- **Properties**:
  - `LiveData<List<Program>> programs`
  - `LiveData<List<Workout>> workouts`
  - `LiveData<List<Exercise>> exercises`
  - `LiveData<Program?> currentProgram`
  - `LiveData<Workout?> activeWorkout`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshPrograms()`
  - `suspend refreshWorkouts()`
  - `suspend addProgram(Program)`
  - `suspend updateProgram(Program)`
  - `suspend deleteProgram(Program)`
  - `suspend startWorkout(Program)`
  - `suspend endWorkout(Workout)`
  - `suspend saveWorkout(Workout)`
  - `suspend getWorkoutStats() Map<String, Any>`

### LogErrorViewModel

- **Purpose**: Manages error logging and troubleshooting
- **Properties**:
  - `LiveData<List<String>> logEntries`
  - `LiveData<Boolean> isLoading`
  - `LiveData<String> errorMessage`
  - `suspend refreshLogs()`
  - `suspend captureLogcat()`
  - `suspend saveLogsToFile()`
  - `suspend clearLogs()`
  - `suspend getLogFile() File?`

## Component Relationships

### Main Application Relationships

```
AllinOneApplication --> FirebaseRepository
AllinOneApplication --> CacheManager
AllinOneApplication --> NetworkUtils
AllinOneApplication --> FirebaseIdManager
AllinOneApplication --> FirebaseStorageUtil
AllinOneApplication --> DataChangeNotifier

MainActivity --> HomeFragment
MainActivity --> InvestmentsFragment
MainActivity --> NotesFragment
MainActivity --> TasksFragment
MainActivity --> HistoryFragment
MainActivity --> CalendarFragment
MainActivity --> WTRegistryFragment
MainActivity --> WorkoutFragment
MainActivity --> InstagramBusinessFragment
MainActivity --> DrawingActivity
MainActivity --> EditNoteActivity
MainActivity --> DatabaseManagementFragment
MainActivity --> LogErrorsFragment
MainActivity --> BackupWorker
MainActivity --> ExpirationNotificationWorker
MainActivity --> LogcatCaptureWorker
```

### Fragment Relationships

```
HomeFragment --> TransactionsOverviewFragment
HomeFragment --> HomeViewModel
HomeViewModel --> FirebaseRepository

InvestmentsFragment --> InvestmentsTabFragment
InvestmentsFragment --> FuturesFragment
InvestmentsFragment --> ExternalFuturesFragment
InvestmentsTabFragment --> InvestmentsViewModel
FuturesFragment --> UsdMFuturesFragment
FuturesFragment --> CoinMFuturesFragment
UsdMFuturesFragment --> InvestmentsViewModel
UsdMFuturesFragment --> ExternalBinanceRepository
UsdMFuturesFragment --> BinanceWebSocketClient
CoinMFuturesFragment --> InvestmentsViewModel
CoinMFuturesFragment --> ExternalBinanceRepository
CoinMFuturesFragment --> BinanceWebSocketClient
ExternalFuturesFragment --> ExternalBinanceRepository
InvestmentsViewModel --> FirebaseRepository

NotesFragment --> NotesViewModel
NotesViewModel --> FirebaseRepository
EditNoteActivity --> FirebaseRepository

TasksFragment --> TasksViewModel
TasksViewModel --> FirebaseRepository

HistoryFragment --> HistoryViewModel
HistoryViewModel --> FirebaseRepository

CalendarFragment --> CalendarViewModel
CalendarViewModel --> FirebaseRepository
```

### WT (Wing Tzun) Relationships

```
WTRegistryFragment --> WTStudentsFragment
WTRegistryFragment --> WTRegisterFragment
WTRegistryFragment --> WTRegisterContentFragment
WTRegistryFragment --> WTLessonsFragment
WTRegistryFragment --> WTSeminarsFragment
WTStudentsFragment --> WTRegisterViewModel
WTRegisterFragment --> WTRegisterViewModel
WTRegisterContentFragment --> WTRegisterViewModel
WTLessonsFragment --> WTLessonsViewModel
WTSeminarsFragment --> WTSeminarsViewModel
WTRegisterViewModel --> FirebaseRepository
WTRegisterViewModel --> FirebaseIdManager
WTRegisterViewModel --> FirebaseStorageUtil
WTLessonsViewModel --> FirebaseRepository
WTSeminarsViewModel --> FirebaseRepository
```

### Workout Relationships

```
WorkoutFragment --> WorkoutDashboardFragment
WorkoutFragment --> WorkoutProgramFragment
WorkoutFragment --> WorkoutExerciseFragment
WorkoutFragment --> WorkoutStatsFragment
WorkoutFragment --> WorkoutViewModel
WorkoutViewModel --> FirebaseRepository
WorkoutDashboardFragment --> WorkoutViewModel
WorkoutProgramFragment --> WorkoutViewModel
WorkoutExerciseFragment --> WorkoutViewModel
ActiveWorkoutFragment --> WorkoutViewModel
ActiveWorkoutFragment --> WorkoutFragment
ActiveWorkoutFragment --> FirebaseRepository
```

### Instagram Relationships

```
InstagramBusinessFragment --> InstagramPostsFragment
InstagramBusinessFragment --> InstagramInsightsFragment
InstagramBusinessFragment --> InstagramAskAIFragment
InstagramBusinessFragment --> BaseFragment
InstagramPostsFragment --> InstagramViewModel
InstagramInsightsFragment --> InstagramViewModel
InstagramAskAIFragment --> InstagramViewModel
```

### Firebase Layer Relationships

```
FirebaseRepository --> FirebaseManager
FirebaseRepository --> OfflineQueue
FirebaseRepository --> CacheManager
FirebaseRepository --> NetworkUtils
FirebaseRepository --> FirebaseIdManager
FirebaseRepository --> DataChangeNotifier
FirebaseRepository --> FirebaseStorageUtil

FirebaseManager --> FirebaseIdManager
FirebaseManager --> FirebaseStorageUtil
```

### API Relationships

```
ExternalBinanceRepository --> ExternalBinanceApiClient
ExternalBinanceRepository --> BinanceWebSocketClient
ExternalBinanceRepository --> BackupWorker
ExternalBinanceRepository --> FirebaseRepository
ExternalBinanceApiClient --> BinanceExternalService
ExternalBinanceApiClient --> BinanceWebSocketClient
```

### Utility Relationships

```
OfflineQueue --> CacheManager
CacheManager --> NetworkUtils
LogcatHelper --> BackupHelper
LogErrorsFragment --> LogErrorViewModel
LogErrorViewModel --> LogcatHelper
MainActivity --> BackupWorker
MainActivity --> ExpirationNotificationWorker
MainActivity --> LogcatCaptureWorker
BackupWorker --> FirebaseRepository
ExpirationNotificationWorker --> FirebaseRepository
ExpirationNotificationWorker --> NotificationManager
LogcatCaptureWorker --> LogcatHelper
```

### Data Flow Relationships

```
FirebaseRepository ..> Transaction
FirebaseRepository ..> Investment
FirebaseRepository ..> Note
FirebaseRepository ..> Task
FirebaseRepository ..> TaskGroup
FirebaseRepository ..> WTStudent
FirebaseRepository ..> WTRegistration
FirebaseRepository ..> Event
FirebaseRepository ..> WTLesson
FirebaseRepository ..> Program
FirebaseRepository ..> Workout
```

### Adapter Relationships

```
HomeFragment --> TransactionAdapter
TransactionsOverviewFragment --> TransactionAdapter
TransactionReportFragment --> TransactionReportAdapter
InvestmentsFragment --> InvestmentAdapter
InvestmentsTabFragment --> InvestmentAdapter
UsdMFuturesFragment --> BinanceFuturesAdapter
CoinMFuturesFragment --> BinancePositionAdapter
NotesFragment --> NotesAdapter
TasksFragment --> GroupedTasksAdapter
HistoryFragment --> HistoryAdapter
CalendarFragment --> EventAdapter
WTStudentsFragment --> WTStudentAdapter
WTRegisterFragment --> WTRegistrationAdapter
WTLessonsFragment --> WTEventAdapter
WTSeminarsFragment --> SeminarAdapter
WorkoutProgramFragment --> InvestmentSelectionAdapter
```

### Data Model Relationships

```
WTRegistration --> WTStudent
Workout --> Program
Program --> Exercise
Note --> VoiceNote
Task --> TaskGroup
HistoryItem --> Transaction
HistoryItem --> Investment
HistoryItem --> Note
HistoryItem --> WTStudent
```
