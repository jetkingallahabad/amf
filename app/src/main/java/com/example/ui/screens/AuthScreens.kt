package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.local.EmployeeEntity
import com.example.ui.theme.MonoFontFamily
import com.example.util.GoogleCredentialAuthHelper

@Composable
fun GoogleSignInScreen(
  isBusy: Boolean,
  errorMessage: String?,
  onSetBusy: (Boolean) -> Unit,
  onClearError: () -> Unit,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
) {
  var mobile by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .imePadding(),
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      HeroBrandBanner(
        title = stringResource(R.string.app_name),
        subtitle = "AM Finance • Employee Login",
      )
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Column(
          modifier = Modifier.padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Employee Login",
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Login with Mobile Number", style = MaterialTheme.typography.titleLarge)
          }
          Text(
            "Enter the mobile number and password assigned to you in Firebase.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (!errorMessage.isNullOrBlank()) ErrorBanner(errorMessage)

          OutlinedTextField(
            value = mobile,
            onValueChange = { value -> mobile = value.filter { it.isDigit() }.take(10) },
            label = { Text("10-digit mobile number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
          )
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
          )
          Button(
            onClick = {
              onClearError()
              if (mobile.length != 10 || password.isBlank()) {
                onAuthError("Enter a valid 10-digit mobile number and password.")
                return@Button
              }
              onSetBusy(true)
              val loginEmail = "${mobile}@amfinance-login.com"
              com.google.firebase.auth.FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(loginEmail, password)
                .addOnCompleteListener { task ->
                  onSetBusy(false)
                  if (task.isSuccessful) {
                    onAuthSuccess()
                  } else {
                    onAuthError(
                      when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthInvalidUserException,
                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                          "Login failed. Check the mobile number and password, and confirm the Firebase user exists."
                        else -> task.exception?.localizedMessage ?: "Login failed. Please try again."
                      }
                    )
                  }
                }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("mobile_password_login_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
              Spacer(Modifier.width(10.dp))
              Text("Signing in…")
            } else {
              Icon(Icons.Default.Lock, contentDescription = null)
              Spacer(Modifier.width(10.dp))
              Text("Login")
            }
          }
          Text(
            "Testing setup: Firebase Authentication email must be mobile@amfinance-login.com.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun OwnerSetupScreen(
  signedInEmail: String,
  suggestedFullName: String,
  isBusy: Boolean,
  errorMessage: String?,
  onCreateOwner: (
    orgName: String,
    fullName: String,
    mobile: String,
    email: String,
    branchName: String,
    branchCity: String,
    branchAddress: String,
  ) -> Unit,
  onSignOut: () -> Unit,
) {
  var orgName by rememberSaveable { mutableStateOf("") }
  var fullName by rememberSaveable(suggestedFullName) { mutableStateOf(suggestedFullName) }
  var mobile by rememberSaveable { mutableStateOf("") }
  var branchName by rememberSaveable { mutableStateOf("") }
  var branchCity by rememberSaveable { mutableStateOf("") }
  var branchAddress by rememberSaveable { mutableStateOf("") }

  Box(
    contentAlignment = Alignment.TopCenter,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .imePadding(),
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 540.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      HeroBrandBanner(
        title = stringResource(R.string.app_name),
        subtitle = "Enterprise Microfinance, Daily EDI & Weekly EMI Cloud Ledger",
      )

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = "Owner Setup",
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.setup_owner_title),
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Text(
            text = stringResource(R.string.setup_owner_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Verified Google Account",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text = signedInEmail.ifBlank { "Authenticated via Google" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }

          if (!errorMessage.isNullOrBlank()) {
            ErrorBanner(errorMessage)
          }

          OutlinedTextField(
            value = orgName,
            onValueChange = { orgName = it },
            label = { Text(stringResource(R.string.label_org_name)) },
            placeholder = { Text("e.g. Samruddhi Microfinance Pvt Ltd") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("setup_org_name_input"),
          )

          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("${stringResource(R.string.label_full_name)} *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("setup_owner_name_input"),
          )

          OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it.filter { ch -> ch.isDigit() }.take(10) },
            label = { Text("${stringResource(R.string.label_mobile_number)} *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("setup_owner_mobile_input"),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = branchName,
              onValueChange = { branchName = it },
              label = { Text("${stringResource(R.string.label_branch_name)} *") },
              singleLine = true,
              modifier = Modifier
                .weight(1.2f)
                .testTag("setup_branch_name_input"),
            )
            OutlinedTextField(
              value = branchCity,
              onValueChange = { branchCity = it },
              label = { Text("${stringResource(R.string.label_branch_city)} *") },
              singleLine = true,
              modifier = Modifier
                .weight(0.8f)
                .testTag("setup_branch_city_input"),
            )
          }

          OutlinedTextField(
            value = branchAddress,
            onValueChange = { branchAddress = it },
            label = { Text("Branch Office Address") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("setup_branch_address_input"),
          )

          Spacer(Modifier.height(4.dp))
          Button(
            onClick = {
              onCreateOwner(
                orgName,
                fullName,
                mobile,
                signedInEmail,
                branchName,
                branchCity,
                branchAddress,
              )
            },
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("create_owner_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
            } else {
              Text(stringResource(R.string.action_create_owner))
            }
          }

          OutlinedButton(
            onClick = onSignOut,
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("owner_setup_sign_out_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Switch Google Account")
          }

          Text(
            text = "Your Owner account receives Employee Code EMP-0001 in Cloud Firestore. Only the Owner can invite Branch Managers and Collection Agents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun AccessNotProvisionedScreen(
  signedInEmail: String,
  isBusy: Boolean,
  errorMessage: String?,
  onRecheckAccess: () -> Unit,
  onSignOut: () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(20.dp),
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .widthIn(max = 500.dp)
        .fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Access Restricted",
            tint = MaterialTheme.colorScheme.error,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.access_not_provisioned_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        Text(
          text = "This organization is already initialized. For financial security, self-registration and self-assigned roles are disabled.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "Share this Google Email with your Organization Owner:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = signedInEmail.ifBlank { "No Google email found on account" },
              style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Text(
          text = "Once the Owner adds your Google email under Manage → Employees & Role Management, tap 'Check Invitation Again' below.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!errorMessage.isNullOrBlank()) {
          ErrorBanner(errorMessage)
        }

        Button(
          onClick = onRecheckAccess,
          enabled = !isBusy,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("recheck_invite_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Check Invitation Again")
          }
        }

        OutlinedButton(
          onClick = onSignOut,
          enabled = !isBusy,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("unprovisioned_sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Sign Out / Switch Google Account")
        }
      }
    }
  }
}

@Composable
fun AccountDeactivatedScreen(
  employee: EmployeeEntity,
  onSignOut: () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(20.dp),
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .widthIn(max = 480.dp)
        .fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Block,
            contentDescription = "Account Deactivated",
            tint = MaterialTheme.colorScheme.error,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.account_deactivated_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = "${employee.fullName} (${employee.employeeCode}) • ${employee.role.displayTitle} has been marked inactive by the Organization Owner. Database access is suspended.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("deactivated_sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Sign Out")
        }
      }
    }
  }
}

@Composable
private fun HeroBrandBanner(title: String, subtitle: String) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Image(
        painter = painterResource(id = R.drawable.img_finance_hero_1793009518068),
        contentDescription = "Loan Finance Manager Banner",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp)
          .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp),
      ) {
        Icon(
          imageVector = Icons.Default.AccountBalance,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
          )
        }
      }
    }
  }
}

@Composable
private fun ErrorBanner(message: String) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onErrorContainer,
      fontWeight = FontWeight.Medium,
      modifier = Modifier
        .padding(12.dp)
        .testTag("auth_error_banner"),
    )
  }
}
