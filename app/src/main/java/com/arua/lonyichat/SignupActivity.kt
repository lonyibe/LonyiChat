package com.arua.lonyichat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
// ADDED IMPORT for safer coroutine management
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
// REMOVED 'import kotlinx.coroutines.GlobalScope'
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SignupActivity : AppCompatActivity() {

    // Change to lateinit var initialization using the KTX extension to allow the
    // Android SDK to initialize Firebase before auth is accessed.
    private val auth by lazy { Firebase.auth }

    // 🌟 UPDATED: Using the provided Vercel domain
    private val VERCEL_API_BASE_URL = "https://lonyichat-backend.vercel.app"
    private val HTTP_CLIENT = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_signup)

        // ⚠️ REMOVED: The manual assignment line "auth = Firebase.auth" was here.
        // It caused the crash because it was too early. Using `by lazy` above fixes this.

        // Find the views
        val editTextName: TextInputEditText = findViewById(R.id.editTextName)
        val editTextEmail: TextInputEditText = findViewById(R.id.editTextEmail)
        val editTextPhone: TextInputEditText = findViewById(R.id.editTextPhone)
        val editTextAge: TextInputEditText = findViewById(R.id.editTextAge)
        val editTextCountry: TextInputEditText = findViewById(R.id.editTextCountry)
        val editTextPassword: TextInputEditText = findViewById(R.id.editTextPassword)
        val editTextConfirmPassword: TextInputEditText = findViewById(R.id.editTextConfirmPassword)
        val buttonSignup: MaterialButton = findViewById(R.id.buttonSignup)
        val buttonLoginLink: TextView = findViewById(R.id.buttonLoginLink)

        buttonSignup.setOnClickListener {
            handleSignup(
                editTextName, editTextEmail, editTextPhone, editTextAge,
                editTextCountry, editTextPassword, editTextConfirmPassword, buttonSignup
            )
        }

        buttonLoginLink.setOnClickListener {
            finish()
        }
    }

    private fun handleSignup(
        nameInput: TextInputEditText, emailInput: TextInputEditText, phoneInput: TextInputEditText,
        ageInput: TextInputEditText, countryInput: TextInputEditText, passwordInput: TextInputEditText,
        confirmPasswordInput: TextInputEditText, signupButton: MaterialButton
    ) {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val age = ageInput.text.toString().trim()
        val country = countryInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || age.isEmpty() || country.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_LONG).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_LONG).show()
            return
        }

        signupButton.isEnabled = false
        signupButton.text = "Signing Up..."

        // Step 1: Firebase Authentication (Email/Password)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        Toast.makeText(this, "Authentication successful. Saving profile...", Toast.LENGTH_SHORT).show()

                        // Step 2: Send extended profile data to Vercel API
                        sendProfileDataToVercel(firebaseUser.uid, name, email, phone, age, country)
                    } else {
                        onSignupFailed("Firebase user is null after creation.")
                    }
                } else {
                    onSignupFailed("Authentication failed: ${task.exception?.localizedMessage}")
                }
                signupButton.isEnabled = true
                signupButton.text = "Sign Up"
            }
    }

    private fun sendProfileDataToVercel(userId: String, name: String, email: String, phone: String, age: String, country: String) {
        val json = JSONObject()
        json.put("userId", userId)
        json.put("name", name)
        json.put("email", email)
        json.put("phone", phone)
        json.put("age", age)
        json.put("country", country)
        json.put("photoUrl", "")

        val requestBody = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$VERCEL_API_BASE_URL/signup-profile")
            .post(requestBody)
            .build()

        // FIX: Changed GlobalScope to lifecycleScope to prevent crashes/ANRs during navigation
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                HTTP_CLIENT.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    launch(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@SignupActivity, "Registration complete! Welcome!", Toast.LENGTH_LONG).show()
                            val intent = Intent(this@SignupActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            val errorMsg = try {
                                JSONObject(responseBody ?: "").getString("message")
                            } catch (e: Exception) {
                                "Server responded with error code: ${response.code}"
                            }
                            onSignupFailed("Profile save failed: $errorMsg")
                        }
                    }
                }
            } catch (e: IOException) {
                launch(Dispatchers.Main) {
                    onSignupFailed("Network error: Cannot reach Vercel API.")
                }
            }
        }
    }

    private fun onSignupFailed(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}