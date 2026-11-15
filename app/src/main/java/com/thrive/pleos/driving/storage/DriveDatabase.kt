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
    @ColumnInfo(defaultValue = "NULL") val estimatedSeconds: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val plannedDistanceMeters: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val routeOption: String? = null,
    @ColumnInfo(defaultValue = "NULL") val practiceStopName: String? = null,
    @ColumnInfo(defaultValue = "NULL") val practiceStopLatitude: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val practiceStopLongitude: Double? = null,
    @ColumnInfo(defaultValue = "0") val practiceReturn: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val practiceMaxMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val droppedNavigationEvents: Int = 0,
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

@Entity(tableName = "navigation_events", foreignKeys = [ForeignKey(
    entity = SavedDrive::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("sessionId"), Index(value = ["eventKey"], unique = true)])
internal data class SavedNavigationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventKey: String,
    val sessionId: String,
    val kind: String,
    val source: String,
    val elapsedMs: Long,
    val receivedEpochMs: Long,
    val detail: String,
    val turnType: String? = null,
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

    @Query("UPDATE drive_sessions SET status = 'Interrupted' WHERE id = :id AND status = 'Recording'")
    abstract suspend fun interrupt(id: String): Int

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

    @Query("SELECT * FROM navigation_events WHERE sessionId = :id ORDER BY elapsedMs, id")
    abstract fun observeNavigationEvents(id: String): Flow<List<SavedNavigationEvent>>

    @Query("SELECT * FROM navigation_events WHERE sessionId = :id ORDER BY elapsedMs, id")
    abstract suspend fun navigationEvents(id: String): List<SavedNavigationEvent>

    @Query("SELECT COUNT(*) FROM navigation_events WHERE sessionId = :id")
    abstract suspend fun navigationEventCount(id: String): Int

    @Query("SELECT COUNT(*) FROM navigation_events WHERE eventKey = :key")
    abstract suspend fun navigationEventExists(key: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNavigationEvent(event: SavedNavigationEvent): Long

    @Query("UPDATE drive_sessions SET droppedNavigationEvents = droppedNavigationEvents + :count WHERE id = :id")
    abstract suspend fun markNavigationLoss(id: String, count: Int)

    @Transaction
    open suspend fun appendNavigationEvents(id: String, events: List<SavedNavigationEvent>, lost: Int) {
        val record = find(id) ?: error("기록을 찾을 수 없어요.")
        check(record.source == "SDK" && record.status == "Recording")
        var count = navigationEventCount(id)
        var omitted = lost
        for (event in events) {
            check(event.sessionId == id && event.elapsedMs >= 0 && event.detail.length <= 200)
            if (navigationEventExists(event.eventKey) != 0) continue
            if (count >= 2_000) omitted++
            else if (insertNavigationEvent(event) != -1L) count++
        }
        if (omitted > 0) markNavigationLoss(id, omitted)
    }

    @Query("UPDATE drive_sessions SET feeling = :feeling WHERE id = :id")
    abstract suspend fun setFeeling(id: String, feeling: String)

    @Query("DELETE FROM drive_sessions WHERE id = :id AND activeSlot IS NULL")
    abstract suspend fun deleteFinished(id: String): Int

    @Query("DELETE FROM drive_sessions WHERE activeSlot IS NULL")
    abstract suspend fun deleteAllFinished(): Int
}

@Database(entities = [SavedDrive::class, SavedLocation::class, SavedNavigationEvent::class], version = 4, exportSchema = true)
internal abstract class DriveDatabase : RoomDatabase() {
    abstract fun drives(): DriveDao

    companion object {
        @Volatile private var instance: DriveDatabase? = null

        fun get(context: Context): DriveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DriveDatabase::class.java, "driving.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN estimatedSeconds INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN plannedDistanceMeters REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN routeOption TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN practiceStopName TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN practiceStopLatitude REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN practiceStopLongitude REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN practiceReturn INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN practiceMaxMinutes INTEGER DEFAULT NULL")
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN droppedNavigationEvents INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS navigation_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, eventKey TEXT NOT NULL, sessionId TEXT NOT NULL, kind TEXT NOT NULL, source TEXT NOT NULL, elapsedMs INTEGER NOT NULL, receivedEpochMs INTEGER NOT NULL, detail TEXT NOT NULL, turnType TEXT, FOREIGN KEY(sessionId) REFERENCES drive_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_navigation_events_sessionId ON navigation_events(sessionId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_navigation_events_eventKey ON navigation_events(eventKey)")
    }
}
