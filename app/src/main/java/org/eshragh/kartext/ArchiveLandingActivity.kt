package org.eshragh.kartext

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.eshragh.kartext.database.AppDatabase
import org.eshragh.kartext.database.LogEntity
import org.eshragh.kartext.utils.DateConverter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ArchiveLandingActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private val STORAGE_PERMISSION_CODE = 101
    private var exportYear: Int = 0
    private var exportMonth: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive_landing)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = " "

        database = AppDatabase.getDatabase(this)

        val cardThisMonth = findViewById<MaterialCardView>(R.id.cardThisMonth)
        val cardLastMonth = findViewById<MaterialCardView>(R.id.cardLastMonth)
        val btnExportThisMonth = findViewById<ImageButton>(R.id.btnExportThisMonth)
        val btnExportLastMonth = findViewById<ImageButton>(R.id.btnExportLastMonth)

        lifecycleScope.launch {
            setupMonthCards()
        }

        cardThisMonth.setOnClickListener {
            val shamsiDate = DateConverter.toShamsi(Date())
            val intent = Intent(this, MonthDetailsActivity::class.java)
            intent.putExtra("YEAR", shamsiDate.year)
            intent.putExtra("MONTH", shamsiDate.month)
            startActivity(intent)
        }

        cardLastMonth.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -1)
            val shamsiDate = DateConverter.toShamsi(calendar.time)
            val intent = Intent(this, MonthDetailsActivity::class.java)
            intent.putExtra("YEAR", shamsiDate.year)
            intent.putExtra("MONTH", shamsiDate.month)
            startActivity(intent)
        }

        btnExportThisMonth.setOnClickListener {
            val shamsiDate = DateConverter.toShamsi(Date())
            requestExportForMonth(shamsiDate.year, shamsiDate.month)
        }

        btnExportLastMonth.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -1)
            val shamsiDate = DateConverter.toShamsi(calendar.time)
            requestExportForMonth(shamsiDate.year, shamsiDate.month)
        }
    }

    private fun requestExportForMonth(year: Int, month: Int) {
        exportYear = year
        exportMonth = month
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                exportMonthToCsv(exportYear, exportMonth)
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch {
                    exportMonthToCsv(exportYear, exportMonth)
                }
            } else {
                Toast.makeText(this, "دسترسی به حافظه برای ذخیره فایل گزارش رد شد", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun exportMonthToCsv(year: Int, month: Int) {
        val allLogs = database.logDao().getAllLogs()
        val monthLogs = allLogs.filter {
            val shamsiDate = DateConverter.toShamsi(Date(it.enterTime))
            shamsiDate.year == year && shamsiDate.month == month
        }.sortedBy { it.enterTime }

        if (monthLogs.isEmpty()) {
            Toast.makeText(this, "هیچ رکوردی برای این ماه یافت نشد", Toast.LENGTH_SHORT).show()
            return
        }

        val csvHeader = "\uFEFFتاریخ,زمان ورود,زمان خروج,کسورات (دقیقه),مدت زمان مفید\n"
        val csvContent = StringBuilder(csvHeader)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        for (log in monthLogs) {
            val shamsiDate = DateConverter.toShamsi(Date(log.enterTime))
            val date = DateConverter.formatShamsiDate(shamsiDate)
            val enterTime = timeFormat.format(Date(log.enterTime))
            val exitTime = if (log.exitTime != null) timeFormat.format(Date(log.exitTime!!)) else "--"
            val deductions = TimeUnit.MILLISECONDS.toMinutes(log.deductions)

            val durationMillis = if (log.exitTime != null) (log.exitTime!! - log.enterTime) - log.deductions else 0
            val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
            val duration = String.format("%d:%02d", hours, minutes)

            csvContent.append("$date,$enterTime,$exitTime,$deductions,$duration\n")
        }

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = getUniqueFile(downloadsDir, "report-$year-$month.csv")

            FileOutputStream(file).use {
                it.write(csvContent.toString().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "فایل گزارش با موفقیت در پوشه Downloads ذخیره شد", Toast.LENGTH_LONG).show()
            openFile(file)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "خطا در ذخیره فایل گزارش", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getUniqueFile(directory: File, baseName: String): File {
        var file = File(directory, baseName)
        if (!file.exists()) {
            return file
        }

        val name = baseName.substringBeforeLast('.')
        val ext = baseName.substringAfterLast('.')
        var count = 1
        while (file.exists()) {
            file = File(directory, "$name ($count).$ext")
            count++
        }
        return file
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "text/csv")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "هیچ برنامه‌ای برای باز کردن فایل CSV یافت نشد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun setupMonthCards() {
        val allLogs = database.logDao().getAllLogs()

        val today = Date()
        val shamsiToday = DateConverter.toShamsi(today)
        val thisMonthName = DateConverter.getShamsiMonthName(shamsiToday.month)
        val (thisMonthHours, thisMonthMinutes) = calculateMonthDuration(allLogs, shamsiToday.year, shamsiToday.month)

        findViewById<TextView>(R.id.tvThisMonthName).text = "این ماه ($thisMonthName)"
        val thisMonthDurationText = "${DateConverter.toPersianNumbers(thisMonthHours.toString())} ساعت و ${DateConverter.toPersianNumbers(thisMonthMinutes.toString())} دقیقه"
        findViewById<TextView>(R.id.tvThisMonthHours).text = "مجموع: $thisMonthDurationText"

        val calendar = Calendar.getInstance()
        calendar.time = today
        calendar.add(Calendar.MONTH, -1)
        val lastMonthDay = calendar.time
        val shamsiLastMonth = DateConverter.toShamsi(lastMonthDay)
        val lastMonthName = DateConverter.getShamsiMonthName(shamsiLastMonth.month)
        val (lastMonthHours, lastMonthMinutes) = calculateMonthDuration(allLogs, shamsiLastMonth.year, shamsiLastMonth.month)

        findViewById<TextView>(R.id.tvLastMonthName).text = "ماه قبل ($lastMonthName)"
        val lastMonthDurationText = "${DateConverter.toPersianNumbers(lastMonthHours.toString())} ساعت و ${DateConverter.toPersianNumbers(lastMonthMinutes.toString())} دقیقه"
        findViewById<TextView>(R.id.tvLastMonthHours).text = "مجموع: $lastMonthDurationText"
    }

    private fun calculateMonthDuration(logs: List<LogEntity>, year: Int, month: Int): Pair<Long, Long> {
        var totalMillis: Long = 0
        val monthLogs = logs.filter {
            val shamsiDate = DateConverter.toShamsi(Date(it.enterTime))
            shamsiDate.year == year && shamsiDate.month == month
        }

        for (log in monthLogs) {
            if (log.exitTime != null) {
                totalMillis += (log.exitTime!! - log.enterTime) - log.deductions
            }
        }
        val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
        return Pair(hours, minutes)
    }
}