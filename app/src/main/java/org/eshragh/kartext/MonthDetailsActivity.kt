package org.eshragh.kartext

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.eshragh.kartext.adapters.DayAdapter
import org.eshragh.kartext.database.AppDatabase
import org.eshragh.kartext.models.DayRecord
import org.eshragh.kartext.utils.DateConverter
import java.util.Date
import java.util.concurrent.TimeUnit

class MonthDetailsActivity : AppCompatActivity() {

    private lateinit var rvDayRecords: RecyclerView
    private lateinit var database: AppDatabase
    private var year: Int = 0
    private var month: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_month_details)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = " "

        database = AppDatabase.getDatabase(this)
        rvDayRecords = findViewById(R.id.rvDayRecords)

        year = intent.getIntExtra("YEAR", 0)
        month = intent.getIntExtra("MONTH", 0)

        val monthName = DateConverter.getShamsiMonthName(month)
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "$monthName ${DateConverter.toPersianNumbers(year.toString())}"

        rvDayRecords.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            loadDayRecords()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun loadDayRecords() {
        val allLogs = database.logDao().getAllLogs()
        val monthLogs = allLogs.filter { 
            val shamsiDate = DateConverter.toShamsi(Date(it.enterTime))
            shamsiDate.year == year && shamsiDate.month == month
        }
        
        val groupedByDay = monthLogs.groupBy { 
            val shamsiDate = DateConverter.toShamsi(Date(it.enterTime))
            shamsiDate.day
        }

        val dayRecords = mutableListOf<DayRecord>()
        for ((dayOfMonth, dayLogs) in groupedByDay) {
            var totalMillis: Long = 0
            for (log in dayLogs) {
                if (log.exitTime != null) {
                    totalMillis += (log.exitTime!! - log.enterTime) - log.deductions
                }
            }
            val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60

            val firstLogTime = Date(dayLogs.first().enterTime)
            val dayOfWeek = DateConverter.getShamsiDayOfWeek(firstLogTime)

            dayRecords.add(DayRecord(dayOfMonth, dayOfWeek, hours, minutes, dayLogs.first().enterTime))
        }

        val adapter = DayAdapter(dayRecords.sortedBy { it.dayOfMonth }) { dayRecord ->
            val intent = Intent(this, DayDetailsActivity::class.java)
            intent.putExtra("TIMESTAMP", dayRecord.timestamp)
            startActivity(intent)
        }
        rvDayRecords.adapter = adapter
    }
}