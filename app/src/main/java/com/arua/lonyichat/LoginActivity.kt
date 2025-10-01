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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    // Initialize Firebase Auth using a lazy delegate for proper timing
    private val auth by lazy { Firebase.auth }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is already signed in (quick check before showing login UI)
        if (auth.currentUser != null) {
            navigateToMainScreen()
            return
        }

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
     * Handles Firebase Email/Password Authentication.
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

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, navigate to Main
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    navigateToMainScreen()
                } else {
                    // If sign in fails, display a message to the user.
                    val message = task.exception?.localizedMessage ?: "Login failed."
                    Toast.makeText(this, "Login failed: $message", Toast.LENGTH_LONG).show()
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
