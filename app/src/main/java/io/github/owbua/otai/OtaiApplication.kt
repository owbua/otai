package io.github.owbua.otai

import android.app.Application
import io.github.owbua.otai.data.KitchenRepository
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.RecipeStore
import io.github.owbua.otai.util.TimerAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OtaiApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy { RecipeRepository(RecipeStore(filesDir), appScope, Dispatchers.IO) }
    val kitchen by lazy { KitchenRepository(filesDir, appScope, Dispatchers.IO) }
    val timerAlerts by lazy { TimerAlerts(this) }
    val settings by lazy { io.github.owbua.otai.data.SettingsRepository(this) }
}
