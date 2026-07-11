package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Screen Enumeration
sealed interface Screen {
    object Landing : Screen
    object Dashboard : Screen
    object Geomap : Screen
    object AttendanceVerification : Screen
    object LunchShop : Screen
    object Settings : Screen
}

// Campus Map Location and Waypoint data structures
data class MapLocation(
    val name: String,
    val description: String,
    val classroom: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val xRatio: Float,
    val yRatio: Float,
    val latitude: Double,
    val longitude: Double
)

data class MapWaypoint(
    val xRatio: Float,
    val yRatio: Float,
    val direction: String
)

fun getWaypointsForLocation(locationName: String): List<MapWaypoint> {
    val startX = 0.25f
    val startY = 0.75f
    
    return when {
        locationName.contains("Cafeteria") || locationName.contains("Cafe") || locationName.contains("LNC101") -> listOf(
            MapWaypoint(startX, startY, "Start at Central Gate Plaza, head North-East towards Main Street (60m)"),
            MapWaypoint(0.35f, 0.65f, "Pass by the Student Union and turn right at the Central Fountain (50m)"),
            MapWaypoint(0.45f, 0.55f, "You have arrived at the Main Cafeteria & Coffee Bar! Enjoy your break.")
        )
        locationName.contains("Tech Hall") || locationName.contains("CS402") -> listOf(
            MapWaypoint(startX, startY, "Head North from Plaza past the Science lawn (80m)"),
            MapWaypoint(0.45f, 0.55f, "Continue straight, passing the Main Cafeteria on your right (60m)"),
            MapWaypoint(0.60f, 0.40f, "Turn right at the Tech Hall courtyard entrance (50m)"),
            MapWaypoint(0.70f, 0.30f, "Enter Tech Hall. Take stairs to Lab 202. You have arrived!")
        )
        locationName.contains("Science Building") || locationName.contains("AI510") -> listOf(
            MapWaypoint(startX, startY, "Head North-West towards the Humanities wing (50m)"),
            MapWaypoint(0.22f, 0.50f, "Continue straight along the Humanities corridor (70m)"),
            MapWaypoint(0.30f, 0.25f, "Turn right into Science Building. Auditorium A is straight ahead.")
        )
        locationName.contains("Library") || locationName.contains("LIB301") -> listOf(
            MapWaypoint(startX, startY, "Walk East from the Plaza along the Library avenue (60m)"),
            MapWaypoint(0.45f, 0.70f, "Continue past the Fountain, turning slightly right (50m)"),
            MapWaypoint(0.60f, 0.75f, "Arrive at Varsity Library entrance. Quiet Zone is on Level 1.")
        )
        locationName.contains("Sports Gym") || locationName.contains("PED201") -> listOf(
            MapWaypoint(startX, startY, "Walk East past the Central Fountain (90m)"),
            MapWaypoint(0.60f, 0.75f, "Walk past the Varsity Library on your left towards the Arena (60m)"),
            MapWaypoint(0.80f, 0.60f, "Arrive at Sports Gym Fitness Arena. Workout zone is to your left.")
        )
        locationName.contains("Humanities") || locationName.contains("HUM102") -> listOf(
            MapWaypoint(startX, startY, "Head West towards the West Quad (50m)"),
            MapWaypoint(0.15f, 0.60f, "Turn right at the Arts pillar and head North (40m)"),
            MapWaypoint(0.20f, 0.45f, "Arrive at Humanities Block - Seminar 5 on the ground floor.")
        )
        else -> listOf( // Engineering Room 102 (CS312)
            MapWaypoint(startX, startY, "Head North-East past the Central Lawn (100m)"),
            MapWaypoint(0.60f, 0.50f, "Turn left towards the Engineering courtyard (50m)"),
            MapWaypoint(0.85f, 0.35f, "Arrive at Engineering Room 102. Practical lab is on your right.")
        )
    }
}

// Food / Lunch item
data class ShopItem(
    val id: String,
    val name: String,
    val costPoints: Int,
    val localValue: String,
    val description: String,
    val category: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: UniPointsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "unipoints_database"
        ).fallbackToDestructiveMigration().build()

        repository = UniPointsRepository(database.dao())

        setContent {
            MyApplicationTheme {
                val vm: UniPointsViewModel = viewModel(
                    factory = UniPointsViewModelFactory(repository)
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UniPointsApp(vm)
                }
            }
        }
    }
}

class UniPointsViewModelFactory(private val repository: UniPointsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UniPointsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UniPointsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class UniPointsViewModel(val repository: UniPointsRepository) : ViewModel() {
    val studentProfile: StateFlow<StudentProfile?> = repository.studentProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allClasses: StateFlow<List<UniversityClass>> = repository.allClasses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allVouchers: StateFlow<List<RedeemedVoucher>> = repository.allVouchers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    var currentScreen by mutableStateOf<Screen>(Screen.Landing)
    var selectedClassForMap by mutableStateOf<UniversityClass?>(null)

    val campusLocations = listOf(
        MapLocation(
            name = "Main Cafeteria & Coffee Bar",
            description = "Enjoy campus meals, snacks, and South African coffee",
            classroom = "Main Cafeteria - Dining & Coffee Bar",
            icon = Icons.Default.LocalCafe,
            xRatio = 0.45f,
            yRatio = 0.55f,
            latitude = 37.7740,
            longitude = -122.4190
        ),
        MapLocation(
            name = "Tech Hall - Lab 202",
            description = "Mobile development, Kotlin lab, and computer science classes",
            classroom = "Tech Hall - Lab 202",
            icon = Icons.Default.Computer,
            xRatio = 0.70f,
            yRatio = 0.30f,
            latitude = 37.7749,
            longitude = -122.4194
        ),
        MapLocation(
            name = "Science Building - Aud A",
            description = "Auditorium for AI classes and neural network seminars",
            classroom = "Science Building - Auditorium A",
            icon = Icons.Default.Science,
            xRatio = 0.30f,
            yRatio = 0.25f,
            latitude = 37.7758,
            longitude = -122.4210
        ),
        MapLocation(
            name = "Varsity Library - Quiet Zone",
            description = "Academic quiet study zone, research databases, and reading",
            classroom = "Varsity Library - Quiet Zone",
            icon = Icons.Default.MenuBook,
            xRatio = 0.60f,
            yRatio = 0.75f,
            latitude = 37.7750,
            longitude = -122.4200
        ),
        MapLocation(
            name = "Sports Gym - Fitness Arena",
            description = "Cardio, weight training, fitness tests, and recreation",
            classroom = "Sports Gym - Fitness Arena",
            icon = Icons.Default.FitnessCenter,
            xRatio = 0.80f,
            yRatio = 0.60f,
            latitude = 37.7760,
            longitude = -122.4220
        ),
        MapLocation(
            name = "Humanities Block - Seminar 5",
            description = "Seminar venue for languages, arts, and literature classes",
            classroom = "Humanities Block - Seminar 5",
            icon = Icons.Default.School,
            xRatio = 0.20f,
            yRatio = 0.45f,
            latitude = 37.7730,
            longitude = -122.4170
        ),
        MapLocation(
            name = "Engineering - Room 102",
            description = "Database architecture & hardware practicals",
            classroom = "Engineering - Room 102",
            icon = Icons.Default.HomeWork,
            xRatio = 0.85f,
            yRatio = 0.35f,
            latitude = 37.7735,
            longitude = -122.4180
        )
    )

    var selectedMapLocation by mutableStateOf<MapLocation>(campusLocations[0])
    var currentWaypointIndex by mutableStateOf(0)

    // Routing workflow states
    var simulatedDistance by mutableStateOf(180.0) // meters
    var isWalking by mutableStateOf(false)
    var isGeofenceTriggered by mutableStateOf(false)

    // Attendance Verification States
    var arrivalStatusChoice by mutableStateOf("ON_TIME") // "ON_TIME", "LATE", "ABSENT"

    // Shop checkout states
    var selectedShopItem by mutableStateOf<ShopItem?>(null)
    var deviceHasNfc by mutableStateOf(true)
    var activeVoucherToShow by mutableStateOf<RedeemedVoucher?>(null)
    var transactionSuccessMessage by mutableStateOf<String?>(null)

    // Custom UniEarn settings and helpdesk feedback
    var selectedLanguage by mutableStateOf("English")
    val submittedConcerns = mutableStateListOf<Pair<String, String>>()

    init {
        // Automatic login if session exists in Room
        viewModelScope.launch {
            repository.studentProfile.firstOrNull()?.let { profile ->
                if (profile.isLoggedIn) {
                    currentScreen = Screen.Dashboard
                }
            }
        }
    }

    fun handleSignIn(email: String, name: String, points: Int = 350) {
        viewModelScope.launch {
            val studentEmail = email.ifBlank { "MMogomotsi14@gmail.com" }
            val studentName = name.ifBlank { 
                if (studentEmail == "MMogomotsi14@gmail.com") "Mogomotsi"
                else if (studentEmail == "ZamaS@varsity.ac.za") "Zama Shabangu"
                else "New Student"
            }
            
            repository.logoutAllProfiles()
            
            val existingProfile = repository.getProfileByEmail(studentEmail)
            if (existingProfile != null) {
                repository.saveProfile(existingProfile.copy(isLoggedIn = true))
            } else {
                // Determine preloaded stats based on email
                val initialPoints = if (studentEmail == "MMogomotsi14@gmail.com") 350
                                    else if (studentEmail == "ZamaS@varsity.ac.za") 820
                                    else 300 // A new profile has an initial amount of 300 points
                
                val avatarIndexVal = if (studentEmail == "MMogomotsi14@gmail.com") 0
                                     else if (studentEmail == "ZamaS@varsity.ac.za") 1
                                     else 2

                val cs402Att = if (studentEmail == "MMogomotsi14@gmail.com") 8 else if (studentEmail == "ZamaS@varsity.ac.za") 9 else 5
                val cs402Tot = if (studentEmail == "MMogomotsi14@gmail.com") 10 else if (studentEmail == "ZamaS@varsity.ac.za") 10 else 5

                val ai510Att = if (studentEmail == "MMogomotsi14@gmail.com") 7 else if (studentEmail == "ZamaS@varsity.ac.za") 10 else 5
                val ai510Tot = if (studentEmail == "MMogomotsi14@gmail.com") 10 else if (studentEmail == "ZamaS@varsity.ac.za") 10 else 5

                val cs312Att = if (studentEmail == "MMogomotsi14@gmail.com") 9 else if (studentEmail == "ZamaS@varsity.ac.za") 8 else 5
                val cs312Tot = if (studentEmail == "MMogomotsi14@gmail.com") 10 else if (studentEmail == "ZamaS@varsity.ac.za") 10 else 5

                val hum102Att = if (studentEmail == "MMogomotsi14@gmail.com") 6 else if (studentEmail == "ZamaS@varsity.ac.za") 9 else 5
                val hum102Tot = if (studentEmail == "MMogomotsi14@gmail.com") 10 else if (studentEmail == "ZamaS@varsity.ac.za") 10 else 5

                val profile = StudentProfile(
                    email = studentEmail,
                    name = studentName,
                    points = initialPoints,
                    isLoggedIn = true,
                    avatarIndex = avatarIndexVal,
                    cs402Attended = cs402Att,
                    cs402Total = cs402Tot,
                    ai510Attended = ai510Att,
                    ai510Total = ai510Tot,
                    cs312Attended = cs312Att,
                    cs312Total = cs312Tot,
                    hum102Attended = hum102Att,
                    hum102Total = hum102Tot
                )
                repository.saveProfile(profile)
            }

            // Populate classes if table is empty
            val classesFlow = repository.allClasses.firstOrNull() ?: emptyList()
            if (classesFlow.isEmpty()) {
                repository.insertClasses(listOf(
                    UniversityClass(
                        courseCode = "CS402",
                        courseName = "Mobile Application Development",
                        classroom = "Tech Hall - Lab 202",
                        latitude = 37.7749,
                        longitude = -122.4194,
                        startTime = "09:00",
                        endTime = "10:30",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    ),
                    UniversityClass(
                        courseCode = "AI510",
                        courseName = "Neural Networks & AI Models",
                        classroom = "Science Building - Auditorium A",
                        latitude = 37.7758,
                        longitude = -122.4210,
                        startTime = "11:15",
                        endTime = "12:45",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    ),
                    UniversityClass(
                        courseCode = "LNC101",
                        courseName = "Lunch & Coffee Break",
                        classroom = "Main Cafeteria - Dining & Coffee Bar",
                        latitude = 37.7740,
                        longitude = -122.4190,
                        startTime = "13:00",
                        endTime = "14:15",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    ),
                    UniversityClass(
                        courseCode = "CS312",
                        courseName = "Database Architectures",
                        classroom = "Engineering - Room 102",
                        latitude = 37.7735,
                        longitude = -122.4180,
                        startTime = "14:30",
                        endTime = "16:00",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    ),
                    UniversityClass(
                        courseCode = "LIB301",
                        courseName = "Academic Self-Study",
                        classroom = "Varsity Library - Quiet Zone",
                        latitude = 37.7750,
                        longitude = -122.4200,
                        startTime = "16:15",
                        endTime = "17:30",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    ),
                    UniversityClass(
                        courseCode = "PED201",
                        courseName = "Campus Fitness Training",
                        classroom = "Sports Gym - Fitness Arena",
                        latitude = 37.7760,
                        longitude = -122.4220,
                        startTime = "17:45",
                        endTime = "18:45",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    ),
                    UniversityClass(
                        courseCode = "HUM102",
                        courseName = "Modern South African Literature",
                        classroom = "Humanities Block - Seminar 5",
                        latitude = 37.7730,
                        longitude = -122.4170,
                        startTime = "19:00",
                        endTime = "20:00",
                        dayOfWeek = "Today",
                        status = "PENDING"
                    )
                ))
            }
            currentScreen = Screen.Dashboard
        }
    }

    fun selectClassForRouting(uniClass: UniversityClass) {
        selectedClassForMap = uniClass
        val matchingLocation = campusLocations.firstOrNull { it.classroom == uniClass.classroom }
            ?: MapLocation(
                name = uniClass.courseName,
                description = "Classroom venue for ${uniClass.courseName}",
                classroom = uniClass.classroom,
                icon = Icons.Default.LocationOn,
                xRatio = 0.70f,
                yRatio = 0.30f,
                latitude = uniClass.latitude,
                longitude = uniClass.longitude
            )
        selectedMapLocation = matchingLocation
        currentWaypointIndex = 0
        simulatedDistance = 180.0
        isWalking = false
        isGeofenceTriggered = false
        currentScreen = Screen.Dashboard
    }

    fun startWalkingSimulation(onGeofenceReached: () -> Unit) {
        if (isWalking) return
        isWalking = true
        val waypoints = getWaypointsForLocation(selectedMapLocation.classroom)
        currentWaypointIndex = 0
        viewModelScope.launch {
            for (i in waypoints.indices) {
                currentWaypointIndex = i
                val remainingRatio = (waypoints.size - 1 - i).toDouble() / (waypoints.size - 1).coerceAtLeast(1).toDouble()
                simulatedDistance = (180.0 * remainingRatio).coerceAtLeast(12.0)
                delay(1200L) // 1.2 seconds per step
            }
            isWalking = false
            isGeofenceTriggered = true
            delay(1200)
            isGeofenceTriggered = false
            
            // Check if this corresponds to an active class
            val matchedClass = selectedClassForMap ?: repository.allClasses.first().firstOrNull { it.classroom == selectedMapLocation.classroom }
            if (matchedClass != null) {
                selectedClassForMap = matchedClass
                currentScreen = Screen.AttendanceVerification
            } else {
                studentProfile.value?.let { profile ->
                    repository.saveProfile(profile.copy(points = profile.points + 15))
                }
                transactionSuccessMessage = "Welcome to ${selectedMapLocation.name}! You checked in successfully and received +15 PTS for exploring campus!"
            }
            onGeofenceReached()
        }
    }

    fun teleportToClassroom() {
        if (isWalking) return
        val waypoints = getWaypointsForLocation(selectedMapLocation.classroom)
        currentWaypointIndex = waypoints.size - 1
        simulatedDistance = 12.0
        isGeofenceTriggered = true
        viewModelScope.launch {
            delay(1000)
            isGeofenceTriggered = false
            val matchedClass = selectedClassForMap ?: repository.allClasses.first().firstOrNull { it.classroom == selectedMapLocation.classroom }
            if (matchedClass != null) {
                selectedClassForMap = matchedClass
                currentScreen = Screen.AttendanceVerification
            } else {
                studentProfile.value?.let { profile ->
                    repository.saveProfile(profile.copy(points = profile.points + 15))
                }
                transactionSuccessMessage = "Arrived at ${selectedMapLocation.name}! You checked in successfully and received +15 PTS!"
            }
        }
    }

    fun claimAttendanceReward() {
        val uClass = selectedClassForMap ?: return
        viewModelScope.launch {
            val ptsAwarded = when (arrivalStatusChoice) {
                "ON_TIME" -> 50
                "LATE" -> 25
                else -> 0
            }

            // Update Class status in Database
            val updatedClass = uClass.copy(
                status = when (arrivalStatusChoice) {
                    "ON_TIME" -> "ATTENDED"
                    "LATE" -> "LATE"
                    else -> "ABSENT"
                }
            )
            repository.updateClass(updatedClass)

            // Update points and attendance stats in Database Profile
            studentProfile.value?.let { currentProfile ->
                val isAttended = arrivalStatusChoice == "ON_TIME" || arrivalStatusChoice == "LATE"
                val isAbsent = arrivalStatusChoice == "ABSENT"
                
                var cs402Att = currentProfile.cs402Attended
                var cs402Tot = currentProfile.cs402Total
                var ai510Att = currentProfile.ai510Attended
                var ai510Tot = currentProfile.ai510Total
                var cs312Att = currentProfile.cs312Attended
                var cs312Tot = currentProfile.cs312Total
                var hum102Att = currentProfile.hum102Attended
                var hum102Tot = currentProfile.hum102Total
                
                if (isAttended || isAbsent) {
                    when (uClass.courseCode) {
                        "CS402" -> {
                            cs402Tot += 1
                            if (isAttended) cs402Att += 1
                        }
                        "AI510" -> {
                            ai510Tot += 1
                            if (isAttended) ai510Att += 1
                        }
                        "CS312" -> {
                            cs312Tot += 1
                            if (isAttended) cs312Att += 1
                        }
                        "HUM102" -> {
                            hum102Tot += 1
                            if (isAttended) hum102Att += 1
                        }
                    }
                }

                val updatedProfile = currentProfile.copy(
                    points = currentProfile.points + ptsAwarded,
                    cs402Attended = cs402Att,
                    cs402Total = cs402Tot,
                    ai510Attended = ai510Att,
                    ai510Total = ai510Tot,
                    cs312Attended = cs312Att,
                    cs312Total = cs312Tot,
                    hum102Attended = hum102Att,
                    hum102Total = hum102Tot
                )
                repository.updateProfile(updatedProfile)
            }

            selectedClassForMap = null
            currentScreen = Screen.Dashboard
        }
    }

    fun purchaseLunchItem(item: ShopItem) {
        val profile = studentProfile.value ?: return
        if (profile.points < item.costPoints) return

        viewModelScope.launch {
            // Deduct Points
            val updatedProfile = profile.copy(points = profile.points - item.costPoints)
            repository.updateProfile(updatedProfile)

            if (deviceHasNfc) {
                // Direct instant NFC Till Tap Redemption
                transactionSuccessMessage = "Direct NFC Tapped & Approved! Deducted ${item.costPoints} PTS. Pick up your hot '${item.name}' at the cafeteria register!"
            } else {
                // Generate a Digital Voucher Coupon & save to local wallet
                val qrData = "UNI-VOUCH-${item.id.uppercase()}-${(100000..999999).random()}"
                val voucher = RedeemedVoucher(
                    name = item.name,
                    costPoints = item.costPoints,
                    localValue = item.localValue,
                    qrCode = qrData,
                    redeemTimestamp = System.currentTimeMillis(),
                    isUsed = false,
                    studentEmail = profile.email
                )
                repository.addVoucher(voucher)
                transactionSuccessMessage = "Coupon Generated! Saved '${item.name}' voucher to your inventory. Scan the screen QR code at the cafeteria checkout till."
            }
            selectedShopItem = null
        }
    }

    fun scanVoucherInWallet(voucher: RedeemedVoucher) {
        viewModelScope.launch {
            repository.updateVoucher(voucher.copy(isUsed = true))
            activeVoucherToShow = null
            transactionSuccessMessage = "Voucher successfully scanned and processed at Cafe register! Total balance updated."
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logoutAllProfiles()
            currentScreen = Screen.Landing
        }
    }
}

// Root Navigation Component
@Composable
fun UniPointsApp(viewModel: UniPointsViewModel) {
    val screen = viewModel.currentScreen

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenSwitch"
        ) { targetScreen ->
            when (targetScreen) {
                is Screen.Landing -> LandingScreen(viewModel)
                is Screen.Dashboard -> DashboardScreen(viewModel)
                is Screen.Geomap -> GeomapScreen(viewModel)
                is Screen.AttendanceVerification -> AttendanceScreen(viewModel)
                is Screen.LunchShop -> ShopScreen(viewModel)
                is Screen.Settings -> SettingsScreen(viewModel)
            }
        }
    }
}

// 1. Landing / SSO Login & Registration Screen
@Composable
fun LandingScreen(viewModel: UniPointsViewModel) {
    var isRegisterTab by remember { mutableStateOf(false) }
    
    // Login fields
    var email by remember { mutableStateOf("MMogomotsi14@gmail.com") }
    var password by remember { mutableStateOf("Password123") }
    
    // Registration fields
    var registerName by remember { mutableStateOf("") }
    var registerEmail by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    
    // Permissions & privacy settings
    var gpsConsent by remember { mutableStateOf(true) }
    var notificationConsent by remember { mutableStateOf(true) }
    
    var isAuthenticating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFFEADDFF).copy(alpha = 0.25f)
                    )
                )
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Large illustration generated image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_student_rewards_banner_1783753259995),
                contentDescription = "UniEarn Welcome Hero",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF21005D).copy(alpha = 0.75f))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = "App Logo Icon",
                tint = Color(0xFF21005D),
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "UniEarn",
                color = Color(0xFF21005D),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("app_logo_title")
            )
        }

        Text(
            text = "Campus Attendance Rewards & Multi-Promenade",
            color = Color(0xFF49454F),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // QUICK PROFILE SIMULATION SWITCHER
        Text(
            text = "SIMULATE ACTIVE STUDENT PROFILES",
            color = Color(0xFF21005D),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Student A: Mogomotsi
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (email == "MMogomotsi14@gmail.com") Color(0xFFEADDFF) else Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        isRegisterTab = false
                        email = "MMogomotsi14@gmail.com"
                        password = "Password123"
                    }
                    .border(
                        1.dp,
                        if (email == "MMogomotsi14@gmail.com") Color(0xFF21005D) else Color(0xFFCAC4D0).copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF21005D), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("MM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Mogomotsi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                    Text("350 PTS (R35)", fontSize = 10.sp, color = Color(0xFF49454F))
                }
            }

            // Student B: Zama
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (email == "ZamaS@varsity.ac.za") Color(0xFFEADDFF) else Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        isRegisterTab = false
                        email = "ZamaS@varsity.ac.za"
                        password = "Password456"
                    }
                    .border(
                        1.dp,
                        if (email == "ZamaS@varsity.ac.za") Color(0xFF21005D) else Color(0xFFCAC4D0).copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF0061A4), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ZS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Zama Shabangu", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                    Text("820 PTS (R82)", fontSize = 10.sp, color = Color(0xFF49454F))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Tab Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isRegisterTab = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isRegisterTab) Color(0xFF21005D) else Color(0xFFF3F3F3),
                            contentColor = if (!isRegisterTab) Color.White else Color(0xFF1C1B1F)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SSO Login", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { isRegisterTab = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRegisterTab) Color(0xFF21005D) else Color(0xFFF3F3F3),
                            contentColor = if (isRegisterTab) Color.White else Color(0xFF1C1B1F)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Register", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isRegisterTab) {
                    // LOGIN INTERFACE
                    Text(
                        text = "University Student SSO Login",
                        color = Color(0xFF1C1B1F),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Student Email Address") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0),
                            focusedLabelColor = Color(0xFF21005D),
                            unfocusedLabelColor = Color(0xFF49454F),
                            focusedTextColor = Color(0xFF1C1B1F),
                            unfocusedTextColor = Color(0xFF1C1B1F),
                            focusedContainerColor = Color(0xFFFCF8FD),
                            unfocusedContainerColor = Color(0xFFFCF8FD)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_email_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("SSO Password") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0),
                            focusedLabelColor = Color(0xFF21005D),
                            unfocusedLabelColor = Color(0xFF49454F),
                            focusedTextColor = Color(0xFF1C1B1F),
                            unfocusedTextColor = Color(0xFF1C1B1F),
                            focusedContainerColor = Color(0xFFFCF8FD),
                            unfocusedContainerColor = Color(0xFFFCF8FD)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_password_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                } else {
                    // REGISTRATION INTERFACE
                    Text(
                        text = "Create Student Profile",
                        color = Color(0xFF1C1B1F),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = registerName,
                        onValueChange = { registerName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = registerEmail,
                        onValueChange = { registerEmail = it },
                        label = { Text("Student Email Address") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = registerPassword,
                        onValueChange = { registerPassword = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ASK FOR PERMISSIONS SECTION
                Divider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "REQUIRED CAMPUS PERMISSIONS",
                    color = Color(0xFF49454F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = gpsConsent,
                        onCheckedChange = { gpsConsent = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF21005D))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Real-time GPS Location", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                        Text("Required to detect classroom geofences", fontSize = 10.sp, color = Color(0xFF49454F))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = notificationConsent,
                        onCheckedChange = { notificationConsent = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF21005D))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Push Notifications", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                        Text("Receive instant reward alerts & countdowns", fontSize = 10.sp, color = Color(0xFF49454F))
                    }
                }

                // PRIVACY ASSURANCE WARNING
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFCF8FD)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .border(1.dp, Color(0xFF21005D).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Safe",
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy Safe Assurance: Your data remains local and encrypted on-device. We never upload your credentials or live locations.",
                            fontSize = 10.sp,
                            color = Color(0xFF49454F),
                            lineHeight = 14.sp
                        )
                    }
                }

                if (isAuthenticating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF21005D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Synchronizing Student Timetable...",
                            color = Color(0xFF21005D),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            isAuthenticating = true
                            if (!isRegisterTab) {
                                // Simulate switching profile points based on choice
                                val pts = if (email == "ZamaS@varsity.ac.za") 820 else 350
                                val studentName = if (email == "ZamaS@varsity.ac.za") "Zama Shabangu" else "Mogomotsi"
                                viewModel.handleSignIn(email, studentName, pts)
                            } else {
                                // Register new custom student
                                val nameInput = registerName.ifBlank { "New Student" }
                                val emailInput = registerEmail.ifBlank { "student@varsity.ac.za" }
                                viewModel.handleSignIn(emailInput, nameInput, 200) // 200 PTS initial bonus
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF21005D),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_button"),
                        enabled = if (!isRegisterTab) (email.isNotBlank() && password.isNotBlank()) else (registerName.isNotBlank() && registerEmail.isNotBlank())
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (!isRegisterTab) "Sync & Enter Portal" else "Register Student & Get Bonus",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Connected securely to UniEarn campus database API. Powered by student smart card geofencing protocols.",
            color = Color(0xFF49454F),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// 2. Dashboard Screen
@Composable
fun DashboardScreen(viewModel: UniPointsViewModel) {
    val profile by viewModel.studentProfile.collectAsStateWithLifecycle()
    val timetable by viewModel.allClasses.collectAsStateWithLifecycle()
    val vouchers by viewModel.allVouchers.collectAsStateWithLifecycle()

    var simulateActiveClassNow by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var showEditProfileDialog by remember { mutableStateOf(false) }
                        
                        if (showEditProfileDialog && profile != null) {
                            EditProfileDialog(
                                profile = profile!!,
                                onDismiss = { showEditProfileDialog = false },
                                onSave = { newName, newAvatarIndex ->
                                    viewModel.viewModelScope.launch {
                                        viewModel.repository.saveProfile(
                                            profile!!.copy(name = newName, avatarIndex = newAvatarIndex)
                                        )
                                        showEditProfileDialog = false
                                    }
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clickable { showEditProfileDialog = true }
                                .testTag("edit_profile_avatar_click")
                        ) {
                            AvatarImage(avatarIndex = profile?.avatarIndex ?: 0, size = 40.dp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = profile?.name ?: "Student",
                                color = Color(0xFF1C1B1F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF22C55E), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SSO Verified (Tap Avatar to Edit)",
                                    color = Color(0xFF49454F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Log Out",
                            tint = Color(0xFF49454F)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F2FA))
                    .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDashboard = viewModel.currentScreen == Screen.Dashboard || viewModel.currentScreen == Screen.Geomap || viewModel.currentScreen == Screen.AttendanceVerification
                val isShop = viewModel.currentScreen == Screen.LunchShop
                val isSettings = viewModel.currentScreen == Screen.Settings

                // Button 1: Home
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.Dashboard }
                        .testTag("dashboard_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDashboard) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Dashboard",
                                tint = if (isDashboard) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Home",
                            fontSize = 11.sp,
                            fontWeight = if (isDashboard) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDashboard) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }

                // Button 2: Shop
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.LunchShop }
                        .testTag("shop_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isShop) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Shop",
                                tint = if (isShop) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Shop",
                            fontSize = 11.sp,
                            fontWeight = if (isShop) FontWeight.Bold else FontWeight.Medium,
                            color = if (isShop) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }

                // Button 3: Settings
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.Settings }
                        .testTag("settings_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSettings) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (isSettings) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Settings",
                            fontSize = 11.sp,
                            fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSettings) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Real-time Clock display
            TimeBar()
            // Success indicator dialog trigger (NFC/Voucher purchase confirmation)
            if (viewModel.transactionSuccessMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.transactionSuccessMessage = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF22C55E))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transaction Approved!")
                        }
                    },
                    text = { Text(viewModel.transactionSuccessMessage ?: "") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.transactionSuccessMessage = null }) {
                            Text("Awesome", color = Color(0xFF21005D), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Wallet and points display Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD0BCFF), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "AVAILABLE REWARDS BALANCE",
                                color = Color(0xFF49454F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${profile?.points ?: 0} PTS",
                                color = Color(0xFF21005D),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFD8E4))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "≈ R${String.format("%.2f", (profile?.points ?: 0) * 0.10)}",
                                color = Color(0xFF31111D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom quick actions on wallet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.currentScreen = Screen.LunchShop },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF21005D),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("NFC Pay", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = { viewModel.currentScreen = Screen.LunchShop },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.5f),
                                contentColor = Color(0xFF21005D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21005D).copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Redeem Voucher", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // MODULE ATTENDANCE TRACKER
            Text(
                text = "Module Attendance Tracker",
                color = Color(0xFF1C1B1F),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEADDFF), RoundedCornerShape(24.dp))
                    .testTag("attendance_tracker_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val modules = listOf(
                        Triple("CS402: Mobile App Dev", profile?.cs402Attended ?: 8, profile?.cs402Total ?: 10),
                        Triple("AI510: Neural Networks", profile?.ai510Attended ?: 7, profile?.ai510Total ?: 10),
                        Triple("CS312: Database Arch", profile?.cs312Attended ?: 9, profile?.cs312Total ?: 10),
                        Triple("HUM102: SA Literature", profile?.hum102Attended ?: 6, profile?.hum102Total ?: 10)
                    )

                    modules.forEach { (moduleName, attended, total) ->
                        val percent = if (total > 0) (attended.toFloat() / total * 100).toInt() else 0
                        val (statusColor, statusText) = when {
                            percent >= 85 -> Color(0xFF15803D) to "Excellent"
                            percent >= 75 -> Color(0xFFB45309) to "Good"
                            else -> Color(0xFFB91C1C) to "At Risk"
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = moduleName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1B1F)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$attended/$total ($percent%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF49454F)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = if (total > 0) attended.toFloat() / total else 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = if (percent >= 75) Color(0xFF6750A4) else Color(0xFFEF4444),
                                trackColor = Color(0xFFF3F3F3)
                            )
                        }
                    }
                }
            }

            // Quick simulation controls (Extremely useful for demoing both states)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F3)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simulation Helper",
                            color = Color(0xFF1C1B1F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (simulateActiveClassNow) "Active class mode triggered" else "Campus is currently idle",
                            color = Color(0xFF49454F),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = { simulateActiveClassNow = !simulateActiveClassNow },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simulateActiveClassNow) Color(0xFFFFD8E4) else Color(0xFF21005D),
                            contentColor = if (simulateActiveClassNow) Color(0xFF31111D) else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("toggle_simulation_button")
                    ) {
                        Text(
                            text = if (simulateActiveClassNow) "Set Campus Idle" else "Force Active Class",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // SECTION 2: INTEGRATED LIVE CAMPUS MAP & ROUTING
            Text(
                text = "Live Campus Map & Navigation",
                color = Color(0xFF1C1B1F),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Embedded Map Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, Color(0xFF21005D).copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            ) {
                val mapWidth = maxWidth
                val mapHeight = maxHeight

                // 1. Campus Map background illustration
                Image(
                    painter = painterResource(id = R.drawable.img_campus_map_1783754520860),
                    contentDescription = "Stylized Campus Map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 2. Custom Canvas for Route Lines, Waypoints, and Student Live Marker
                val waypoints = getWaypointsForLocation(viewModel.selectedMapLocation.classroom)
                val currentWaypoint = waypoints.getOrNull(viewModel.currentWaypointIndex) ?: waypoints.first()

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val widthPx = size.width
                    val heightPx = size.height

                    // Draw route lines connecting all waypoints
                    if (waypoints.size > 1) {
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(widthPx * waypoints[0].xRatio, heightPx * waypoints[0].yRatio)
                        for (i in 1 until waypoints.size) {
                            path.lineTo(widthPx * waypoints[i].xRatio, heightPx * waypoints[i].yRatio)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF21005D),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            )
                        )
                    }

                    // Draw check-in radius geofence (20m) around selected target
                    val selectedX = widthPx * viewModel.selectedMapLocation.xRatio
                    val selectedY = heightPx * viewModel.selectedMapLocation.yRatio
                    drawCircle(
                        color = Color(0xFF22C55E).copy(alpha = 0.15f),
                        radius = 45.dp.toPx(),
                        center = Offset(selectedX, selectedY)
                    )
                    drawCircle(
                        color = Color(0xFF22C55E).copy(alpha = 0.5f),
                        radius = 45.dp.toPx(),
                        center = Offset(selectedX, selectedY),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                    )

                    // Draw small dots for intermediate waypoints
                    for (wp in waypoints) {
                        drawCircle(
                            color = Color(0xFF49454F),
                            radius = 4.dp.toPx(),
                            center = Offset(widthPx * wp.xRatio, heightPx * wp.yRatio)
                        )
                    }

                    // Draw Student Live dot (deep purple) with outer radar ripple pulse
                    val studentX = widthPx * currentWaypoint.xRatio
                    val studentY = heightPx * currentWaypoint.yRatio
                    drawCircle(
                        color = Color(0xFF21005D).copy(alpha = 0.25f),
                        radius = 18.dp.toPx(),
                        center = Offset(studentX, studentY)
                    )
                    drawCircle(
                        color = Color(0xFF21005D),
                        radius = 8.dp.toPx(),
                        center = Offset(studentX, studentY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(studentX, studentY)
                    )
                }

                // 3. Float interactive clickable icons for ALL available campus locations
                viewModel.campusLocations.forEach { location ->
                    val isSelected = viewModel.selectedMapLocation.classroom == location.classroom
                    val posX = mapWidth * location.xRatio - 18.dp
                    val posY = mapHeight * location.yRatio - 18.dp

                    Box(
                        modifier = Modifier
                            .offset(x = posX, y = posY)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF21005D) else Color.White)
                            .border(1.5.dp, if (isSelected) Color.White else Color(0xFF21005D).copy(alpha = 0.3f), CircleShape)
                            .clickable {
                                viewModel.selectedMapLocation = location
                                viewModel.currentWaypointIndex = 0
                                viewModel.simulatedDistance = 180.0
                                viewModel.isWalking = false
                                viewModel.isGeofenceTriggered = false
                            }
                            .testTag("map_marker_${location.classroom.replace(" ", "_")}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = location.icon,
                            contentDescription = location.name,
                            tint = if (isSelected) Color.White else Color(0xFF21005D),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Legend overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF21005D), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live Student Location", color = Color(0xFF1C1B1F), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF22C55E), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check-In Geofence", color = Color(0xFF22C55E), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Target Location Panel & Simulator actions
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE ROUTE TO",
                                color = Color(0xFF49454F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = viewModel.selectedMapLocation.name,
                                color = Color(0xFF21005D),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = viewModel.selectedMapLocation.classroom,
                                color = Color(0xFF49454F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "DISTANCE",
                                color = Color(0xFF49454F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${viewModel.simulatedDistance.toInt()}m",
                                color = if (viewModel.simulatedDistance <= 20.0) Color(0xFF22C55E) else Color(0xFFEF4444),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (viewModel.simulatedDistance <= 20.0) "Ready!" else "Too Far",
                                color = if (viewModel.simulatedDistance <= 20.0) Color(0xFF22C55E) else Color(0xFF49454F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.startWalkingSimulation {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.isWalking) Color(0xFFCAC4D0) else Color(0xFF21005D),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                                .testTag("walk_button"),
                            enabled = !viewModel.isWalking && viewModel.simulatedDistance > 20.0
                        ) {
                            if (viewModel.isWalking) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Walking...", fontSize = 11.sp)
                            } else {
                                Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = "Walk", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simulate Walk", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { viewModel.teleportToClassroom() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD8E4),
                                contentColor = Color(0xFF31111D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("teleport_button"),
                            enabled = !viewModel.isWalking && viewModel.simulatedDistance > 20.0
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Teleport", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Teleport", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Step-by-Step Waypoint Directions Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "STEP-BY-STEP ROUTE DIRECTIONS",
                        color = Color(0xFF21005D),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val activeWaypoints = getWaypointsForLocation(viewModel.selectedMapLocation.classroom)
                    activeWaypoints.forEachIndexed { index, wp ->
                        val isActive = index == viewModel.currentWaypointIndex
                        val isPassed = index < viewModel.currentWaypointIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isActive -> Color(0xFF21005D)
                                            isPassed -> Color(0xFF22C55E)
                                            else -> Color(0xFFE8DEF8)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPassed) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Passed",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isActive) Color.White else Color(0xFF49454F),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = wp.direction,
                                color = when {
                                    isActive -> Color(0xFF1D192B)
                                    isPassed -> Color(0xFF49454F).copy(alpha = 0.6f)
                                    else -> Color(0xFF49454F)
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // TODAY'S TIMETABLE SECTION
            Text(
                text = "Today's Class Timetable",
                color = Color(0xFF1C1B1F),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (timetable.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No synchronized classes found.", color = Color(0xFF49454F))
                }
            } else {
                timetable.forEach { uClass ->
                    val statusColor = when (uClass.status) {
                        "ATTENDED" -> Color(0xFF001D36)
                        "LATE" -> Color(0xFF601410)
                        "ABSENT" -> Color(0xFF601410)
                        else -> Color(0xFF49454F)
                    }

                    val statusBgColor = when (uClass.status) {
                        "ATTENDED" -> Color(0xFFD0E4FF)
                        "LATE" -> Color(0xFFF2B8B5)
                        "ABSENT" -> Color(0xFFF2B8B5)
                        else -> Color(0xFFF3F3F3)
                    }

                    val statusLabel = when (uClass.status) {
                        "ATTENDED" -> "Attended (+100 PTS)"
                        "LATE" -> "Attended Late (+50 PTS)"
                        "ABSENT" -> "Absent (0 PTS)"
                        else -> "Scheduled"
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = uClass.courseCode,
                                        color = Color(0xFF21005D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .background(Color(0xFFEADDFF), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${uClass.startTime} - ${uClass.endTime}",
                                        color = Color(0xFF49454F),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uClass.courseName,
                                    color = Color(0xFF1C1B1F),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Classroom: ${uClass.classroom}",
                                    color = Color(0xFF49454F),
                                    fontSize = 12.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(statusBgColor)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // REDEEMED ACTIVE VOUCHERS WALLET
            val activeVouchers = vouchers.filter { !it.isUsed }
            if (activeVouchers.isNotEmpty()) {
                Text(
                    text = "My Digital Voucher Wallet (${activeVouchers.size})",
                    color = Color(0xFF1C1B1F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )

                activeVouchers.forEach { voucher ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEADDFF), RoundedCornerShape(16.dp))
                            .clickable { viewModel.activeVoucherToShow = voucher }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = "Ticket",
                                    tint = Color(0xFF21005D),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = voucher.name,
                                        color = Color(0xFF1C1B1F),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Voucher Code: ${voucher.qrCode}",
                                        color = Color(0xFF49454F),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = voucher.localValue,
                                    color = Color(0xFF22C55E),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap to Scan",
                                    color = Color(0xFF21005D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Interactive Voucher scan overlay
    viewModel.activeVoucherToShow?.let { voucher ->
        AlertDialog(
            onDismissRequest = { viewModel.activeVoucherToShow = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp),
            title = {
                Text(
                    text = "Voucher QR Coupon",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1C1B1F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = voucher.name,
                        color = Color(0xFF1C1B1F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Value: ${voucher.localValue}",
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw a highly believable, beautiful simulated 2D Barcode and QR code in Canvas
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw QR matrix simulation lines
                            val cellSize = size.width / 8f
                            for (i in 0 until 8) {
                                        for (j in 0 until 8) {
                                    // Make some solid black blocks and corners to look like a real QR code
                                    val isAnchor = (i < 3 && j < 3) || (i > 4 && j < 3) || (i < 3 && j > 4)
                                    val isRandomBlock = (i + j) % 3 == 0 || (i * j) % 2 == 1
                                    if (isAnchor || isRandomBlock) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(i * cellSize, j * cellSize),
                                            size = androidx.compose.ui.geometry.Size(cellSize - 2, cellSize - 2)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = voucher.qrCode,
                        color = Color(0xFF1C1B1F),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFFF3F3F3), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Simulate presenting this voucher at the cafeteria checkout scanner.",
                        color = Color(0xFF49454F),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.scanVoucherInWallet(voucher) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("scan_voucher_button")
                ) {
                    Text("Scan Screen Voucher at Till Register", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.activeVoucherToShow = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color(0xFFEF4444))
                }
            }
        )
    }
}

// 3. Geomap display & Walk routing screen
@Composable
fun GeomapScreen(viewModel: UniPointsViewModel) {
    val targetClass = viewModel.selectedClassForMap ?: return
    val distance = viewModel.simulatedDistance
    val isWalking = viewModel.isWalking
    val isGeofence = viewModel.isGeofenceTriggered

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1B1F))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "GPS CLASSROOM ROUTING",
                    color = Color(0xFF21005D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = targetClass.courseName,
                    color = Color(0xFF1C1B1F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Target Info Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TARGET DESTINATION",
                        color = Color(0xFF49454F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = targetClass.classroom,
                        color = Color(0xFF21005D),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "GPS: Lat ${targetClass.latitude}, Lon ${targetClass.longitude}",
                        color = Color(0xFF49454F),
                        fontSize = 11.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DISTANCE",
                        color = Color(0xFF49454F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${distance.toInt()} meters",
                        color = if (distance <= 20.0) Color(0xFF22C55E) else Color(0xFFEF4444),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (distance <= 20.0) "Within range! [GEOFENCED]" else "Too far to check-in",
                        color = if (distance <= 20.0) Color(0xFF22C55E) else Color(0xFF49454F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // GEOMAP DISPLAY CANVAS (plots dynamic GPS coordinates)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF3F3F3))
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_campus_map_1783754520860),
                contentDescription = "Stylized Campus Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val mapWidth = size.width
                val mapHeight = size.height

                // Center of target building (Tech Hall) - locked at coordinates (cx, cy)
                val targetX = mapWidth * 0.7f
                val targetY = mapHeight * 0.3f

                // Classroom 20m Geofence boundary circle (120px representing 20m)
                drawCircle(
                    color = Color(0xFF22C55E).copy(alpha = 0.15f),
                    radius = 70.dp.toPx(),
                    center = Offset(targetX, targetY)
                )
                drawCircle(
                    color = Color(0xFF22C55E).copy(alpha = 0.6f),
                    radius = 70.dp.toPx(),
                    center = Offset(targetX, targetY),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )

                // Student location (Moves closer as simulated distance decreases)
                val t = (180.0 - distance) / 180.0
                val studentX = mapWidth * 0.25f + (targetX - mapWidth * 0.25f) * t.toFloat()
                val studentY = mapHeight * 0.75f + (targetY - mapHeight * 0.75f) * t.toFloat()

                // Draw dotted path from start to target
                drawLine(
                    color = Color(0xFF49454F),
                    start = Offset(mapWidth * 0.25f, mapHeight * 0.75f),
                    end = Offset(targetX, targetY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )

                // Draw Target Building Location Card (Visual Map Pin)
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                    radius = 28.dp.toPx(),
                    center = Offset(targetX, targetY)
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 10.dp.toPx(),
                    center = Offset(targetX, targetY)
                )

                // Draw Student (Live Deep Purple Dot with radar pulses)
                drawCircle(
                    color = Color(0xFF21005D).copy(alpha = 0.25f),
                    radius = 22.dp.toPx(),
                    center = Offset(studentX, studentY)
                )
                drawCircle(
                    color = Color(0xFF21005D),
                    radius = 8.dp.toPx(),
                    center = Offset(studentX, studentY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(studentX, studentY)
                )
            }

            // Legend / Floating Indicators on Map
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF21005D), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Student Live Location (You)", color = Color(0xFF1C1B1F), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Classroom (Tech Hall)", color = Color(0xFF1C1B1F), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).border(1.dp, Color(0xFF22C55E), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("20m Classroom Check-in Radius", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Map Control panel containing the automated movement simulations
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F3)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "WALK ROUTE SIMULATOR",
                    color = Color(0xFF49454F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.startWalkingSimulation {}
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWalking) Color(0xFFCAC4D0) else Color(0xFF21005D),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("walk_button"),
                        enabled = !isWalking && distance > 20.0
                    ) {
                        if (isWalking) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Walking...", fontSize = 13.sp)
                        } else {
                            Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = "Walk")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Walking", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.teleportToClassroom() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD8E4),
                            contentColor = Color(0xFF31111D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("teleport_button"),
                        enabled = !isWalking && distance > 20.0
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Teleport")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Instant Teleport", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Press 'Simulate Walking' to watch the GPS Blue Dot move along campus lanes. Entering the 20m radius will automatically trigger background geofence check-in.",
                    color = Color(0xFF49454F),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Geofence detection notification overlay (Simulating automatic Android geofence trigger)
    if (isGeofence) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Nfc, contentDescription = "Alert", tint = Color(0xFF22C55E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GEOFENCE TRIGGERED", color = Color(0xFF22C55E))
                }
            },
            text = {
                Text(
                    text = "Mobile GPS background scan confirms you have safely entered the classroom 20m check-in radius! Redirecting to Attendance Portal...",
                    color = Color(0xFF1C1B1F),
                    fontWeight = FontWeight.Medium
                )
            },
            confirmButton = {}
        )
    }
}

// 4. Attendance verification & reward claim screen
@Composable
fun AttendanceScreen(viewModel: UniPointsViewModel) {
    val currentClass = viewModel.selectedClassForMap ?: return
    var arrivalSelection by remember { mutableStateOf("ON_TIME") }

    val confettis = remember {
        List(40) {
            Offset(
                x = (100..1000).random().toFloat(),
                y = (0..600).random().toFloat()
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "GEOFENCE CHECK-IN VERIFIED",
                color = Color(0xFF22C55E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = currentClass.courseName,
                color = Color(0xFF1C1B1F),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Classroom Location: ${currentClass.classroom}",
                color = Color(0xFF49454F),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Color(0xFFEADDFF).copy(alpha = 0.4f), CircleShape)
                    .border(3.dp, Color(0xFF21005D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    confettis.forEach { offset ->
                        drawCircle(
                            color = listOf(Color(0xFF21005D), Color(0xFF22C55E), Color(0xFFFFD8E4), Color(0xFFEF4444)).random().copy(alpha = 0.8f),
                            radius = (4..8).random().dp.toPx(),
                            center = offset
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Graduation Cap",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (arrivalSelection == "ON_TIME") "+100" else if (arrivalSelection == "LATE") "+50" else "+0",
                        color = Color(0xFF21005D),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "POINTS",
                        color = Color(0xFF49454F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Section 3: Arrival Selection Choices
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select Simulation Arrival Time:",
                    color = Color(0xFF1C1B1F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // 1. ON-TIME
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (arrivalSelection == "ON_TIME") Color(0xFFE8DEF8) else Color.Transparent)
                        .clickable {
                            arrivalSelection = "ON_TIME"
                            viewModel.arrivalStatusChoice = "ON_TIME"
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = arrivalSelection == "ON_TIME",
                        onClick = {
                            arrivalSelection = "ON_TIME"
                            viewModel.arrivalStatusChoice = "ON_TIME"
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF21005D), unselectedColor = Color(0xFF49454F))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("On Time (Within 5m limit)", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Awards 100% full points: 100 PTS", color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                }

                // 2. LATE ARRIVAL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (arrivalSelection == "LATE") Color(0xFFE8DEF8) else Color.Transparent)
                        .clickable {
                            arrivalSelection = "LATE"
                            viewModel.arrivalStatusChoice = "LATE"
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = arrivalSelection == "LATE",
                        onClick = {
                            arrivalSelection = "LATE"
                            viewModel.arrivalStatusChoice = "LATE"
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF21005D), unselectedColor = Color(0xFF49454F))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Late Arrival (After 5m limit)", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Awards 50% partial points: 50 PTS", color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                }

                // 3. ABSENT FOR WHOLE CLASS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (arrivalSelection == "ABSENT") Color(0xFFE8DEF8) else Color.Transparent)
                        .clickable {
                            arrivalSelection = "ABSENT"
                            viewModel.arrivalStatusChoice = "ABSENT"
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = arrivalSelection == "ABSENT",
                        onClick = {
                            arrivalSelection = "ABSENT"
                            viewModel.arrivalStatusChoice = "ABSENT"
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF21005D), unselectedColor = Color(0xFF49454F))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Missed Class entirely (Absent)", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Awards 0% points: 0 PTS", color = Color(0xFF49454F), fontSize = 11.sp)
                    }
                }
            }
        }

        // Action button to credit Room database
        Button(
            onClick = { viewModel.claimAttendanceReward() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D), contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("claim_reward_button")
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Check", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Verify Attendance & Update Database",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// 5. Lunch Shop and coupon/NFC redemption screen
@Composable
fun ShopScreen(viewModel: UniPointsViewModel) {
    val profile by viewModel.studentProfile.collectAsStateWithLifecycle()
    val vouchers by viewModel.allVouchers.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("Fruits & Snacks") }

    val shopItems = remember {
        listOf(
            // 1. Fruits & Snacks
            ShopItem("banana", "Fresh Bananas (Bunch)", 50, "R5.00", "Potassium-rich ripe organic yellow bananas, perfect for sustained brain energy.", "Fruits & Snacks", Icons.Default.Favorite),
            ShopItem("berries", "Mixed Berries Bowl", 120, "R12.00", "A chilled cup of sweet fresh blueberries, strawberries, and sweet raspberries.", "Fruits & Snacks", Icons.Default.Favorite),
            ShopItem("granola", "Crunchy Oats Granola Bar", 40, "R4.00", "Whole-grain oats with honey, almonds, and dried cranberries.", "Fruits & Snacks", Icons.Default.Favorite),
            ShopItem("apple", "Apple Slices & Peanut Butter", 60, "R6.00", "Crisp Granny Smith apple wedges served with a creamy salted peanut butter dip.", "Fruits & Snacks", Icons.Default.Favorite),
            ShopItem("biltong", "Traditional Beef Biltong", 150, "R15.00", "Savoury, tender, high-protein South African cured beef strips.", "Fruits & Snacks", Icons.Default.Favorite),

            // 2. Coffee Bar
            ShopItem("macchiato", "Iced Caramel Macchiato", 200, "R20.00", "Freshly pulled espresso shots layered with caramel drizzle and whole cold milk.", "Coffee Bar", Icons.Default.LocalCafe),
            ShopItem("espresso", "Double Espresso Shot", 120, "R12.00", "Bold and intense classic espresso brewed from premium local roasted beans.", "Coffee Bar", Icons.Default.LocalCafe),
            ShopItem("cappuccino", "Velvety Cappuccino", 180, "R18.00", "Rich espresso capped with thick, smooth frothed milk and a dust of cocoa.", "Coffee Bar", Icons.Default.LocalCafe),
            ShopItem("latte", "Iced Matcha Latte", 220, "R22.00", "Stone-ground Japanese green tea whisked with creamy chilled milk and sweetener.", "Coffee Bar", Icons.Default.LocalCafe),
            ShopItem("coldbrew", "Nitro Cold Brew Coffee", 240, "R24.00", "Slow-steeped cold brew infused with nitrogen for a super-smooth texture.", "Coffee Bar", Icons.Default.LocalCafe),

            // 3. Lunch Cafe
            ShopItem("hotdog", "Campus Hot Dog & Soda", 150, "R15.00", "All-beef sausage link inside a toasted bun, paired with an ice-cold fountain soda.", "Lunch Cafe", Icons.Default.Restaurant),
            ShopItem("burger", "Double Cheeseburger Meal", 300, "R30.00", "Two fire-grilled beef patties, melted cheddar, pickles, secret sauce and crinkle fries.", "Lunch Cafe", Icons.Default.Restaurant),
            ShopItem("sushi", "Premium Sushi Set", 350, "R35.00", "Fresh salmon, tuna, and California roll selections served with light soy and wasabi.", "Lunch Cafe", Icons.Default.Restaurant),
            ShopItem("pizza", "Gourmet Pepperoni Pizza Slice", 180, "R18.00", "Freshly baked pizza slice topped with marinara, dynamic mozzarella and thin sliced pepperoni.", "Lunch Cafe", Icons.Default.Restaurant),
            ShopItem("wrap", "Chicken Mayo & Avocado Wrap", 250, "R25.00", "Toasted tortilla filled with grilled shredded chicken breast, cream mayo, and fresh avocado.", "Lunch Cafe", Icons.Default.Restaurant),
            ShopItem("buddhabowl", "Vegan Quinoa Buddha Bowl", 280, "R28.00", "Nutritious mix of organic red quinoa, roasted sweet potatoes, avocado, spinach, and tahini.", "Lunch Cafe", Icons.Default.Restaurant),

            // 4. School Accessories
            ShopItem("notebook", "A5 Branded Hardcover Notebook", 120, "R12.00", "Premium 160-page ruled notebook featuring the official silver-embossed varsity crest.", "School Accessories", Icons.Default.Book),
            ShopItem("pens", "Ergonomic Gel Pens Set (5 Pack)", 80, "R8.00", "Comfort-grip retracting fine gel ink pens in academic blue, black, and red.", "School Accessories", Icons.Default.Book),
            ShopItem("bottle", "Stainless Steel Thermal Water Bottle", 250, "R25.00", "Double-wall vacuum insulated 750ml bottle. Keeps drinks cold for 24 hours.", "School Accessories", Icons.Default.Book),
            ShopItem("backpack", "Heavy-Duty Laptop Backpack", 550, "R55.00", "Water-resistant student backpack with padded laptop compartment and anti-theft zipper.", "School Accessories", Icons.Default.Book),
            ShopItem("planner", "Undated Weekly Academic Planner", 140, "R14.00", "Manage classes, assignments, and exam calendars with elegant daily schedules.", "School Accessories", Icons.Default.Book),

            // 5. Tech Stuff
            ShopItem("earbuds", "Wireless Bluetooth Earbuds", 450, "R45.00", "Immersive stereo sound with smart touch control, long battery life, and microphone.", "Tech Stuff", Icons.Default.Smartphone),
            ShopItem("powerbank", "20000mAh High-Speed Power Bank", 350, "R35.00", "Dual USB outputs, fast charging capability to keep your laptop and phone powered on the go.", "Tech Stuff", Icons.Default.Smartphone),
            ShopItem("mouse", "Ergonomic Wireless Mouse", 180, "R18.00", "Silent-click multi-mode wireless mouse with adjustable DPI and rechargeable battery.", "Tech Stuff", Icons.Default.Smartphone),
            ShopItem("cable", "Reinforced 3-in-1 Charging Cable", 100, "R10.00", "Heavy-duty braided cable with Lightning, USB-C, and Micro-USB connectors.", "Tech Stuff", Icons.Default.Smartphone),
            ShopItem("sleeve", "Padded 14-inch Laptop Sleeve", 220, "R22.00", "Ultra-slim soft-cushion neoprene bag to shield your notebook from shock and scratches.", "Tech Stuff", Icons.Default.Smartphone),

            // 6. Fashion Accessories
            ShopItem("beanie", "Knitted Varsity Beanie", 110, "R11.00", "Cosy ribbed knit cap in school colors with a thick fold-over cuff and classic patch.", "Fashion Accessories", Icons.Default.School),
            ShopItem("socks", "Classic School Logo Socks (Pair)", 50, "R5.00", "Cushioned crew socks stitched with the official school emblem. Super comfortable.", "Fashion Accessories", Icons.Default.School),
            ShopItem("hoodie", "Classic Campus Fleece Hoodie", 480, "R48.00", "Ultra-soft cotton blend pullover with premium embroidery lettering and front pouch pocket.", "Fashion Accessories", Icons.Default.School),
            ShopItem("wristband", "Embossed Leather Wristband", 90, "R9.00", "Genuine brown leather band embossed with 'EXCELLENCE & INTEGRITY' varsity motto.", "Fashion Accessories", Icons.Default.School),
            ShopItem("cap", "Retro Washed Sports Cap", 150, "R15.00", "Breathable 6-panel strapback unstructured hat featuring embroidered vintage letter crest.", "Fashion Accessories", Icons.Default.School),
            ShopItem("scarf", "Double-Knit Academic Scarf", 180, "R18.00", "Traditional school stripes scarf made of premium warm acrylic yarn.", "Fashion Accessories", Icons.Default.School)
        )
    }

    val stalls = remember {
        listOf(
            Triple("Fruits & Snacks", "Fruits & Snacks", Icons.Default.Favorite),
            Triple("Coffee Bar", "Coffee Bar", Icons.Default.LocalCafe),
            Triple("Lunch Cafe", "Lunch Cafe", Icons.Default.Restaurant),
            Triple("School Gear", "School Accessories", Icons.Default.Book),
            Triple("Tech Depot", "Tech Stuff", Icons.Default.Smartphone),
            Triple("Varsity Wear", "Fashion Accessories", Icons.Default.School)
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1B1F))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CAMPUS SHOPPING PROMENADE",
                        color = Color(0xFF1C1B1F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F2FA))
                    .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDashboard = viewModel.currentScreen == Screen.Dashboard || viewModel.currentScreen == Screen.Geomap || viewModel.currentScreen == Screen.AttendanceVerification
                val isShop = viewModel.currentScreen == Screen.LunchShop
                val isSettings = viewModel.currentScreen == Screen.Settings

                // Button 1: Home
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.Dashboard }
                        .testTag("dashboard_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDashboard) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Dashboard",
                                tint = if (isDashboard) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Home",
                            fontSize = 11.sp,
                            fontWeight = if (isDashboard) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDashboard) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }

                // Button 2: Shop
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.LunchShop }
                        .testTag("shop_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isShop) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Shop",
                                tint = if (isShop) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Shop",
                            fontSize = 11.sp,
                            fontWeight = if (isShop) FontWeight.Bold else FontWeight.Medium,
                            color = if (isShop) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }

                // Button 3: Settings
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.Settings }
                        .testTag("settings_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSettings) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (isSettings) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Settings",
                            fontSize = 11.sp,
                            fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSettings) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Real-time Clock display
            TimeBar()

            // Wallet balance display
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AVAILABLE TO SPEND",
                            color = Color(0xFF49454F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${profile?.points ?: 0} PTS",
                            color = Color(0xFF21005D),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = "Cash", tint = Color(0xFF22C55E), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cafeteria Balance: R${String.format("%.2f", (profile?.points ?: 0) * 0.10)}",
                                color = Color(0xFF22C55E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEADDFF), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "100 PTS = R10.00",
                            color = Color(0xFF21005D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // REDEEMED ACTIVE VOUCHERS WALLET IN SHOP TAB
            val activeVouchers = vouchers.filter { !it.isUsed && (it.studentEmail.isEmpty() || it.studentEmail == (profile?.email ?: "")) }
            if (activeVouchers.isNotEmpty()) {
                Text(
                    text = "My Digital Voucher Wallet (${activeVouchers.size})",
                    color = Color(0xFF1C1B1F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                activeVouchers.forEach { voucher ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEADDFF), RoundedCornerShape(16.dp))
                            .clickable { viewModel.activeVoucherToShow = voucher }
                            .testTag("shop_voucher_${voucher.qrCode}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = "Ticket",
                                    tint = Color(0xFF21005D),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = voucher.name,
                                        color = Color(0xFF1C1B1F),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Voucher Code: ${voucher.qrCode}",
                                        color = Color(0xFF49454F),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = voucher.localValue,
                                    color = Color(0xFF22C55E),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap to Scan",
                                    color = Color(0xFF21005D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Select Shopping Stall",
                color = Color(0xFF1C1B1F),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Dynamic Row of Shopping Stalls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                stalls.forEach { (shortName, categoryName, icon) ->
                    val isSelected = selectedCategory == categoryName
                    Card(
                        onClick = { selectedCategory = categoryName },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF21005D) else Color(0xFFF3F3F3)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(140.dp)
                            .height(80.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) Color(0xFF21005D) else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = shortName,
                                tint = if (isSelected) Color.White else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = shortName,
                                color = if (isSelected) Color.White else Color(0xFF1C1B1F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Text(
                text = "$selectedCategory Offerings",
                color = Color(0xFF1C1B1F),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Dynamic grid layout for delicious items
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val filteredItems = shopItems.filter { it.category == selectedCategory }
                filteredItems.forEach { item ->
                    val isAffordable = (profile?.points ?: 0) >= item.costPoints

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // High Fidelity Shop Item Image/Illustration Container
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFFEADDFF),
                                                        Color(0xFF21005D).copy(alpha = 0.15f)
                                                    )
                                                )
                                            )
                                            .border(1.dp, Color(0xFF21005D).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Colored badge to represent item status
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .size(8.dp)
                                                .background(Color(0xFF22C55E), CircleShape)
                                        )
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.name,
                                            tint = Color(0xFF21005D),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = item.name,
                                            color = Color(0xFF1C1B1F),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "${item.costPoints} PTS (Value ${item.localValue})",
                                            color = Color(0xFF21005D),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.selectedShopItem = item },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAffordable) Color(0xFF21005D) else Color(0xFFF3F3F3),
                                        contentColor = if (isAffordable) Color.White else Color(0xFFCAC4D0)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("buy_${item.id}_button"),
                                    enabled = isAffordable
                                ) {
                                    Text(
                                        text = if (isAffordable) "Select" else "Locked",
                                        color = if (isAffordable) Color.White else Color(0xFFCAC4D0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = item.description,
                                color = Color(0xFF49454F),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Vouchers listing at the bottom
            val scannedVouchers = vouchers.filter { it.isUsed }
            if (scannedVouchers.isNotEmpty()) {
                Text(
                    text = "Transaction History / Redeemed Coupons",
                    color = Color(0xFF1C1B1F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )

                scannedVouchers.forEach { voucher ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F3)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(voucher.name, color = Color(0xFF1C1B1F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Ref: ${voucher.qrCode}", color = Color(0xFF49454F), fontSize = 11.sp)
                            }
                            Text(
                                text = "REDEEMED",
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Interactive Checkout & Hardware NFC Confirmation dialog (Section 4)
    viewModel.selectedShopItem?.let { item ->
        var showNfcSimulationScreen by remember { mutableStateOf(false) }

        if (!showNfcSimulationScreen) {
            AlertDialog(
                onDismissRequest = { viewModel.selectedShopItem = null },
                title = {
                    Text("Rewards Checkout", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Confirm spending reward points for:", color = Color(0xFF1C1B1F))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8DEF8), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold)
                            Text("${item.costPoints} PTS", color = Color(0xFF21005D), fontWeight = FontWeight.Black)
                        }

                        // HARDWARE NFC DETECTION TOGGLE
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Phone NFC Hardware", color = Color(0xFF1C1B1F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = if (viewModel.deviceHasNfc) "Direct NFC tap to card reader" else "Generate 2D QR screen barcode",
                                            color = Color(0xFF49454F),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Switch(
                                        checked = viewModel.deviceHasNfc,
                                        onCheckedChange = { viewModel.deviceHasNfc = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF21005D),
                                            checkedTrackColor = Color(0xFFEADDFF)
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.deviceHasNfc) {
                                showNfcSimulationScreen = true
                            } else {
                                // Direct instant voucher generation
                                viewModel.purchaseLunchItem(item)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("confirm_checkout_button")
                    ) {
                        Text(
                            text = if (viewModel.deviceHasNfc) "Proceed with NFC Payment" else "Deduct Points & Create Voucher",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.selectedShopItem = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color(0xFFEF4444))
                    }
                }
            )
        } else {
            // NFC HARDWARE TAP PAY SCREEN
            AlertDialog(
                onDismissRequest = { viewModel.selectedShopItem = null },
                title = {
                    Text(
                        text = "NFC Payment Terminal",
                        color = Color(0xFF1C1B1F),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("TAP PHONE ON CARD READER", color = Color(0xFF49454F), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Pulse animation placeholder
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color(0xFFEADDFF), CircleShape)
                                .border(2.dp, Color(0xFF21005D), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = "NFC Pulse",
                                tint = Color(0xFF21005D),
                                modifier = Modifier.size(64.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = item.name,
                            color = Color(0xFF1C1B1F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Amount to Deduct: ${item.costPoints} PTS",
                            color = Color(0xFF21005D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hold your phone close to the cafeteria cashier register till card reader.",
                            color = Color(0xFF49454F),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.purchaseLunchItem(item)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("nfc_tap_button")
                    ) {
                        Text("Simulate NFC Card Reader Contact", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.selectedShopItem = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Checkout", color = Color(0xFFEF4444))
                    }
                }
            )
        }
    }

    // Interactive Voucher scan overlay inside Shop Screen
    viewModel.activeVoucherToShow?.let { voucher ->
        AlertDialog(
            onDismissRequest = { viewModel.activeVoucherToShow = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp),
            title = {
                Text(
                    text = "Voucher QR Coupon",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1C1B1F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = voucher.name,
                        color = Color(0xFF1C1B1F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Value: ${voucher.localValue}",
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw QR matrix simulation
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cellSize = size.width / 8f
                            for (i in 0 until 8) {
                                for (j in 0 until 8) {
                                    val isAnchor = (i < 3 && j < 3) || (i > 4 && j < 3) || (i < 3 && j > 4)
                                    val isRandomBlock = (i + j) % 3 == 0 || (i * j) % 2 == 1
                                    if (isAnchor || isRandomBlock) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(i * cellSize, j * cellSize),
                                            size = androidx.compose.ui.geometry.Size(cellSize - 2, cellSize - 2)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = voucher.qrCode,
                        color = Color(0xFF1C1B1F),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFFF3F3F3), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Simulate presenting this voucher at the cafeteria checkout scanner.",
                        color = Color(0xFF49454F),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.scanVoucherInWallet(voucher) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("scan_shop_voucher_button")
                ) {
                    Text("Scan Screen Voucher at Till Register", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.activeVoucherToShow = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color(0xFFEF4444))
                }
            }
        )
    }
}

// Time Bar Component
@Composable
fun TimeBar() {
    var timeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = java.text.SimpleDateFormat("EEEE, dd MMM yyyy HH:mm:ss", java.util.Locale.getDefault())
            timeString = sdf.format(java.util.Date())
            delay(1000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21005D)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Time icon",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "South African Standard Time (SAST)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = timeString,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// 6. Settings, Language, Helpline Services & Feedback Screen
@Composable
fun SettingsScreen(viewModel: UniPointsViewModel) {
    val languages = listOf(
        "English", "isiZulu", "isiXhosa", "Afrikaans", 
        "Sepedi", "Setswana", "Sesotho", "Xitsonga", 
        "siSwati", "Tshivenda", "isiNdebele"
    )

    var concernText by remember { mutableStateOf("") }
    var suggestionText by remember { mutableStateOf("") }
    var showSuccessToast by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1B1F))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CAMPUS SETTINGS & SUPPORT",
                        color = Color(0xFF1C1B1F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F2FA))
                    .border(width = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDashboard = viewModel.currentScreen == Screen.Dashboard || viewModel.currentScreen == Screen.Geomap || viewModel.currentScreen == Screen.AttendanceVerification
                val isShop = viewModel.currentScreen == Screen.LunchShop
                val isSettings = viewModel.currentScreen == Screen.Settings

                // Button 1: Home
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.Dashboard }
                        .testTag("dashboard_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDashboard) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Dashboard",
                                tint = if (isDashboard) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Home",
                            fontSize = 11.sp,
                            fontWeight = if (isDashboard) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDashboard) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }

                // Button 2: Lunch Shop
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.LunchShop }
                        .testTag("shop_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isShop) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Lunch Shop",
                                tint = if (isShop) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Lunch Shop",
                            fontSize = 11.sp,
                            fontWeight = if (isShop) FontWeight.Bold else FontWeight.Medium,
                            color = if (isShop) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }

                // Button 3: Settings
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.currentScreen = Screen.Settings }
                        .testTag("settings_nav"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSettings) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (isSettings) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Settings",
                            fontSize = 11.sp,
                            fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSettings) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Real-time Clock display
            TimeBar()

            // SECTION: MY STUDENT PROFILE DETAILS
            val profile by viewModel.studentProfile.collectAsStateWithLifecycle()
            var showEditProfileDialog by remember { mutableStateOf(false) }

            if (showEditProfileDialog && profile != null) {
                EditProfileDialog(
                    profile = profile!!,
                    onDismiss = { showEditProfileDialog = false },
                    onSave = { newName, newAvatarIndex ->
                        viewModel.viewModelScope.launch {
                            viewModel.repository.saveProfile(
                                profile!!.copy(name = newName, avatarIndex = newAvatarIndex)
                            )
                            showEditProfileDialog = false
                        }
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEADDFF), RoundedCornerShape(20.dp))
                    .testTag("my_profile_details_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(avatarIndex = profile?.avatarIndex ?: 0, size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile?.name ?: "Student",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1B1F)
                                )
                                Text(
                                    text = profile?.email ?: "student@varsity.ac.za",
                                    fontSize = 12.sp,
                                    color = Color(0xFF49454F)
                                )
                            }
                        }
                        
                        Button(
                            onClick = { showEditProfileDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEADDFF),
                                contentColor = Color(0xFF21005D)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("edit_profile_details_button")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SECTION 1: OFFICIAL SOUTH AFRICAN LANGUAGES
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = "Lang", tint = Color(0xFF21005D))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Official South African Languages",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current: ${viewModel.selectedLanguage}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { lang ->
                            val isSelected = viewModel.selectedLanguage == lang
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFEADDFF) else Color(0xFFF3F3F3))
                                    .clickable { viewModel.selectedLanguage = lang }
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF21005D) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = lang,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF21005D) else Color(0xFF1C1B1F)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: UNIVERSITY HELPLINES
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ContactPhone, contentDescription = "Help", tint = Color(0xFF21005D))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "University Helplines & Contacts",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Campus Emergency Security", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                            Text("+27 (0)11 555 1111", fontSize = 11.sp, color = Color(0xFF49454F))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Student Wellness Helpline (SADAG)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                            Text("0800 21 22 23 (Toll-Free 24/7)", fontSize = 11.sp, color = Color(0xFF49454F))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = Color(0xFF21005D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Academic IT & SSO Support Desk", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                            Text("helpdesk@uniearn.ac.za", fontSize = 11.sp, color = Color(0xFF49454F))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF21005D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("General Inquiries & Points Services", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                            Text("info@uniearn.ac.za", fontSize = 11.sp, color = Color(0xFF49454F))
                        }
                    }
                }
            }

            // SECTION 3: APP FEEDBACK CENTER
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.RateReview, contentDescription = "Review", tint = Color(0xFF21005D))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UniEarn Concerns & Suggestions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Report app bugs and offer helpful suggestions to improve the application.",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = concernText,
                        onValueChange = { concernText = it },
                        label = { Text("What concern do you have regarding the app?") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = suggestionText,
                        onValueChange = { suggestionText = it },
                        label = { Text("Offer suggestions to improve UniEarn") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF21005D),
                            unfocusedBorderColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (concernText.isNotBlank() || suggestionText.isNotBlank()) {
                                viewModel.submittedConcerns.add(Pair(concernText, suggestionText))
                                concernText = ""
                                suggestionText = ""
                                showSuccessToast = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = concernText.isNotBlank() || suggestionText.isNotBlank()
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Feedback to Helpdesk", fontWeight = FontWeight.Bold)
                    }

                    if (showSuccessToast) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF22C55E))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Feedback Submitted! Thank you for supporting the university.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1D192B)
                                )
                            }
                        }
                    }
                }
            }

            // LIST OF TICKETS
            if (viewModel.submittedConcerns.isNotEmpty()) {
                Text(
                    text = "YOUR SUBMITTED FEEDBACK TICKETS (${viewModel.submittedConcerns.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F),
                    letterSpacing = 0.5.sp
                )

                viewModel.submittedConcerns.forEachIndexed { idx, pair ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCF8FD)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Ticket #${idx + 1} - Status: Pending Review", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF21005D))
                            Spacer(modifier = Modifier.height(4.dp))
                            if (pair.first.isNotBlank()) {
                                Text("Concern: ${pair.first}", fontSize = 12.sp, color = Color(0xFF1C1B1F))
                            }
                            if (pair.second.isNotBlank()) {
                                Text("Suggestion: ${pair.second}", fontSize = 12.sp, color = Color(0xFF49454F))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AvatarImage(avatarIndex: Int, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val (gradient, emoji) = remember(avatarIndex) {
        when (avatarIndex) {
            0 -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5))) to "🎓"
            1 -> Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF7E22CE))) to "💻"
            2 -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFFDB2777))) to "🎨"
            3 -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF0891B2))) to "🧬"
            4 -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))) to "☕"
            else -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF047857))) to "🏆"
        }
    }
    
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = (size.value * 0.5f).sp
        )
    }
}

@Composable
fun EditProfileDialog(
    profile: StudentProfile,
    onDismiss: () -> Unit,
    onSave: (newName: String, newAvatarIndex: Int) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var selectedAvatarIndex by remember { mutableStateOf(profile.avatarIndex) }
    
    val avatarChoices = listOf(
        "🎓 Scholar" to 0,
        "💻 Techie" to 1,
        "🎨 Artist" to 2,
        "🧬 AI Lab" to 3,
        "☕ Espresso" to 4,
        "🏆 Champ" to 5
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Student Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1C1B1F)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarImage(avatarIndex = selectedAvatarIndex, size = 80.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap a profile picture to choose it",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F),
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "SELECT NEW AVATAR PICTURE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF21005D),
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    avatarChoices.forEach { (label, index) ->
                        val isSelected = selectedAvatarIndex == index
                        Card(
                            onClick = { selectedAvatarIndex = index },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEADDFF) else Color(0xFFF3F3F3)
                            ),
                            modifier = Modifier
                                .width(90.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Color(0xFF21005D) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AvatarImage(avatarIndex = index, size = 36.dp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label.split(" ")[1],
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF21005D) else Color(0xFF1C1B1F)
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF21005D),
                        focusedLabelColor = Color(0xFF21005D)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input")
                )

                OutlinedTextField(
                    value = profile.email,
                    onValueChange = {},
                    label = { Text("SSO Registered Email") },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Color(0xFFCAC4D0).copy(alpha = 0.5f),
                        disabledLabelColor = Color(0xFF49454F).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedAvatarIndex) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D)),
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save Changes", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFEF4444))
            }
        }
    )
}
