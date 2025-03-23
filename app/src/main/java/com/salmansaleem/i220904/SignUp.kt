package com.salmansaleem.i220904

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SignUp : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        val nameField = findViewById<EditText>(R.id.name)
        val usernameField = findViewById<EditText>(R.id.username)
        val phoneField = findViewById<EditText>(R.id.phonenumber)
        val emailField = findViewById<EditText>(R.id.email1)
        val passwordField = findViewById<EditText>(R.id.password)
        val signUpButton = findViewById<Button>(R.id.signup1)

        signUpButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val username = usernameField.text.toString().trim()
            val phone = phoneField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (validateInputs(name, username, phone, email, password)) {
                checkUsernameAvailability(name, username, phone, email, password)
            }
        }

        var btn1 = findViewById<TextView>(R.id.login2)
        btn1.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }
    }


    private fun validateInputs(name: String, username: String, phone: String, email: String, password: String): Boolean {
        if (name.isEmpty() || username.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return false
        }
        if (phone.length != 11 || !phone.all { it.isDigit() }) {
            Toast.makeText(this, "Phone number must be 11 digits", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun checkUsernameAvailability(name: String, username: String, phone: String, email: String, password: String) {
        database.orderByChild("username").equalTo(username).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(this@SignUp, "Username already taken, choose another", Toast.LENGTH_SHORT).show()
                } else {
                    registerUser(name, username, phone, email, password)
               }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SignUp, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
           }
        })
    }

    private fun registerUser(name: String, username: String, phone: String, email: String, password: String) {
        var signup1 = findViewById<Button>(R.id.signup1)
        signup1.isEnabled = false // Disable button during registration

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val user = User(uid, name, username, phone, email)

                    database.child(uid).setValue(user)
                        .addOnCompleteListener { dbTask ->
                            signup1.isEnabled = true // Re-enable button
                            if (dbTask.isSuccessful) {
                                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, Login::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "Database error: ${dbTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    val errorMessage = when (task.exception?.message) {
                        "The email address is already in use by another account." ->
                            "This email is already registered"

                        "The email address is badly formatted." ->
                            "Invalid email format"

                        else -> "Registration failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }








}

