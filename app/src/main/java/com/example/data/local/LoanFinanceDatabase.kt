package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.domain.model.DocumentType
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.LoanProductType
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentAdjustmentType
import com.example.domain.model.PaymentMode
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.PreClosureStatus
import com.example.domain.model.RepaymentFrequency
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole

class FinanceTypeConverters {
  @TypeConverter fun fromUserRole(v: UserRole): String = v.name
  @TypeConverter fun toUserRole(v: String): UserRole = UserRole.valueOf(v)

  @TypeConverter fun fromFrequency(v: RepaymentFrequency): String = v.name
  @TypeConverter fun toFrequency(v: String): RepaymentFrequency = RepaymentFrequency.valueOf(v)

  @TypeConverter fun fromProductType(v: LoanProductType): String = v.name
  @TypeConverter fun toProductType(v: String): LoanProductType = LoanProductType.valueOf(v)

  @TypeConverter fun fromLoanStatus(v: LoanStatus): String = v.name
  @TypeConverter fun toLoanStatus(v: String): LoanStatus = LoanStatus.valueOf(v)

  @TypeConverter fun fromInstallmentStatus(v: InstallmentStatus): String = v.name
  @TypeConverter fun toInstallmentStatus(v: String): InstallmentStatus = InstallmentStatus.valueOf(v)

  @TypeConverter fun fromPaymentMode(v: PaymentMode): String = v.name
  @TypeConverter fun toPaymentMode(v: String): PaymentMode = PaymentMode.valueOf(v)

  @TypeConverter fun fromPaymentType(v: PaymentType): String = v.name
  @TypeConverter fun toPaymentType(v: String): PaymentType = PaymentType.valueOf(v)

  @TypeConverter fun fromSyncStatus(v: SyncStatus): String = v.name
  @TypeConverter fun toSyncStatus(v: String): SyncStatus =
    if (v == "LOCAL_PENDING_SYNC") {
      SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC
    } else {
      runCatching { SyncStatus.valueOf(v) }.getOrDefault(SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC)
    }

  @TypeConverter fun fromPaymentRecordStatus(v: PaymentRecordStatus): String = v.name
  @TypeConverter fun toPaymentRecordStatus(v: String): PaymentRecordStatus =
    runCatching { PaymentRecordStatus.valueOf(v) }.getOrDefault(PaymentRecordStatus.ACTIVE)

  @TypeConverter fun fromPaymentAdjustmentType(v: PaymentAdjustmentType): String = v.name
  @TypeConverter fun toPaymentAdjustmentType(v: String): PaymentAdjustmentType =
    runCatching { PaymentAdjustmentType.valueOf(v) }.getOrDefault(PaymentAdjustmentType.REVERSAL)

  @TypeConverter fun fromPreClosureStatus(v: PreClosureStatus): String = v.name
  @TypeConverter fun toPreClosureStatus(v: String): PreClosureStatus = PreClosureStatus.valueOf(v)

  @TypeConverter fun fromDocumentType(v: DocumentType): String = v.name
  @TypeConverter fun toDocumentType(v: String): DocumentType = DocumentType.valueOf(v)
}

@Database(
  entities = [
    BranchEntity::class,
    TeamEntity::class,
    EmployeeEntity::class,
    EmployeeInviteEntity::class,
    CustomerEntity::class,
    CustomerDocumentEntity::class,
    LoanEntity::class,
    InstallmentScheduleEntity::class,
    PaymentEntity::class,
    PaymentAdjustmentEntity::class,
    AdvanceTransactionEntity::class,
    PreClosureRequestEntity::class,
    SyncQueueEntity::class,
    AuditLogEntity::class,
  ],
  version = 4,
  exportSchema = false,
)
@TypeConverters(FinanceTypeConverters::class)
abstract class LoanFinanceDatabase : RoomDatabase() {
  abstract fun dao(): LoanFinanceDao

  companion object {
    @Volatile
    private var INSTANCE: LoanFinanceDatabase? = null

    fun getInstance(context: Context): LoanFinanceDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          LoanFinanceDatabase::class.java,
          "loan_finance_manager.db",
        )
          .fallbackToDestructiveMigration(true)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
