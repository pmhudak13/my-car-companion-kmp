package org.mycarcompanion.app.di

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.VehicleRepository
import org.mycarcompanion.app.data.supabase.supabaseClient
import org.mycarcompanion.app.ui.auth.AuthScreenModel
import org.mycarcompanion.app.ui.home.HomeScreenModel
import org.mycarcompanion.app.ui.maintenance.AddMaintenanceScreenModel
import org.mycarcompanion.app.ui.reminders.AddReminderScreenModel
import org.mycarcompanion.app.ui.vehicles.AddVehicleScreenModel
import org.mycarcompanion.app.ui.vehicles.VehicleDetailScreenModel
import org.mycarcompanion.app.ui.vehicles.VehicleListScreenModel

val appModule = module {
    single { supabaseClient }
    single { AuthRepository(get()) }
    single { VehicleRepository(get()) }
    single { MaintenanceRepository(get()) }
    single { ReminderRepository(get()) }
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
    factoryOf(::VehicleListScreenModel)
    factoryOf(::AddVehicleScreenModel)
    factoryOf(::VehicleDetailScreenModel)
    factoryOf(::AddMaintenanceScreenModel)
    factoryOf(::AddReminderScreenModel)
}
