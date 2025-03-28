package com.salmansaleem.i220904

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var loginButton: RelativeLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        loginButton = findViewById(R.id.login)

        loginButton.setOnClickListener {
            val enteredUsername = username.text.toString().trim()
            val pass = password.text.toString().trim()

            if (!validateInputs(enteredUsername, pass)) {
                return@setOnClickListener
            }

            loginUser(enteredUsername, pass)
        }

        var btn1 = findViewById<TextView>(R.id.signup)
        btn1.setOnClickListener {
            val intent = Intent(this, SignUp::class.java)
            startActivity(intent)
        }
    }

    private fun validateInputs(username: String, password: String): Boolean {
        when {
            username.isEmpty() -> {
                Toast.makeText(this, "Please enter username", Toast.LENGTH_SHORT).show()
                return false
            }
            password.isEmpty() -> {
                Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show()
                return false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            else -> return true
        }
    }

    private fun loginUser(username: String, password: String) {
        loginButton.isEnabled = false

        val usersRef = FirebaseDatabase.getInstance().getReference("Users")
        usersRef.orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    loginButton.isEnabled = true

                    if (snapshot.exists()) {
                        val userSnapshot = snapshot.children.first()
                        val userData = userSnapshot.getValue(User::class.java)

                        if (userData?.email == null) {
                            Toast.makeText(this@Login,
                                "User data error, please contact support",
                                Toast.LENGTH_SHORT).show()
                            return
                        }

                        auth.signInWithEmailAndPassword(userData.email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this@Login,
                                        "Login Successful!",
                                        Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@Login, Home::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this@Login,
                                        "Login failed: ${task.exception?.message ?: "Unknown error"}",
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        Toast.makeText(this@Login,
                            "Username does not exist!",
                            Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    loginButton.isEnabled = true
                    Toast.makeText(this@Login,
                        "Database Error: ${error.message}",
                        Toast.LENGTH_SHORT).show()
                }
            })
    }
}
