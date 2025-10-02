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
import com.arua.lonyichat.data.ApiService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This line MUST be called before "setContentView"
        installSplashScreen()

        // ✨ PERSISTENT LOGIN CHECK: Check if the user is already logged in.
        if (ApiService.getCurrentUserId() != null) {
            // If they are, navigate directly to the main screen.
            navigateToMainScreen()
            return // Stop the rest of the onCreate from running.
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_login)

        val editTextEmail: TextInputEditText = findViewById(R.id.editTextEmail)
        val editTextPassword: TextInputEditText = findViewById(R.id.editTextPassword)
        val buttonLogin: MaterialButton = findViewById(R.id.buttonLogin)
        val buttonSignup: TextView = findViewById(R.id.buttonSignup)

        buttonLogin.setOnClickListener {
            handleLogin(editTextEmail, editTextPassword, buttonLogin)
        }

        buttonSignup.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Handles custom backend JWT Authentication.
     */
    private fun handleLogin(
        emailInput: TextInputEditText,
        passwordInput: TextInputEditText,
        loginButton: MaterialButton
    ) {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password.", Toast.LENGTH_SHORT).show()
            return
        }

        loginButton.isEnabled = false
        loginButton.text = "Logging in..."

        lifecycleScope.launch {
            val result = ApiService.login(email, password)

            result.onSuccess {
                Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                navigateToMainScreen()
            }.onFailure { error ->
                val message = error.localizedMessage ?: "Login failed due to network error."
                Toast.makeText(this@LoginActivity, "Login failed: $message", Toast.LENGTH_LONG).show()
            }

            loginButton.isEnabled = true
            loginButton.text = "Login"
        }
    }

    /**
     * Navigates the user to the main activity and clears the task stack.
     */
    private fun navigateToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}