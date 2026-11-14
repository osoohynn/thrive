package com.thrive.pleos.driving.storage

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thrive.pleos.driving.location.TrackedFix

internal enum class SessionStatus { Recording, Interrupted, Completed }

/** Planned route values and measured elapsed time are intentionally distinct. */
@Entity(tableName = "drive_sessions", indices = [Index(value = ["activeSlot"], unique = true)])
internal data class SavedDrive(
    @PrimaryKey val id: String,
    val source: String = "DEMO",
    val mode: String,
    val goal: String,
    val route: String,
    val destination: String,
    val plannedMinutes: Int,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val elapsedMs: Long = 0,
    val status: String = SessionStatus.Recording.name,
    val activeSlot: Int? = 1,
    val feeling: String = "",
    @ColumnInfo(defaultValue = "'UNKNOWN'") val environment: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "NULL") val destinationLatitude: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val destinationLongitude: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val distanceMeters: Double? = null,
    @ColumnInfo(defaultValue = "0") val acceptedFixes: Int = 0,
    @ColumnInfo(defaultValue = "0") val rejectedFixes: Int = 0,
    @ColumnInfo(defaultValue = "0") val gapCount: Int = 0,
    @ColumnInfo(defaultValue = "'위치 미수신'") val locationMessage: String = "위치 미수신",
)

@Entity(tableName = "location_samples", foreignKeys = [ForeignKey(
    entity = SavedDrive::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("sessionId")])
internal data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val latitude: Double?, val longitude: Double?, val sourceTimestampMs: Long?,
    val receivedElapsedMs: Long, val receivedEpochMs: Long, val confidence: Double?, val quality: String,
)

@Dao
internal abstract class DriveDao {
    @Query("SELECT * FROM drive_sessions ORDER BY startedAtEpochMs DESC")
    abstract fun observeAll(): Flow<List<SavedDrive>>

    @Query("SELECT * FROM drive_sessions WHERE id = :id")
    abstract suspend fun find(id: String): SavedDrive?

    @Query("SELECT * FROM drive_sessions WHERE activeSlot = 1 LIMIT 1")
    abstract suspend fun active(): SavedDrive?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(record: SavedDrive)

    @Transaction
    open suspend fun startOnce(proposed: SavedDrive): SavedDrive {
        val current = active()
        if (current != null) return current
        insert(proposed)
        return proposed
    }

    @Query("UPDATE drive_sessions SET elapsedMs = MAX(elapsedMs, :elapsedMs) WHERE id = :id AND status = 'Recording'")
    abstract suspend fun checkpoint(id: String, elapsedMs: Long): Int

    @Query("UPDATE drive_sessions SET status = 'Interrupted' WHERE status = 'Recording'")
    abstract suspend fun interruptOpenSessions(): Int

    @Query("UPDATE drive_sessions SET status = 'Recording' WHERE id = :id AND status = 'Interrupted'")
    abstract suspend fun resume(id: String): Int

    @Query("UPDATE drive_sessions SET status = 'Completed', activeSlot = NULL, endedAtEpochMs = :endedAt, elapsedMs = MAX(elapsedMs, :elapsedMs) WHERE id = :id AND activeSlot = 1")
    abstract suspend fun finishOnce(id: String, endedAt: Long, elapsedMs: Long): Int

    @Insert
    abstract suspend fun insertLocation(sample: SavedLocation)

    @Query("UPDATE drive_sessions SET distanceMeters = :distance, acceptedFixes = :accepted, rejectedFixes = :rejected, gapCount = :gaps, locationMessage = :message WHERE id = :id AND status = 'Recording' AND source = 'SDK'")
    abstract suspend fun updateLocationSummary(id: String, distance: Double?, accepted: Int, rejected: Int, gaps: Int, message: String)

    @Query("UPDATE drive_sessions SET gapCount = gapCount + 1 WHERE id = :id AND status = 'Recording' AND source = 'SDK'")
    abstract suspend fun markRecordingGap(id: String)

    @Query("UPDATE drive_sessions SET locationMessage = :message WHERE id = :id AND status = 'Recording' AND source = 'SDK'")
    abstract suspend fun updateLocationMessage(id: String, message: String)

    @Transaction
    open suspend fun appendLocation(sessionId: String, value: TrackedFix, message: String): Boolean {
        val session = find(sessionId) ?: return false
        if (session.status != SessionStatus.Recording.name || session.source != "SDK") return false
        val fix = value.fix
        insertLocation(SavedLocation(sessionId = sessionId, latitude = fix.latitude?.takeIf { it.isFinite() },
            longitude = fix.longitude?.takeIf { it.isFinite() }, sourceTimestampMs = fix.sourceTimestampMs,
            receivedElapsedMs = fix.receivedElapsedMs, receivedEpochMs = fix.receivedEpochMs,
            confidence = fix.confidence?.takeIf { it.isFinite() }, quality = value.quality.name))
        updateLocationSummary(sessionId, value.summary.distanceMeters, value.summary.accepted, value.summary.rejected, value.summary.gaps, message)
        return true
    }

    @Query("UPDATE drive_sessions SET feeling = :feeling WHERE id = :id")
    abstract suspend fun setFeeling(id: String, feeling: String)

    @Query("DELETE FROM drive_sessions WHERE id = :id AND activeSlot IS NULL")
    abstract suspend fun deleteFinished(id: String): Int

    @Query("DELETE FROM drive_sessions WHERE activeSlot IS NULL")
    abstract suspend fun deleteAllFinished(): Int
}

@Database(entities = [SavedDrive::class, SavedLocation::class], version = 2, exportSchema = true)
internal abstract class DriveDatabase : RoomDatabase() {
    abstract fun drives(): DriveDao

    companion object {
        @Volatile private var instance: DriveDatabase? = null

        fun get(context: Context): DriveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DriveDatabase::class.java, "driving.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN environment TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN destinationLatitude REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN destinationLongitude REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN distanceMeters REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN acceptedFixes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN rejectedFixes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN gapCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN locationMessage TEXT NOT NULL DEFAULT '위치 미수신'")
        db.execSQL("CREATE TABLE IF NOT EXISTS location_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, latitude REAL, longitude REAL, sourceTimestampMs INTEGER, receivedElapsedMs INTEGER NOT NULL, receivedEpochMs INTEGER NOT NULL, confidence REAL, quality TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES drive_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_location_samples_sessionId ON location_samples(sessionId)")
    }
}
