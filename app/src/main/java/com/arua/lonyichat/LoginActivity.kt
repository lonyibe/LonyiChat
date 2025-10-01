package com.arua.lonyichat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.arua.lonyichat.R

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This line MUST be called before "setContentView"
        installSplashScreen()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_login)

        val editTextEmail: TextInputEditText = findViewById(R.id.editTextEmail)
        val editTextPassword: TextInputEditText = findViewById(R.id.editTextPassword)
        val buttonLogin: MaterialButton = findViewById(R.id.buttonLogin)
        val buttonSignup: TextView = findViewById(R.id.buttonSignup)

        buttonLogin.setOnClickListener {
            val email = editTextEmail.text.toString()
            val password = editTextPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                Toast.makeText(this, "Login Successful (placeholder)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSignup.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }
}