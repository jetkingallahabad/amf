package com.example.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "loan_finance_session_prefs",
)

interface SessionStore {
  val activeEmployeeIdFlow: Flow<String?>
  suspend fun setActiveEmployeeId(employeeId: String?)
}

class DataStoreSessionPreferences(private val context: Context) : SessionStore {

  override val activeEmployeeIdFlow: Flow<String?> =
    context.sessionDataStore.data.map { prefs -> prefs[KEY_ACTIVE_EMPLOYEE_ID] }

  override suspend fun setActiveEmployeeId(employeeId: String?) {
    context.sessionDataStore.edit { prefs ->
      if (employeeId == null) {
        prefs.remove(KEY_ACTIVE_EMPLOYEE_ID)
      } else {
        prefs[KEY_ACTIVE_EMPLOYEE_ID] = employeeId
      }
    }
  }

  private companion object {
    val KEY_ACTIVE_EMPLOYEE_ID = stringPreferencesKey("active_employee_id")
  }
}
