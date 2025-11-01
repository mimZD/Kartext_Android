package org.eshragh.kartext.models

import com.google.gson.annotations.SerializedName

enum class RecordType {
    @SerializedName("work")
    WORK,
    @SerializedName("hourly_leave")
    HOURLY_LEAVE,
    @SerializedName("daily_leave")
    DAILY_LEAVE
}

data class Record(
    @SerializedName("id") val id: String,
    @SerializedName("enter_time") var enterTime: Long,
    @SerializedName("exit_time") var exitTime: Long?,
    @SerializedName("deductions") var deductions: Long,
    // Make type nullable as it might not be in the server response
    @SerializedName("type") val type: RecordType? 
)


data class LoginRequest(
    val username: String,
    val password: String
)

// This should wrap the actual login data from the server
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: LoginData
)


data class LoginData(
    val token: String,
    val user: User
)

data class User(
    val id: Int,
    val username: String
)

data class CreateLeaveRequest(
    @SerializedName("type") val type: String, // "daily" or "hourly"
    @SerializedName("date") val date: Long, // Timestamp for the day of leave
    @SerializedName("start_time") val startTime: Long? = null, // Only for hourly
    @SerializedName("end_time") val endTime: Long? = null // Only for hourly
)
