package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ConnectedAccountEntity::class,
        AiProviderConfigEntity::class,
        AiModelEntity::class,
        RoutingRuleEntity::class,
        AiUsageLogEntity::class,
        EmailEntity::class,
        AiSummaryEntity::class,
        AiDraftEntity::class,
        FollowUpReminderEntity::class,
        AutomationRuleEntity::class,
        AutomationExecutionEntity::class,
        AttachmentEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        CustomCategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WafaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun accountDao(): AccountDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun emailDao(): EmailDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        @Volatile
        private var INSTANCE: WafaDatabase? = null

        fun getInstance(context: Context): WafaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WafaDatabase::class.java,
                    "wafa_inboxai.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
