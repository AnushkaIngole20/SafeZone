package com.example.safezone

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val sos = findViewById<View>(R.id.btnSOS)
        val contacts = findViewById<View>(R.id.btnContacts)
        val location = findViewById<View>(R.id.btnLocation)
        val services = findViewById<View>(R.id.btnServices)
        val report = findViewById<View>(R.id.btnReport)
        val assistant = findViewById<View>(R.id.btnAssistant)
        val history = findViewById<View>(R.id.btnHistory)
        val safetyTips = findViewById<View>(R.id.btnSafetyTips)
        val profile = findViewById<View>(R.id.btnProfile)

        sos.setupTouchFeedback {
            startActivity(Intent(this, SOSActivity::class.java))
        }
        contacts.setupTouchFeedback {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        }
        location.setupTouchFeedback {
            startActivity(Intent(this, LocationActivity::class.java))
        }
        services.setupTouchFeedback {
            startActivity(Intent(this, EmergencyServicesActivity::class.java))
        }
        report.setupTouchFeedback {
            startActivity(Intent(this, ReportIncidentActivity::class.java))
        }
        assistant.setupTouchFeedback {
            startActivity(Intent(this, AIAssistantActivity::class.java))
        }
        history.setupTouchFeedback {
            startActivity(Intent(this, IncidentHistoryActivity::class.java))
        }
        safetyTips.setupTouchFeedback {
            startActivity(Intent(this, SafetyTipsActivity::class.java))
        }
        profile.setupTouchFeedback {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun View.setupTouchFeedback(onClick: () -> Unit) {
        setOnClickListener {
            animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(70)
                .withEndAction {
                    animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(70)
                        .withEndAction {
                            onClick()
                        }
                        .start()
                }
                .start()
        }
    }
}