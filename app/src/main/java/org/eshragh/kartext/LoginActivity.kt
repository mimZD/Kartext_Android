package org.eshragh.kartext

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.eshragh.kartext.api.RetrofitClient
import org.eshragh.kartext.models.LoginRequest

class LoginActivity : AppCompatActivity() {

    private val TAG = "KartextDebug_Login"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null)

        if (token != null) {
            Log.d(TAG, "Token found, starting MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        Log.d(TAG, "Token not found, setting login content view")
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.login(LoginRequest(username, password))
                        if (response.isSuccessful && response.body() != null) {
                            val newToken = response.body()!!.   data.token
                            Log.d(TAG, "Login successful. New token received.")
                            
                            sharedPreferences.edit(commit = true) { putString("token", newToken) }
                            Log.d(TAG, "Token saved synchronously.")

                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            Log.d(TAG, "Starting MainActivity and finishing LoginActivity.")
                            startActivity(intent)
                            finish()

                        } else {
                            Log.w(TAG, "Login failed. Response: ${response.errorBody()?.string()}")
                            Toast.makeText(this@LoginActivity, "نام کاربری یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Login exception", e)
                        Toast.makeText(this@LoginActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
    }
}