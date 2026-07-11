package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "student_profile")
data class StudentProfile(
    @PrimaryKey val email: String,
    val name: String,
    val points: Int,
    val isLoggedIn: Boolean = false,
    val avatarIndex: Int = 0,
    val cs402Attended: Int = 8,
    val cs402Total: Int = 10,
    val ai510Attended: Int = 7,
    val ai510Total: Int = 10,
    val cs312Attended: Int = 9,
    val cs312Total: Int = 10,
    val hum102Attended: Int = 6,
    val hum102Total: Int = 10
)

@Entity(tableName = "university_classes")
data class UniversityClass(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val courseName: String,
    val classroom: String,
    val latitude: Double,
    val longitude: Double,
    val startTime: String, // e.g., "09:00" or "14:15"
    val endTime: String,   // e.g., "10:30" or "15:45"
    val dayOfWeek: String, // e.g., "Monday"
    val status: String = "PENDING" // "PENDING", "ATTENDED", "LATE", "ABSENT"
)

@Entity(tableName = "redeemed_vouchers")
data class RedeemedVoucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val costPoints: Int,
    val localValue: String,
    val qrCode: String,
    val redeemTimestamp: Long,
    val isUsed: Boolean = false,
    val studentEmail: String = ""
)

@Dao
interface UniPointsDao {
    @Query("SELECT * FROM student_profile WHERE isLoggedIn = 1 LIMIT 1")
    fun getStudentProfile(): Flow<StudentProfile?>

    @Query("SELECT * FROM student_profile")
    fun getAllStudentProfiles(): Flow<List<StudentProfile>>

    @Query("SELECT * FROM student_profile WHERE email = :email LIMIT 1")
    suspend fun getProfileByEmail(email: String): StudentProfile?

    @Query("UPDATE student_profile SET isLoggedIn = 0")
    suspend fun logoutAllProfiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentProfile(profile: StudentProfile)

    @Update
    suspend fun updateStudentProfile(profile: StudentProfile)

    @Query("SELECT * FROM university_classes")
    fun getAllClasses(): Flow<List<UniversityClass>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<UniversityClass>)

    @Update
    suspend fun updateClass(uniClass: UniversityClass)

    @Query("SELECT * FROM redeemed_vouchers ORDER BY redeemTimestamp DESC")
    fun getAllVouchers(): Flow<List<RedeemedVoucher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: RedeemedVoucher)

    @Update
    suspend fun updateVoucher(voucher: RedeemedVoucher)

    @Query("DELETE FROM university_classes")
    suspend fun clearClasses()

    @Query("DELETE FROM student_profile")
    suspend fun clearProfile()

    @Query("DELETE FROM redeemed_vouchers")
    suspend fun clearVouchers()
}

@Database(entities = [StudentProfile::class, UniversityClass::class, RedeemedVoucher::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): UniPointsDao
}

class UniPointsRepository(private val dao: UniPointsDao) {
    val studentProfile: Flow<StudentProfile?> = dao.getStudentProfile()
    val allStudentProfiles: Flow<List<StudentProfile>> = dao.getAllStudentProfiles()
    val allClasses: Flow<List<UniversityClass>> = dao.getAllClasses()
    val allVouchers: Flow<List<RedeemedVoucher>> = dao.getAllVouchers()

    suspend fun saveProfile(profile: StudentProfile) = dao.insertStudentProfile(profile)
    suspend fun updateProfile(profile: StudentProfile) = dao.updateStudentProfile(profile)
    suspend fun getProfileByEmail(email: String): StudentProfile? = dao.getProfileByEmail(email)
    suspend fun logoutAllProfiles() = dao.logoutAllProfiles()
    suspend fun insertClasses(classes: List<UniversityClass>) = dao.insertClasses(classes)
    suspend fun updateClass(uniClass: UniversityClass) = dao.updateClass(uniClass)
    suspend fun addVoucher(voucher: RedeemedVoucher) = dao.insertVoucher(voucher)
    suspend fun updateVoucher(voucher: RedeemedVoucher) = dao.updateVoucher(voucher)
    suspend fun clearAll() {
        dao.clearClasses()
        dao.clearProfile()
        dao.clearVouchers()
    }
}
