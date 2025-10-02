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
// REMOVED ALL FIREBASE AUTH IMPORTS
import com.arua.lonyichat.data.ApiService
import androidx.lifecycle.lifecycleScope // ADDED
import kotlinx.coroutines.launch // ADDED

class LoginActivity : AppCompatActivity() {

    // Removed Firebase Auth initialization

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WARNING: There is no check here if the user is already logged in (no Firebase currentUser).
        // A proper solution would use SharedPreferences to store the JWT token and check it here.

        // This line MUST be called before "setContentView"
        installSplashScreen()

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

        // NEW: Call the custom API login function
        lifecycleScope.launch {
            val result = ApiService.login(email, password)

            result.onSuccess {
                // Login success, the JWT token is now stored in ApiService
                Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                navigateToMainScreen()
            }.onFailure { error ->
                // If sign in fails, display a message to the user.
                val message = error.localizedMessage ?: "Login failed due to network error."
                Toast.makeText(this@LoginActivity, "Login failed: $message", Toast.LENGTH_LONG).show()
            }

            // Re-enable button on completion (success or failure)
            loginButton.isEnabled = true
            loginButton.text = "Login"
        }
    }

    /**
     * Navigates the user to the main activity and clears the task stack.
     */
    private fun navigateToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        // Clears all previous activities so the user cannot press back to login
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}