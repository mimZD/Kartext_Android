package org.eshragh.kartext

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.eshragh.kartext.adapters.RecordAdapter
import org.eshragh.kartext.database.AppDatabase
import java.util.Calendar

class ArchiveActivity : AppCompatActivity() {

    private lateinit var spnYear: Spinner
    private lateinit var spnMonth: Spinner
    private lateinit var rvArchiveRecords: RecyclerView
    private lateinit var database: AppDatabase
    private val records = mutableListOf<Any>()
    private lateinit var adapter: RecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)

        database = AppDatabase.getDatabase(this)
        spnYear = findViewById(R.id.spnYear)
        spnMonth = findViewById(R.id.spnMonth)
        rvArchiveRecords = findViewById(R.id.rvArchiveRecords)

        setupSpinners()

        rvArchiveRecords.layoutManager = LinearLayoutManager(this)
        // The adapter will be more complex to handle headers, so this is a placeholder
        // adapter = RecordAdapter(records, {}, {}, {})
        // rvArchiveRecords.adapter = adapter
    }

    private fun setupSpinners() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (2020..currentYear).map { it.toString() }.toMutableList()
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        spnYear.adapter = yearAdapter

        val months = (1..12).map { "ماه $it" }.toMutableList()
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        spnMonth.adapter = monthAdapter

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadRecordsForSelectedMonth()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spnYear.onItemSelectedListener = itemSelectedListener
        spnMonth.onItemSelectedListener = itemSelectedListener
    }

    private fun loadRecordsForSelectedMonth() {
        lifecycleScope.launch {
            // Logic to fetch and group records by day will be added here
        }
    }
}