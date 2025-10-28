package org.eshragh.kartext

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.eshragh.kartext.api.RetrofitClient
import org.eshragh.kartext.models.CreateLeaveRequest
import org.eshragh.kartext.utils.DateConverter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RequestLeaveActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var token: String? = null
    private val calendar = Calendar.getInstance()
    private var selectedStartTime: Calendar? = null
    private var selectedEndTime: Calendar? = null

    private lateinit var btnSelectDate: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var layoutHourlyLeave: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_leave)

        sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        token = sharedPreferences.getString("token", null)

        if (token == null) {
            handleAuthFailure(false)
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        btnSelectDate = findViewById(R.id.btn_select_date)
        btnStartTime = findViewById(R.id.btn_start_time)
        btnEndTime = findViewById(R.id.btn_end_time)
        layoutHourlyLeave = findViewById(R.id.layout_hourly_leave)
        val rgLeaveType = findViewById<RadioGroup>(R.id.rg_leave_type)
        val btnSubmitRequest = findViewById<Button>(R.id.btn_submit_request)

        updateDateButtonText()

        rgLeaveType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_hourly) {
                layoutHourlyLeave.visibility = View.VISIBLE
            } else {
                layoutHourlyLeave.visibility = View.GONE
            }
        }

        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        btnStartTime.setOnClickListener {
            showTimePicker(true)
        }

        btnEndTime.setOnClickListener {
            showTimePicker(false)
        }

        btnSubmitRequest.setOnClickListener {
            submitLeaveRequest(rgLeaveType.checkedRadioButtonId)
        }
    }

    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateButtonText()
        }

        DatePickerDialog(
            this,
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val tempCal = Calendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val selectedTime = Calendar.getInstance()
            selectedTime.timeInMillis = calendar.timeInMillis // Start with the selected date
            selectedTime.set(Calendar.HOUR_OF_DAY, hour)
            selectedTime.set(Calendar.MINUTE, minute)

            if (isStartTime) {
                selectedStartTime = selectedTime
                btnStartTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedTime.time)
            } else {
                selectedEndTime = selectedTime
                btnEndTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedTime.time)
            }
        }

        TimePickerDialog(
            this,
            timeSetListener,
            tempCal.get(Calendar.HOUR_OF_DAY),
            tempCal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateButtonText() {
        val shamsiDate = DateConverter.toShamsi(calendar.time)
        btnSelectDate.text = "تاریخ: ${DateConverter.formatShamsiDate(shamsiDate)}"
    }

    private fun submitLeaveRequest(checkedId: Int) {
        val request: CreateLeaveRequest = when (checkedId) {
            R.id.rb_daily -> {
                // For daily leave, we can set start and end times based on working hours
                val dayStart = calendar.clone() as Calendar
                dayStart.set(Calendar.HOUR_OF_DAY, 8)
                dayStart.set(Calendar.MINUTE, 0)

                val dayEnd = calendar.clone() as Calendar
                dayEnd.set(Calendar.HOUR_OF_DAY, 17) // Assuming 17:00 as end of work
                dayEnd.set(Calendar.MINUTE, 0)

                CreateLeaveRequest(
                    type = "daily",
                    date = calendar.timeInMillis,
                    startTime = dayStart.timeInMillis,
                    endTime = dayEnd.timeInMillis
                )
            }
            R.id.rb_hourly -> {
                if (selectedStartTime == null || selectedEndTime == null) {
                    Toast.makeText(this, "لطفاً زمان شروع و پایان را انتخاب کنید", Toast.LENGTH_SHORT).show()
                    return
                }
                if (selectedStartTime!!.timeInMillis >= selectedEndTime!!.timeInMillis) {
                    Toast.makeText(this, "زمان پایان باید بعد از زمان شروع باشد", Toast.LENGTH_SHORT).show()
                    return
                }
                CreateLeaveRequest(
                    type = "hourly",
                    date = calendar.timeInMillis,
                    startTime = selectedStartTime!!.timeInMillis,
                    endTime = selectedEndTime!!.timeInMillis
                )
            }
            else -> {
                Toast.makeText(this, "لطفاً نوع مرخصی را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return
            }
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.requestLeave("Bearer $token", request)
                if (response.isSuccessful) {
                    Toast.makeText(this@RequestLeaveActivity, "درخواست مرخصی با موفقیت ثبت شد", Toast.LENGTH_LONG).show()
                    finish()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleAuthFailure()
                } else {
                    Toast.makeText(this@RequestLeaveActivity, "خطا در ثبت درخواست", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@RequestLeaveActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleAuthFailure(showToast: Boolean = true) {
        sharedPreferences.edit().remove("token").apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        if (showToast) {
            Toast.makeText(this, "نشست شما منقضی شده است. لطفاً دوباره وارد شوید.", Toast.LENGTH_LONG).show()
        }
    }
}