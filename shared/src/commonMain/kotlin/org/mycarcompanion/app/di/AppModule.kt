package org.mycarcompanion.app.di

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.supabase.supabaseClient
import org.mycarcompanion.app.ui.auth.AuthScreenModel
import org.mycarcompanion.app.ui.home.HomeScreenModel

val appModule = module {
    single { supabaseClient }
    single { AuthRepository(get()) }
    factoryOf(::AuthScreenModel)
    factoryOf(::HomeScreenModel)
}
