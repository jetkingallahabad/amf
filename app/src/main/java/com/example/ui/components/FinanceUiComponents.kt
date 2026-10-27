package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PaymentEntity
import com.example.domain.model.LoanStatus
import com.example.domain.model.PaymentRecordStatus
import com.example.domain.model.SyncStatus
import com.example.domain.model.UserRole
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.ConfirmedGreenContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueContainer
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.ui.theme.OverdueCrimsonContainer
import com.example.ui.theme.PendingSyncAmber
import com.example.ui.theme.PendingSyncAmberContainer
import com.example.util.IndianFinanceFormatter
import java.util.Calendar
import java.util.Locale

@Composable
fun SyncStatusBadge(
  syncStatus: SyncStatus,
  modifier: Modifier = Modifier,
) {
  val (bgColor, fgColor, icon) = when (syncStatus) {
    SyncStatus.SERVER_CONFIRMED -> Triple(ConfirmedGreenContainer, ConfirmedGreen, Icons.Default.CloudDone)
    SyncStatus.CONFIRMED_LOCALLY_PENDING_SYNC, SyncStatus.SYNCING -> Triple(
      PendingSyncAmberContainer,
      PendingSyncAmber,
      Icons.Default.CloudQueue,
    )
    SyncStatus.SYNC_FAILED -> Triple(OverdueCrimsonContainer, OverdueCrimson, Icons.Default.SyncProblem)
  }

  Surface(
    color = bgColor,
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, fgColor.copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = syncStatus.displayTitle,
        tint = fgColor,
        modifier = Modifier.size(14.dp),
      )
      Spacer(Modifier.width(5.dp))
      Text(
        text = syncStatus.displayTitle,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fgColor,
      )
    }
  }
}

@Composable
fun PaymentRecordStatusBadge(
  recordStatus: PaymentRecordStatus,
  modifier: Modifier = Modifier,
) {
  val (bg, fg) = when (recordStatus) {
    PaymentRecordStatus.ACTIVE -> ConfirmedGreenContainer to ConfirmedGreen
    PaymentRecordStatus.REVERSED -> OverdueCrimsonContainer to OverdueCrimson
    PaymentRecordStatus.CORRECTED -> InfoBlueContainer to InfoBlue
  }
  Surface(
    color = bg,
    shape = RoundedCornerShape(6.dp),
    modifier = modifier,
  ) {
    Text(
      text = recordStatus.displayTitle,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun RoleBadge(role: UserRole, modifier: Modifier = Modifier) {
  val (bg, fg) = when (role) {
    UserRole.OWNER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    UserRole.BRANCH_MANAGER -> InfoBlueContainer to InfoBlue
    UserRole.COLLECTION_AGENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
  }
  Surface(
    color = bg,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Text(
      text = role.displayTitle.uppercase(Locale.US),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = fg,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun LoanStatusBadge(status: LoanStatus, modifier: Modifier = Modifier) {
  val (bg, fg) = when (status) {
    LoanStatus.ACTIVE -> ConfirmedGreenContainer to ConfirmedGreen
    LoanStatus.APPROVED -> InfoBlueContainer to InfoBlue
    LoanStatus.PENDING_APPROVAL -> PendingSyncAmberContainer to PendingSyncAmber
    LoanStatus.PRE_CLOSURE_REQUESTED -> InfoBlueContainer to InfoBlue
    LoanStatus.COMPLETED, LoanStatus.PRE_CLOSED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    LoanStatus.REJECTED -> OverdueCrimsonContainer to OverdueCrimson
  }
  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier,
  ) {
    Text(
      text = status.displayTitle,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun KpiStatCard(
  title: String,
  valueText: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    modifier = if (onClick != null) {
      modifier.clickable(onClick = onClick)
    } else {
      modifier
    },
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(32.dp)
            .background(accentColor.copy(alpha = 0.12f), CircleShape),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = valueText,
        style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun DdMmYyyyDateField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  testTagName: String = "date_field",
) {
  val context = LocalContext.current
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text("$label (DD-MM-YYYY)") },
    singleLine = true,
    trailingIcon = {
      IconButton(
        onClick = {
          val cal = Calendar.getInstance()
          IndianFinanceFormatter.parseDdMmYyyyToMillis(value)?.let { cal.timeInMillis = it }
          DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
              val formatted = "%02d-%02d-%04d".format(Locale.US, dayOfMonth, month + 1, year)
              onValueChange(formatted)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
          ).show()
        },
      ) {
        Icon(
          imageVector = Icons.Default.CalendarMonth,
          contentDescription = "Select $label date",
        )
      }
    },
    modifier = modifier.testTag(testTagName),
  )
}

@Composable
fun EmptyLedgerState(
  icon: ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .padding(28.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(56.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
      }
      Spacer(Modifier.height(12.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Immediate Payment Collection Confirmation Dialog displayed strictly AFTER
 * the ACID Room database transaction commits the payment and sync queue entry.
 */
@Composable
fun VerifiedReceiptConfirmationDialog(
  payment: PaymentEntity,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(
        onClick = onDismiss,
        modifier = Modifier.testTag("receipt_done_button"),
      ) {
        Text("Done")
      }
    },
    icon = {
      Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = "Payment Saved",
        tint = ConfirmedGreen,
        modifier = Modifier.size(40.dp),
      )
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Collection Saved to Ledger",
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        SyncStatusBadge(syncStatus = payment.syncStatus)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
          ) {
            Text(
              text = IndianFinanceFormatter.formatPaise(payment.amountPaise, showZeroPaise = true),
              style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = payment.receiptNumber,
              style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Txn ID: ${payment.transactionId}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        ReceiptDetailRow("Customer", "${payment.customerName} (${payment.customerCode})")
        ReceiptDetailRow("Loan Account", payment.loanCode)
        ReceiptDetailRow("Payment Type", payment.paymentType.displayTitle)
        ReceiptDetailRow("Payment Mode", payment.paymentMode.displayTitle)
        if (payment.referenceNumber.isNotBlank()) {
          ReceiptDetailRow("Ref / UTR", payment.referenceNumber)
        }
        if (payment.allocatedToInstallmentsPaise > 0L) {
          ReceiptDetailRow(
            "Applied to EMI/EDI",
            IndianFinanceFormatter.formatPaise(payment.allocatedToInstallmentsPaise),
          )
        }
        if (payment.creditedToAdvancePaise > 0L) {
          ReceiptDetailRow(
            "Credited to Advance",
            IndianFinanceFormatter.formatPaise(payment.creditedToAdvancePaise),
          )
        }
        if (payment.allocationsSummary.isNotBlank()) {
          Text(
            text = "Allocation: ${payment.allocationsSummary}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        ReceiptDetailRow("Collection Date", payment.collectionDateDdMmYyyy)
        ReceiptDetailRow("Saved At", IndianFinanceFormatter.formatDateTimeSeconds(payment.collectedAtMillis))
        ReceiptDetailRow("Collected By", payment.collectedByName)

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
          text = if (payment.syncStatus == SyncStatus.SERVER_CONFIRMED) {
            "Server Confirmed on Cloud Firestore (Confirmation ID: ${payment.serverConfirmationId})."
          } else {
            "Confirmed Locally in Room SQLite (Pending Sync). This transaction has a unique offline ID (${payment.transactionId}) and will automatically sync to Cloud Firestore when internet returns."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

@Composable
private fun ReceiptDetailRow(label: String, value: String) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
