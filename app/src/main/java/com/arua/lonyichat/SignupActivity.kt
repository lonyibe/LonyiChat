package com.arua.lonyichat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
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

    private val auth by lazy { Firebase.auth }
    private val VERCEL_API_BASE_URL = "https://lonyichat-backend.vercel.app"
    private val HTTP_CLIENT = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // State variable to manage the current step
    private var currentStep = 1

    // Declare all views for late initialization
    private lateinit var stepOneLayout: View
    private lateinit var stepTwoLayout: View
    private lateinit var buttonNext: MaterialButton
    private lateinit var buttonSignup: MaterialButton
    private lateinit var buttonBack: TextView
    private lateinit var buttonLoginLink: TextView

    private lateinit var editTextName: TextInputEditText
    private lateinit var editTextEmail: TextInputEditText
    private lateinit var editTextPhone: TextInputEditText
    private lateinit var editTextAge: TextInputEditText
    private lateinit var editTextCountry: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var editTextConfirmPassword: TextInputEditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_signup)

        // Find and initialize all views
        stepOneLayout = findViewById(R.id.stepOneLayout)
        stepTwoLayout = findViewById(R.id.stepTwoLayout)
        buttonNext = findViewById(R.id.buttonNext)
        buttonSignup = findViewById(R.id.buttonSignup)
        buttonBack = findViewById(R.id.buttonBack)
        buttonLoginLink = findViewById(R.id.buttonLoginLink)

        editTextName = findViewById(R.id.editTextName)
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPhone = findViewById(R.id.editTextPhone)
        editTextAge = findViewById(R.id.editTextAge)
        editTextCountry = findViewById(R.id.editTextCountry)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)

        // Set initial state (Step 1 visible, Step 2 hidden)
        showStep(1)

        // Set Listeners
        buttonNext.setOnClickListener {
            handleNextOrSignup()
        }

        buttonSignup.setOnClickListener {
            handleNextOrSignup()
        }

        buttonBack.setOnClickListener {
            showStep(1)
        }

        buttonLoginLink.setOnClickListener {
            finish()
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        if (step == 1) {
            stepOneLayout.visibility = View.VISIBLE
            stepTwoLayout.visibility = View.GONE
        } else {
            stepOneLayout.visibility = View.GONE
            stepTwoLayout.visibility = View.VISIBLE
        }
    }

    private fun handleNextOrSignup() {
        if (currentStep == 1) {
            // Step 1: Validate Personal Info and move to Step 2
            val name = editTextName.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val phone = editTextPhone.text.toString().trim()
            val age = editTextAge.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || age.isEmpty()) {
                Toast.makeText(this, "Please fill in all personal information fields.", Toast.LENGTH_LONG).show()
                return
            }
            // All good, proceed to step 2
            showStep(2)

        } else if (currentStep == 2) {
            // Step 2: Validate Account Details and initiate signup
            val country = editTextCountry.text.toString().trim()
            val password = editTextPassword.text.toString()
            val confirmPassword = editTextConfirmPassword.text.toString()

            if (country.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all account details fields.", Toast.LENGTH_LONG).show()
                return
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_LONG).show()
                return
            }

            // All good, initiate Firebase Signup
            val name = editTextName.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val phone = editTextPhone.text.toString().trim()
            val age = editTextAge.text.toString().trim()

            initiateFirebaseSignup(name, email, phone, age, country, password)
        }
    }

    private fun initiateFirebaseSignup(
        name: String, email: String, phone: String, age: String, country: String, password: String
    ) {
        buttonSignup.isEnabled = false
        buttonSignup.text = "Signing Up..."

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

                // Re-enable button in case of instant failure
                if (!task.isSuccessful) {
                    buttonSignup.isEnabled = true
                    buttonSignup.text = "Sign Up"
                }
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

                            // Re-enable button on failure
                            buttonSignup.isEnabled = true
                            buttonSignup.text = "Sign Up"
                        }
                    }
                }
            } catch (e: IOException) {
                launch(Dispatchers.Main) {
                    onSignupFailed("Network error: Cannot reach Vercel API.")
                    // Re-enable button on network error
                    buttonSignup.isEnabled = true
                    buttonSignup.text = "Sign Up"
                }
            }
        }
    }

    private fun onSignupFailed(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}