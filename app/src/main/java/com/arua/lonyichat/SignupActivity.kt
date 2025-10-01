package com.arua.lonyichat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.arua.lonyichat.R

class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This line allows the layout to be drawn behind the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_signup)

        // Find the views - Existing
        val editTextEmail: TextInputEditText = findViewById(R.id.editTextEmail)
        val editTextPassword: TextInputEditText = findViewById(R.id.editTextPassword)
        val editTextConfirmPassword: TextInputEditText = findViewById(R.id.editTextConfirmPassword)
        val buttonSignup: MaterialButton = findViewById(R.id.buttonSignup)
        val buttonLoginLink: TextView = findViewById(R.id.buttonLoginLink)

        // Find the views - New Fields
        val editTextName: TextInputEditText = findViewById(R.id.editTextName)
        val editTextPhone: TextInputEditText = findViewById(R.id.editTextPhone)
        val editTextAge: TextInputEditText = findViewById(R.id.editTextAge)
        val editTextCountry: TextInputEditText = findViewById(R.id.editTextCountry)

        buttonSignup.setOnClickListener {
            val name = editTextName.text.toString()
            val email = editTextEmail.text.toString()
            val phone = editTextPhone.text.toString()
            val age = editTextAge.text.toString()
            val country = editTextCountry.text.toString()
            val password = editTextPassword.text.toString()
            val confirmPassword = editTextConfirmPassword.text.toString()

            // Update validation to check all new fields as well
            if (name.isNotEmpty() && email.isNotEmpty() && phone.isNotEmpty() && age.isNotEmpty() && country.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()) {
                if (password == confirmPassword) {
                    // TODO: Add signup logic here
                    Toast.makeText(this, "Sign Up Successful (placeholder)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        buttonLoginLink.setOnClickListener {
            // Finish this activity and go back to the Login screen
            finish()
        }
    }
}