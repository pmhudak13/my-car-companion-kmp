package org.mycarcompanion.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.MechanicJobIssueRepository
import org.mycarcompanion.app.data.repository.MechanicJobMediaRepository
import org.mycarcompanion.app.data.repository.MechanicJobRepository
import org.mycarcompanion.app.data.repository.MechanicRepository
import org.mycarcompanion.app.data.repository.MessageRepository
import org.mycarcompanion.app.data.repository.MileageTripRepository
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.ReviewRepository
import org.mycarcompanion.app.data.repository.SubscriptionRepository
import org.mycarcompanion.app.data.repository.DeviceTokenRepository
import org.mycarcompanion.app.data.repository.NotificationPreferencesRepository
import org.mycarcompanion.app.data.repository.TransferRepository
import org.mycarcompanion.app.data.repository.VehicleRepository
import org.mycarcompanion.app.data.supabase.supabaseClient
import org.mycarcompanion.app.ui.admin.AdminScreenModel
import org.mycarcompanion.app.ui.auth.AuthScreenModel
import org.mycarcompanion.app.ui.home.HomeScreenModel
import org.mycarcompanion.app.ui.maintenance.AddMaintenanceScreenModel
import org.mycarcompanion.app.ui.mechanics.CreateMechanicJobScreenModel
import org.mycarcompanion.app.ui.mechanics.MechanicDashboardScreenModel
import org.mycarcompanion.app.ui.mechanics.MechanicDirectoryScreenModel
import org.mycarcompanion.app.ui.mechanics.MechanicJobDetailScreenModel
import org.mycarcompanion.app.ui.mechanics.MechanicSetupScreenModel
import org.mycarcompanion.app.ui.mechanics.MechanicVehicleViewScreenModel
import org.mycarcompanion.app.ui.mechanics.RecordImportScreenModel
import org.mycarcompanion.app.ui.messaging.MessagesListScreenModel
import org.mycarcompanion.app.ui.messaging.MessagingScreenModel
import org.mycarcompanion.app.ui.reviews.MechanicReviewsScreenModel
import org.mycarcompanion.app.ui.transfer.ReceiveTransferScreenModel
import org.mycarcompanion.app.ui.transfer.TransferScreenModel
import org.mycarcompanion.app.ui.mileage.MileageTrackerScreenModel
import org.mycarcompanion.app.ui.profile.ProfileScreenModel
import org.mycarcompanion.app.ui.reminders.AddReminderScreenModel
import org.mycarcompanion.app.ui.reminders.RemindersListScreenModel
import org.mycarcompanion.app.ui.settings.SettingsScreenModel
import org.mycarcompanion.app.ui.notifications.NotificationsScreenModel
import org.mycarcompanion.app.ui.subscription.SubscribeScreenModel
import org.mycarcompanion.app.ui.vehicles.AddVehicleScreenModel
import org.mycarcompanion.app.ui.vehicles.VehicleSettingsScreenModel
import org.mycarcompanion.app.ui.vehicles.VehicleDetailScreenModel
import org.mycarcompanion.app.ui.vehicles.VehicleListScreenModel

val appModule = module {
    single { supabaseClient }
    single(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { AuthRepository(get(), get(), get(named("appScope"))) }
    single { VehicleRepository(get()) }
    single { MaintenanceRepository(get()) }
    single { ReminderRepository(get()) }
    single { MechanicRepository(get()) }
    single { MechanicAssignmentRepository(get()) }
    single { MechanicJobRepository(get()) }
    single { MechanicJobIssueRepository(get()) }
    single { MechanicJobMediaRepository(get()) }
    single { MessageRepository(get()) }
    single { MileageTripRepository(get()) }
    single { ProfileRepository(get()) }
    single { ReviewRepository(get()) }
    single { SubscriptionRepository(get()) }
    single { TransferRepository(get()) }
    single { DeviceTokenRepository(get()) }
    single { NotificationPreferencesRepository(get()) }
    factoryOf(::AdminScreenModel)
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf(::VehicleListScreenModel)
    factoryOf(::AddVehicleScreenModel)
    factory { VehicleDetailScreenModel(get(), get(), get(), get(), get(), get(), get()) }
    factoryOf(::AddMaintenanceScreenModel)
    factoryOf(::AddReminderScreenModel)
    factoryOf(::MechanicDashboardScreenModel)
    factoryOf(::MechanicDirectoryScreenModel)
    factoryOf(::CreateMechanicJobScreenModel)
    factory { MechanicJobDetailScreenModel(get(), get(), get(), get(), get()) }
    factoryOf(::MechanicSetupScreenModel)
    factoryOf(::MessagesListScreenModel)
    factoryOf(::MessagingScreenModel)
    factoryOf(::MileageTrackerScreenModel)
    factoryOf(::RemindersListScreenModel)
    factoryOf(::ProfileScreenModel)
    factoryOf(::SettingsScreenModel)
    factoryOf(::VehicleSettingsScreenModel)
    factoryOf(::MechanicVehicleViewScreenModel)
    factoryOf(::MechanicReviewsScreenModel)
    factoryOf(::RecordImportScreenModel)
    factoryOf(::TransferScreenModel)
    factoryOf(::ReceiveTransferScreenModel)
    factoryOf(::NotificationsScreenModel)
    factoryOf(::SubscribeScreenModel)
}
