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
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "KartextDebug_Main"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvRecords: RecyclerView
    private lateinit var tvUserStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var tvTodayDateLabel: TextView
    private lateinit var blinkingDot: View
    private lateinit var sharedPreferences: SharedPreferences

    private val records = mutableListOf<Record>()
    private lateinit var adapter: RecordAdapter
    private lateinit var database: AppDatabase
    private var isEntry = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        // Trust that this activity is only started when logged in.
        // Initialize all UI components.

        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open_nav, R.string.close_nav)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        rvRecords = findViewById(R.id.rvRecords)
        tvUserStatus = findViewById(R.id.tvUserStatus)
        btnAction = findViewById(R.id.btnAction)
        tvTodayDateLabel = findViewById(R.id.tvTodayDateLabel)
        blinkingDot = findViewById(R.id.blinking_dot)

        val userName = "کاربر ۱۲۳" // This can be fetched from server later
        val headerView = navigationView.getHeaderView(0)
        val navHeaderName = headerView.findViewById<TextView>(R.id.tvNavHeaderName)
        navHeaderName.text = userName

        val btnCloseNav = headerView.findViewById<ImageButton>(R.id.btnCloseNav)
        btnCloseNav.setOnClickListener { 
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        val today = DateConverter.toShamsi(Date())
        tvTodayDateLabel.text = "ورود و خروج های امروز (${DateConverter.toPersianNumbers(DateConverter.formatShamsiDate(today))})"

        adapter = RecordAdapter(records,
            { record -> editEnterTime(record) },
            { record -> editExitTime(record) },
            { record -> editDeductions(record) }
        )
        rvRecords.adapter = adapter
        rvRecords.layoutManager = LinearLayoutManager(this)

        setupSwipeToDelete()

        btnAction.setOnClickListener { 
            handleActionClick()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called. Fetching records.")
        // The only check needed is if the token has expired, which will be handled by the API call.
        fetchRecords()
    }

    private fun getAuthToken(): String {
        val token = sharedPreferences.getString("token", "") // Read fresh token
        return "Bearer $token"
    }

    private fun handleAuthFailure(showToast: Boolean = true) {
        Log.w(TAG, "handleAuthFailure called. Redirecting to LoginActivity.")
        // Clear the token that is no longer valid
        sharedPreferences.edit().remove("token").commit()
        
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        if (showToast) {
            Toast.makeText(this, "نشست شما منقضی شده است. لطفاً دوباره وارد شوید.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleActionClick() {
        lifecycleScope.launch {
            try {
                if (isEntry) {
                    val newRecord = Record(id = "temp-" + System.currentTimeMillis(), enterTime = System.currentTimeMillis(), exitTime = null, deductions = 0, type = RecordType.WORK)
                    val response = RetrofitClient.instance.addLog(getAuthToken(), newRecord)
                    if(response.isSuccessful && response.body() != null) {
                        val createdRecord = response.body()!!
                        database.logDao().insertLog(LogEntity(id = createdRecord.id, enterTime = createdRecord.enterTime, exitTime = createdRecord.exitTime, deductions = createdRecord.deductions, type = createdRecord.type ?: RecordType.WORK))
                        fetchRecords()
                    } else if (response.code() == 401 || response.code() == 403) {
                        handleAuthFailure()
                    } else {
                         Toast.makeText(this@MainActivity, "خطا در ثبت ورود", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val lastUnfinishedLog = database.logDao().getLastUnfinishedLog()
                    if (lastUnfinishedLog != null) {
                        lastUnfinishedLog.exitTime = System.currentTimeMillis()
                        val recordToUpdate = Record(lastUnfinishedLog.id.toString(), lastUnfinishedLog.enterTime, lastUnfinishedLog.exitTime, lastUnfinishedLog.deductions, lastUnfinishedLog.type)
                        val response = RetrofitClient.instance.updateLog(getAuthToken(), lastUnfinishedLog.id.toString(), recordToUpdate)
                        if(response.isSuccessful) {
                            database.logDao().updateLog(lastUnfinishedLog)
                            fetchRecords()
                        } else if (response.code() == 401 || response.code() == 403) {
                            handleAuthFailure()
                        } else {
                            Toast.makeText(this@MainActivity, "خطا در ثبت خروج", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchRecords() {
        if (!::adapter.isInitialized) {
            Log.e(TAG, "fetchRecords called but adapter is not initialized!")
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getLogs(getAuthToken())
                if(response.isSuccessful && response.body() != null) {
                    val serverRecords = response.body()!!
                    val logEntities = serverRecords.map { 
                        LogEntity(id = it.id, enterTime = it.enterTime, exitTime = it.exitTime, deductions = it.deductions, type = it.type ?: RecordType.WORK) 
                    }
                    database.logDao().clearAndInsert(logEntities)
                } else if (response.code() == 401 || response.code() == 403) {
                    handleAuthFailure()
                    return@launch
                }
                
                val startOfDay = getStartOfDay(Date())
                val endOfDay = getEndOfDay(Date())

                val savedLogs = database.logDao().getLogsBetween(startOfDay.time, endOfDay.time)
                records.clear()
                records.addAll(savedLogs.map { Record(id = it.id, enterTime = it.enterTime, exitTime = it.exitTime, deductions = it.deductions, type = it.type) })
                adapter.notifyDataSetChanged()

                val lastUnfinishedLog = database.logDao().getLastUnfinishedLog()
                if (lastUnfinishedLog == null) {
                    updateUiForEnter()
                } else {
                    updateUiForExit()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                    database.logDao().deleteLogById(record.id)
                    records.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    Toast.makeText(this@MainActivity, "رکورد حذف شد", Toast.LENGTH_SHORT).show()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleAuthFailure()
                } else {
                    Toast.makeText(this@MainActivity, "خطا در حذف رکورد", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
                adapter.notifyItemChanged(position)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_archive -> startActivity(Intent(this, ArchiveLandingActivity::class.java))
            R.id.nav_request_leave -> startActivity(Intent(this, RequestLeaveActivity::class.java))
            R.id.nav_logout -> {
                // Clear the token and redirect to login
                Log.d(TAG, "Logout clicked. Clearing token.")
                sharedPreferences.edit().remove("token").commit()
                handleAuthFailure(false)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
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
                    database.logDao().updateLog(LogEntity(record.id, record.enterTime, record.exitTime, record.deductions, record.type ?: RecordType.WORK))
                    fetchRecords()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleAuthFailure()
                } else {
                    Toast.makeText(this@MainActivity, "خطا در به‌روزرسانی رکورد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                 e.printStackTrace()
                 Toast.makeText(this@MainActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun updateUiForEnter() {
        isEntry = true
        btnAction.text = "ورود"
        btnAction.setIconResource(android.R.drawable.ic_input_get)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        tvUserStatus.text = "خارج از وضعیت کاری"
        tvUserStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        blinkingDot.visibility = View.GONE
        blinkingDot.clearAnimation()
    }

    private fun updateUiForExit() {
        isEntry = false
        btnAction.text = "خروج"
        btnAction.setIconResource(android.R.drawable.ic_menu_upload)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        tvUserStatus.text = "در وضعیت کاری"
        tvUserStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        blinkingDot.visibility = View.VISIBLE
        val blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)
        blinkingDot.startAnimation(blinkAnimation)
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

    private fun setupSwipeToDelete(){
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if(records[viewHolder.adapterPosition].type == RecordType.WORK){
                    showDeleteConfirmationDialog(viewHolder.adapterPosition)
                } else {
                    Toast.makeText(this@MainActivity, "امکان حذف مرخصی از اینجا وجود ندارد", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                }
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                if (records.getOrNull(viewHolder.adapterPosition)?.type != RecordType.WORK) return

                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.RED)
                val deleteIcon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_delete)

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

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(rvRecords)
    }
}