package com.tutordiary.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM tutor_profile WHERE id = 1")
    fun observe(): Flow<TutorProfileEntity?>

    @Query("SELECT * FROM tutor_profile WHERE id = 1")
    suspend fun get(): TutorProfileEntity?

    @Upsert
    suspend fun upsert(profile: TutorProfileEntity)

    @Query("SELECT * FROM tutor_profile")
    suspend fun all(): List<TutorProfileEntity>

    @Insert
    suspend fun insertAll(items: List<TutorProfileEntity>)

    @Query("DELETE FROM tutor_profile")
    suspend fun clear()
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun get(id: Long): BatchEntity?

    @Insert
    suspend fun insert(batch: BatchEntity): Long

    @Update
    suspend fun update(batch: BatchEntity)

    @Query("SELECT * FROM batches")
    suspend fun all(): List<BatchEntity>

    @Insert
    suspend fun insertAll(items: List<BatchEntity>)

    @Query("DELETE FROM batches")
    suspend fun clear()
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observe(id: Long): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun get(id: Long): StudentEntity?

    @Query("SELECT * FROM students WHERE batchId = :batchId AND archivedOn IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun activeInBatch(batchId: Long): List<StudentEntity>

    @Query("SELECT COUNT(*) FROM students WHERE batchId = :batchId AND archivedOn IS NULL")
    suspend fun countActiveInBatch(batchId: Long): Int

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Update
    suspend fun update(student: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM students")
    suspend fun all(): List<StudentEntity>

    @Insert
    suspend fun insertAll(items: List<StudentEntity>)

    @Query("DELETE FROM students")
    suspend fun clear()
}

@Dao
interface ChargeDao {
    @Query("SELECT * FROM charges ORDER BY month")
    fun observeAll(): Flow<List<ChargeEntity>>

    @Query("SELECT * FROM charges WHERE studentId = :studentId ORDER BY month")
    suspend fun forStudent(studentId: Long): List<ChargeEntity>

    @Query("SELECT month FROM charges WHERE studentId = :studentId")
    suspend fun monthsForStudent(studentId: Long): List<Int>

    /** IGNORE + the unique (studentId, month) index = a month can never be charged twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(charges: List<ChargeEntity>): List<Long>

    @Query("DELETE FROM charges WHERE studentId = :studentId AND month < :month")
    suspend fun deleteBefore(studentId: Long, month: Int)

    @Query("SELECT * FROM charges")
    suspend fun all(): List<ChargeEntity>

    @Insert
    suspend fun insertAll(items: List<ChargeEntity>)

    @Query("DELETE FROM charges")
    suspend fun clear()
}

@Dao
interface WaiverDao {
    @Query("SELECT * FROM waivers ORDER BY month")
    fun observeAll(): Flow<List<WaiverEntity>>

    @Insert
    suspend fun insert(waiver: WaiverEntity): Long

    @Query("DELETE FROM waivers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM waivers WHERE studentId = :studentId AND month = :month")
    suspend fun countFor(studentId: Long, month: Int): Int

    @Query("DELETE FROM waivers WHERE studentId = :studentId AND month < :month")
    suspend fun deleteBefore(studentId: Long, month: Int)

    @Query("SELECT * FROM waivers")
    suspend fun all(): List<WaiverEntity>

    @Insert
    suspend fun insertAll(items: List<WaiverEntity>)

    @Query("DELETE FROM waivers")
    suspend fun clear()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun get(id: Long): PaymentEntity?

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM payments")
    suspend fun all(): List<PaymentEntity>

    @Insert
    suspend fun insertAll(items: List<PaymentEntity>)

    @Query("DELETE FROM payments")
    suspend fun clear()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE batchId = :batchId AND date = :date")
    suspend fun forBatchOnDate(batchId: Long, date: Long): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date BETWEEN :from AND :to")
    fun observeForStudent(studentId: Long, from: Long, to: Long): Flow<List<AttendanceEntity>>

    @Upsert
    suspend fun upsertAll(items: List<AttendanceEntity>)

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date = :date")
    suspend fun find(studentId: Long, date: Long): AttendanceEntity?

    @Query("SELECT * FROM attendance")
    suspend fun all(): List<AttendanceEntity>

    @Insert
    suspend fun insertAll(items: List<AttendanceEntity>)

    @Query("DELETE FROM attendance")
    suspend fun clear()
}
