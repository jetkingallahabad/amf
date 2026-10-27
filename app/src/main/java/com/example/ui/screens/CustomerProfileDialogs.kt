package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerEntity
import com.example.domain.model.CustomerKycValidator
import com.example.domain.model.DocumentType
import com.example.ui.theme.ConfirmedGreen
import com.example.ui.theme.ConfirmedGreenContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueContainer
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OverdueCrimson
import com.example.ui.theme.OverdueCrimsonContainer
import com.example.util.DocumentStorageHelper
import com.example.util.IndianFinanceFormatter
import java.io.File

@Composable
fun EditCustomerProfileDialog(
  customer: CustomerEntity,
  onDismiss: () -> Unit,
  onSaveProfile: (
    fullName: String,
    guardianName: String,
    mobile: String,
    alternateMobile: String,
    address: String,
    city: String,
    occupation: String,
    monthlyIncomePaise: Long,
  ) -> Unit,
) {
  var fullName by rememberSaveable(customer.id) { mutableStateOf(customer.fullName) }
  var guardianName by rememberSaveable(customer.id) { mutableStateOf(customer.guardianName) }
  var mobile by rememberSaveable(customer.id) { mutableStateOf(customer.mobile) }
  var altMobile by rememberSaveable(customer.id) { mutableStateOf(customer.alternateMobile) }
  var address by rememberSaveable(customer.id) { mutableStateOf(customer.address) }
  var city by rememberSaveable(customer.id) { mutableStateOf(customer.city) }
  var occupation by rememberSaveable(customer.id) { mutableStateOf(customer.occupation) }
  var monthlyIncomeRupees by rememberSaveable(customer.id) {
    mutableStateOf(
      if (customer.monthlyIncomePaise > 0L) {
        IndianFinanceFormatter.paiseToPlainRupeesString(customer.monthlyIncomePaise)
      } else {
        ""
      },
    )
  }

  val mobileError = remember(mobile) {
    if (mobile.isEmpty()) "Primary 10-digit mobile number is required."
    else runCatching { CustomerKycValidator.validateIndianMobile(mobile, "Primary mobile") }.exceptionOrNull()?.message
  }
  val altMobileError = remember(altMobile, mobile) {
    if (altMobile.isBlank()) null
    else if (altMobile == mobile) "Alternate mobile must differ from primary mobile."
    else runCatching { CustomerKycValidator.validateIndianMobile(altMobile, "Alternate mobile") }.exceptionOrNull()?.message
  }
  val canSave = fullName.trim().length >= 3 &&
    mobileError == null &&
    altMobileError == null &&
    address.trim().length >= 2

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Customer Profile (${customer.customerCode})") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Customer Full Name *") },
          singleLine = true,
          isError = fullName.isNotEmpty() && fullName.trim().length < 3,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_customer_name_input"),
        )
        OutlinedTextField(
          value = guardianName,
          onValueChange = { guardianName = it },
          label = { Text("Father / Spouse Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it.filter { ch -> ch.isDigit() }.take(10) },
            label = { Text("Mobile *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = mobileError != null,
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("edit_customer_mobile_input"),
          )
          OutlinedTextField(
            value = altMobile,
            onValueChange = { altMobile = it.filter { ch -> ch.isDigit() }.take(10) },
            label = { Text("Alt Mobile") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = altMobileError != null,
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        val phoneErr = mobileError ?: altMobileError
        if (phoneErr != null) {
          Text(
            text = phoneErr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Residential / Business Address *") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_customer_address_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City / Area") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = occupation,
            onValueChange = { occupation = it },
            label = { Text("Occupation") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = monthlyIncomeRupees,
          onValueChange = { monthlyIncomeRupees = it },
          label = { Text("Monthly Income (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = "Primary KYC (${customer.kycDocumentType}: ${customer.kycDocumentNumber}) is locked for audit compliance.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val incomePaise = IndianFinanceFormatter.parseRupeesToPaise(monthlyIncomeRupees) ?: 0L
          onSaveProfile(
            fullName,
            guardianName,
            mobile,
            altMobile,
            address,
            city,
            occupation,
            incomePaise,
          )
        },
        enabled = canSave,
        modifier = Modifier.testTag("confirm_edit_customer_button"),
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadCustomerDocumentDialog(
  initialDocumentNumber: String,
  onDismiss: () -> Unit,
  onSelectPhoto: (DocumentType, String) -> Unit,
  onSelectFile: (DocumentType, String) -> Unit,
) {
  var selectedDocType by rememberSaveable { mutableStateOf(DocumentType.AADHAAR_CARD) }
  var docNumber by rememberSaveable { mutableStateOf(initialDocumentNumber) }
  var expanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Upload Customer KYC / Loan Document") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExposedDropdownMenuBox(
          expanded = expanded,
          onExpandedChange = { expanded = !expanded },
        ) {
          OutlinedTextField(
            value = selectedDocType.displayTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Document Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
          ) {
            DocumentType.entries.forEach { dt ->
              DropdownMenuItem(
                text = { Text(dt.displayTitle) },
                onClick = {
                  selectedDocType = dt
                  expanded = false
                },
              )
            }
          }
        }

        OutlinedTextField(
          value = docNumber,
          onValueChange = { docNumber = it },
          label = { Text("Document / Reference Number") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Text(
          text = "Files are stored privately in encrypted-ready internal app storage (max 15 MB) with a SHA-256 tamper checksum synced to Cloud Firestore.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { onSelectPhoto(selectedDocType, docNumber) },
            modifier = Modifier
              .weight(1f)
              .testTag("upload_doc_photo_btn"),
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Photo")
          }
          OutlinedButton(
            onClick = { onSelectFile(selectedDocType, docNumber) },
            modifier = Modifier
              .weight(1f)
              .testTag("upload_doc_pdf_btn"),
          ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("PDF / File")
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun DocumentPreviewAndIntegrityDialog(
  doc: CustomerDocumentEntity,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var integrityStatus by remember(doc.id) {
    mutableStateOf<DocumentStorageHelper.DocumentIntegrityStatus?>(null)
  }

  LaunchedEffect(doc.id, doc.customerId, doc.localFilePath, doc.sha256Checksum) {
    integrityStatus = DocumentStorageHelper.verifyDocumentIntegrity(
      context = context,
      customerId = doc.customerId,
      localFilePath = doc.localFilePath,
      expectedSha256 = doc.sha256Checksum,
    )
  }

  val localFile = remember(context, doc.customerId, doc.localFilePath) {
    DocumentStorageHelper.resolveConfinedDocumentFile(context, doc.customerId, doc.localFilePath)
  }
  val hasLocalImage = doc.mimeType.startsWith("image/") &&
    localFile != null &&
    integrityStatus == DocumentStorageHelper.DocumentIntegrityStatus.VERIFIED_LOCAL_FILE

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(doc.documentType.displayTitle) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        if (hasLocalImage) {
          AsyncImage(
            model = localFile,
            contentDescription = doc.documentType.displayTitle,
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp)
              .clip(RoundedCornerShape(10.dp)),
          )
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
              imageVector = Icons.Default.Description,
              contentDescription = doc.documentType.displayTitle,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
              Text(doc.fileName, style = MaterialTheme.typography.titleSmall)
              Text(
                "${doc.mimeType} • ${(doc.fileSizeBytes / 1024L).coerceAtLeast(1L)} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        val status = integrityStatus
        if (status != null) {
          val (bg, fg, icon) = when (status) {
            DocumentStorageHelper.DocumentIntegrityStatus.VERIFIED_LOCAL_FILE ->
              Triple(ConfirmedGreenContainer, ConfirmedGreen, Icons.Default.VerifiedUser)
            DocumentStorageHelper.DocumentIntegrityStatus.CLOUD_METADATA_SYNCED ->
              Triple(InfoBlueContainer, InfoBlue, Icons.Default.VerifiedUser)
            DocumentStorageHelper.DocumentIntegrityStatus.CHECKSUM_MISMATCH ->
              Triple(OverdueCrimsonContainer, OverdueCrimson, Icons.Default.WarningAmber)
          }
          Surface(
            color = bg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                text = status.badgeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
              )
            }
          }
        }

        HorizontalDivider()
        if (doc.documentNumber.isNotBlank()) {
          DocMetaRow("Document ID", doc.documentNumber)
        }
        DocMetaRow("File Name", doc.fileName)
        DocMetaRow("File Size", "${(doc.fileSizeBytes / 1024L).coerceAtLeast(1L)} KB")
        DocMetaRow("Uploaded On", doc.uploadedDateDdMmYyyy)
        DocMetaRow("Uploaded By", doc.uploadedByName)
        Text(
          text = "SHA-256 Checksum:",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = doc.sha256Checksum,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        )
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) { Text("Close") }
    },
  )
}

@Composable
private fun DocMetaRow(label: String, value: String) {
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
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
      fontWeight = FontWeight.SemiBold,
    )
  }
}
