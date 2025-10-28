package org.eshragh.kartext

import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.eshragh.kartext.adapters.RecordAdapter
import org.eshragh.kartext.api.RetrofitClient
import org.eshragh.kartext.database.AppDatabase
import org.eshragh.kartext.database.LogEntity
import org.eshragh.kartext.models.Record
import org.eshragh.kartext.models.RecordType
import org.eshragh.kartext.utils.DateConverter
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class DayDetailsActivity : AppCompatActivity() {

    private lateinit var rvDayDetailsRecords: RecyclerView
    private lateinit var database: AppDatabase
    private val records = mutableListOf<Record>()
    private lateinit var adapter: RecordAdapter
    private var timestamp: Long = 0
    private lateinit var fabAddRecord: FloatingActionButton
    private lateinit var sharedPreferences: SharedPreferences
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_details)

        sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        token = sharedPreferences.getString("token", null)

        if (token == null) {
            handleAuthFailure(false)
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = " "

        database = AppDatabase.getDatabase(this)
        rvDayDetailsRecords = findViewById(R.id.rvDayDetailsRecords)
        fabAddRecord = findViewById(R.id.fabAddRecord)

        timestamp = intent.getLongExtra("TIMESTAMP", 0)

        val date = Date(timestamp)
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = DateConverter.toPersianNumbers(DateConverter.formatShamsiDate(DateConverter.toShamsi(date)))

        adapter = RecordAdapter(records,
            { record -> editEnterTime(record) },
            { record -> editExitTime(record) },
            { record -> editDeductions(record) }
        )
        rvDayDetailsRecords.adapter = adapter
        rvDayDetailsRecords.layoutManager = LinearLayoutManager(this)

        setupSwipeToDelete()

        fabAddRecord.setOnClickListener { 
            showAddRecordDialogs()
        }

        lifecycleScope.launch {
            fetchRecords()
        }
    }

    private fun getAuthToken(): String = "Bearer $token"

    private fun handleAuthFailure(showToast: Boolean = true) {
        sharedPreferences.edit().remove("token").apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        if (showToast) {
            Toast.makeText(this, "نشست شما منقضی شده است. لطفاً دوباره وارد شوید.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteConfirmationDialog(position: Int) {
        val record = records[position]
        AlertDialog.Builder(this)
            .setTitle("تایید حذف")
            .setMessage("آیا از حذف این رکورد مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ -> deleteRecord(record, position) }
            .setNegativeButton("خیر") { _, _ -> adapter.notifyItemChanged(position) }
            .setOnCancelListener { adapter.notifyItemChanged(position) }
            .show()
    }

    private fun deleteRecord(record: Record, position: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteLog(getAuthToken(), record.id)
                if (response.isSuccessful) {
                    database.logDao().deleteLogById(record.id.toLong())
                    records.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    Toast.makeText(this@DayDetailsActivity, "رکورد حذف شد", Toast.LENGTH_SHORT).show()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleAuthFailure()
                } else {
                    Toast.makeText(this@DayDetailsActivity, "خطا در حذف رکورد", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DayDetailsActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
                adapter.notifyItemChanged(position)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun fetchRecords() {
        val startOfDay = getStartOfDay(Date(timestamp))
        val endOfDay = getEndOfDay(Date(timestamp))

        val savedLogs = database.logDao().getLogsBetween(startOfDay.time, endOfDay.time)
        records.clear()
        records.addAll(savedLogs.map { Record(id = it.id.toString(), enterTime = it.enterTime, exitTime = it.exitTime, deductions = it.deductions, type = it.type) })
        adapter.notifyDataSetChanged()
    }

    private fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun getEndOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.time
    }

    private fun editEnterTime(record: Record) {
        showTimePicker("زمان ورود را انتخاب کنید", record.enterTime) { newTime ->
            if (record.exitTime != null && newTime >= record.exitTime!!) {
                Toast.makeText(this, "زمان ورود نمی‌تواند بعد از زمان خروج باشد", Toast.LENGTH_SHORT).show()
                return@showTimePicker
            }
            record.enterTime = newTime
            updateRecordOnServer(record)
        }
    }

    private fun editExitTime(record: Record) {
        val initialTime = record.exitTime ?: System.currentTimeMillis()
        showTimePicker("زمان خروج را انتخاب کنید", initialTime) { newTime ->
            if (newTime <= record.enterTime) {
                Toast.makeText(this, "زمان خروج نمی‌تواند قبل از زمان ورود باشد", Toast.LENGTH_SHORT).show()
                return@showTimePicker
            }
            record.exitTime = newTime
            updateRecordOnServer(record)
        }
    }

    private fun editDeductions(record: Record) {
        showDurationPicker("مدت زمان کسورات را انتخاب کنید", record.deductions) { newDeductions ->
            record.deductions = newDeductions
            updateRecordOnServer(record)
        }
    }

    private fun updateRecordOnServer(record: Record) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateLog(getAuthToken(), record.id, record)
                if (response.isSuccessful) {
                    database.logDao().updateLog(LogEntity(record.id.toLong(), record.enterTime, record.exitTime, record.deductions, record.type))
                    fetchRecords()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleAuthFailure()
                } else {
                    Toast.makeText(this@DayDetailsActivity, "خطا در به‌روزرسانی رکورد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                 e.printStackTrace()
                 Toast.makeText(this@DayDetailsActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTimePicker(title: String, initialTime: Long, onTimeSet: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = initialTime

        val dialog = TimePickerDialog(this, { _, hourOfDay, minute ->
            val newCalendar = Calendar.getInstance()
            newCalendar.timeInMillis = initialTime // Preserve the original date
            newCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            newCalendar.set(Calendar.MINUTE, minute)
            onTimeSet(newCalendar.timeInMillis)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        
        dialog.setTitle(title)
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "تایید", dialog)
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "لغو", dialog)
        dialog.show()
    }

    private fun showDurationPicker(title: String, initialDuration: Long, onDurationSet: (Long) -> Unit) {
        val hours = TimeUnit.MILLISECONDS.toHours(initialDuration)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(initialDuration) % 60

        val dialog = TimePickerDialog(this, { _, hourOfDay, minute ->
            val newDuration = TimeUnit.HOURS.toMillis(hourOfDay.toLong()) + TimeUnit.MINUTES.toMillis(minute.toLong())
            onDurationSet(newDuration)
        }, hours.toInt(), minutes.toInt(), true)

        dialog.setTitle(title)
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "تایید", dialog)
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "لغو", dialog)
        dialog.show()
    }

    private fun showAddRecordDialogs() {
        showTimePicker("زمان ورود را انتخاب کنید", timestamp) { enterTime ->
            showTimePicker("زمان خروج را انتخاب کنید", enterTime) { exitTime ->
                if (exitTime <= enterTime) {
                    Toast.makeText(this, "زمان خروج نمی‌تواند قبل از زمان ورود باشد", Toast.LENGTH_SHORT).show()
                    return@showTimePicker
                }
                lifecycleScope.launch {
                    try {
                        val newRecord = Record(id = "temp-" + System.currentTimeMillis(), enterTime = enterTime, exitTime = exitTime, deductions = 0, type = RecordType.WORK)
                        val response = RetrofitClient.instance.addLog(getAuthToken(), newRecord)
                        if(response.isSuccessful && response.body() != null) {
                            val createdRecord = response.body()!!
                            database.logDao().insertLog(LogEntity(id = createdRecord.id.toLong(), enterTime = createdRecord.enterTime, exitTime = createdRecord.exitTime, deductions = createdRecord.deductions, type = createdRecord.type))
                            fetchRecords()
                        } else if (response.code() == 401 || response.code() == 403) {
                            handleAuthFailure()
                        } else {
                             Toast.makeText(this@DayDetailsActivity, "خطا در ثبت رکورد", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@DayDetailsActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if(records[viewHolder.adapterPosition].type == RecordType.WORK){
                    showDeleteConfirmationDialog(viewHolder.adapterPosition)
                } else {
                    Toast.makeText(this@DayDetailsActivity, "امکان حذف مرخصی از اینجا وجود ندارد", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                }
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                if (records.getOrNull(viewHolder.adapterPosition)?.type != RecordType.WORK) return

                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.RED)
                val deleteIcon = ContextCompat.getDrawable(this@DayDetailsActivity, android.R.drawable.ic_menu_delete)

                val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                val iconBottom = iconTop + deleteIcon.intrinsicHeight

                if (dX < 0) { // Swiping to the left
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                } else { // view is unSwiped
                    background.setBounds(0, 0, 0, 0)
                }

                background.draw(c)
                deleteIcon.draw(c)
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(rvDayDetailsRecords)
    }
}