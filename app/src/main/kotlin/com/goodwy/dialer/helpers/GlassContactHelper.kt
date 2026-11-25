package com.goodwy.dialer.helpers

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.ContactsContract
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object GlassContactHelper {
    data class ContactInfo(val id: Int, val name: String, val phones: List<String>, val email: String)

    fun loadContact(ctx: Context, contactId: Int, fallback: String = ""): ContactInfo {
        var name = fallback
        val phones = mutableListOf<String>()
        var email = ""
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts._ID}=?", arrayOf(contactId.toString()), null
            )?.use { if (it.moveToFirst()) name = it.getString(0) ?: fallback }

            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(contactId.toString()), null
            )?.use { while (it.moveToNext()) { it.getString(0)?.trim()?.let { n -> if (n.isNotEmpty() && !phones.contains(n)) phones.add(n) } } }

            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?", arrayOf(contactId.toString()), null
            )?.use { if (it.moveToFirst()) email = it.getString(0).orEmpty() }
        }
        return ContactInfo(contactId, name.ifEmpty { "Unknown" }, phones, email)
    }

    fun showContactDetails(activity: Activity, contactId: Int, fallbackName: String = "", onChanged: (() -> Unit)? = null) {
        val info = loadContact(activity, contactId, fallbackName)
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun pill(bg: String, r: Int = 50) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(Color.parseColor(bg)) }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(20))
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(Color.parseColor("#EB1C1C1E")); setStroke(dp(1), Color.parseColor("#33FFFFFF")) }
        }
        val avatar = TextView(activity).apply {
            text = info.name.firstOrNull()?.uppercase() ?: "?"
            setTextColor(Color.WHITE); textSize = 24f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) }
            background = pill("#FF0A84FF", 32)
        }
        val title = TextView(activity).apply {
            text = info.name; setTextColor(Color.WHITE); textSize = 20f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(avatar); root.addView(title)

        val dlg = AlertDialog.Builder(activity).setView(root).create().apply { window?.setBackgroundDrawableResource(android.R.color.transparent) }

        info.phones.forEach { num ->
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                background = pill("#1FFFFFFF", 16)
            }
            val numTv = TextView(activity).apply { text = num; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(8)) }
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            fun actBtn(label: String, bg: String, fg: String, click: () -> Unit) = TextView(activity).apply {
                text = label; setTextColor(Color.parseColor(fg)); textSize = 13f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8)); background = pill(bg)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { dlg.dismiss(); click() }
            }
            row.addView(actBtn("Call", "#FF30D158", "#FFFFFFFF") {
                activity.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(num)}")))
            })
            row.addView(actBtn("SMS", "#FF0A84FF", "#FFFFFFFF") {
                activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(num)}")))
            })
            row.addView(actBtn("WhatsApp", "#2630D158", "#FF30D158") {
                val clean = num.replace(Regex("[^0-9]"), "")
                val wa = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$clean"))
                runCatching { activity.startActivity(wa) }
            })
            card.addView(numTv); card.addView(row); root.addView(card)
        }

        if (info.email.isNotEmpty()) {
            root.addView(TextView(activity).apply {
                text = "Email: ${info.email}"; setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 13f; setPadding(dp(4), 0, 0, dp(12))
            })
        }

        val bottomRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        val editBtn = TextView(activity).apply {
            text = "Edit Contact"; setTextColor(Color.WHITE); textSize = 14f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11)); background = pill("#33FFFFFF")
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
            setOnClickListener { dlg.dismiss(); showAddEditContact(activity, contactId, onChanged) }
        }
        val delBtn = TextView(activity).apply {
            text = "Delete"; setTextColor(Color.parseColor("#FFFF453A")); textSize = 14f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11)); background = pill("#26FF453A")
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_CONTACTS), 9915)
                    Toast.makeText(activity, "Allow Contacts permission, then try again", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                runCatching {
                    activity.contentResolver.delete(ContactsContract.RawContacts.CONTENT_URI, "${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(contactId.toString()))
                    Toast.makeText(activity, "Contact deleted", Toast.LENGTH_SHORT).show()
                    onChanged?.invoke()
                }
                dlg.dismiss()
            }
        }
        bottomRow.addView(editBtn); bottomRow.addView(delBtn); root.addView(bottomRow)
        dlg.show()
    }

    fun showAddEditContact(activity: Activity, contactId: Int?, onSaved: (() -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_CONTACTS), 9915)
            Toast.makeText(activity, "Allow Contacts permission, then tap again", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = contactId?.let { loadContact(activity, it) }
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun box(bg: String, r: Int = 16) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(Color.parseColor(bg)) }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(20))
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(Color.parseColor("#EB1C1C1E")); setStroke(dp(1), Color.parseColor("#33FFFFFF")) }
        }
        val heading = TextView(activity).apply {
            text = if (contactId == null) "New Contact" else "Edit Contact"
            setTextColor(Color.WHITE); textSize = 19f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(14))
        }
        fun field(hintText: String, prefill: String, inputType: Int) = EditText(activity).apply {
            hint = hintText; setText(prefill); setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#80FFFFFF"))
            this.inputType = inputType; setPadding(dp(14), dp(12), dp(14), dp(12)); background = box("#1FFFFFFF", 14)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        val nameEt = field("Full Name", existing?.name?.takeIf { it != "Unknown" }.orEmpty(), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val phoneEt = field("Phone Number", existing?.phones?.firstOrNull().orEmpty(), android.text.InputType.TYPE_CLASS_PHONE)
        val emailEt = field("Email (Optional)", existing?.email.orEmpty(), android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)

        val dlg = AlertDialog.Builder(activity).setView(root).create().apply { window?.setBackgroundDrawableResource(android.R.color.transparent) }
        val saveBtn = TextView(activity).apply {
            text = "Save Contact"; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12)); background = box("#FF0A84FF", 50)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
            setOnClickListener {
                val nm = nameEt.text.toString().trim()
                val ph = phoneEt.text.toString().trim()
                val em = emailEt.text.toString().trim()
                if (nm.isEmpty() && ph.isEmpty()) { Toast.makeText(activity, "Enter name or number", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                runCatching {
                    val ops = ArrayList<ContentProviderOperation>()
                    if (contactId == null) {
                        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
                        if (nm.isNotEmpty()) ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, nm).build())
                        if (ph.isNotEmpty()) ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, ph).withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
                        if (em.isNotEmpty()) ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, em).build())
                    } else {
                        var rawId = 0L
                        activity.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID), "${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(contactId.toString()), null)?.use { if (it.moveToFirst()) rawId = it.getLong(0) }
                        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI).withSelection("${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE} IN (?,?,?)", arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)).build())
                        if (nm.isNotEmpty()) ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, nm).build())
                        if (ph.isNotEmpty()) ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, ph).withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
                        if (em.isNotEmpty()) ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, em).build())
                    }
                    activity.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    Toast.makeText(activity, "Contact saved", Toast.LENGTH_SHORT).show()
                    onSaved?.invoke()
                }
                dlg.dismiss()
            }
        }
        root.addView(heading); root.addView(nameEt); root.addView(phoneEt); root.addView(emailEt); root.addView(saveBtn)
        dlg.show()
    }
}
