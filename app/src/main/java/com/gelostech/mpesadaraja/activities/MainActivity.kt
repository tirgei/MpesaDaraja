package com.gelostech.mpesadaraja.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.EditText
import android.text.TextUtils
import android.util.Log
import android.view.View
import com.gelostech.mpesadaraja.utils.Connectivity
import com.gelostech.mpesadaraja.commoners.Constants
import com.gelostech.mpesadaraja.R
import com.gelostech.mpesadaraja.commoners.Config
import com.gelostech.mpesadaraja.utils.NotificationUtils
import com.google.firebase.auth.FirebaseAuth
import com.twigafoods.daraja.Daraja
import com.twigafoods.daraja.DarajaListener
import com.twigafoods.daraja.model.AccessToken
import com.twigafoods.daraja.model.LNMExpress
import com.twigafoods.daraja.model.LNMResult
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {
    private lateinit var daraja: Daraja
    private lateinit var ACCESS_TOKEN: String
    private lateinit var broadcastReceiver: BroadcastReceiver

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    override fun onStart() {
        super.onStart()

        if (FirebaseAuth.getInstance().currentUser == null)
            startActivity(Intent(this, Login::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDaraja()
        initBroadcastReceiver()

        pay.setOnClickListener { if (Connectivity.isConnected(this)) makePayment() else toast("Please connect to the internet")}
    }

    private fun initDaraja() {
        daraja = Daraja.with(Constants.CONSUMER_KEY, Constants.CONSUMER_SECRET, object : DarajaListener<AccessToken>{
            override fun onResult(result: AccessToken) {
                ACCESS_TOKEN = result.access_token
            }

            override fun onError(error: String?) {
                Log.d(javaClass.simpleName, "Access Token error: $error")
            }
        })
    }

    private fun initBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent!!.action == Config.PUSH_NOTIFICATION) {
                    toast(intent.getStringExtra("message"))
                }
            }
        }
    }

    private fun makePayment() {
        toast("Please wait...")

        if (!validated(phoneNumber)) {
            phoneNumber.error = "Please provide a phone number"
            return
        }

        if (!validated(amount)) {
            amount.error = "Please enter amount to pay"
            return
        }

        daraja.requestMPESAExpress(generateLNMExpress(), object : DarajaListener<LNMResult>{
            override fun onResult(result: LNMResult) {
                Log.d(javaClass.simpleName, "LNMResult success: ${result.ResponseDescription}")

                clearFields(phoneNumber, amount)
            }

            override fun onError(error: String?) {
                Log.d(javaClass.simpleName, "LNMResult error: $error")
            }
        })

    }

    private fun generateLNMExpress(): LNMExpress = LNMExpress(
            Constants.PAYBILL,
            Constants.PASSKEY,
            amount.text.toString().trim(),
            phoneNumber.text.toString().trim(),
            Constants.PAYBILL,
            phoneNumber.text.toString().trim(),
            Constants.CALLBACK_URL,
            Constants.ACCOUNT_REF,
            "TEST PAYMENT"
    )

    private fun validated(vararg views: View): Boolean {
        var ok = true
        for (v in views) {
            if (v is EditText) {
                if (TextUtils.isEmpty(v.text.toString())) {
                    ok = false
                }
            }
        }
        return ok
    }

    private fun clearFields(vararg views: View){
        for (v in views) {
            if (v is EditText) {
                v.text = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(Config.REGISTRATION_COMPLETE))
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(Config.PUSH_NOTIFICATION))

        // clear the notification area when the app is opened
        NotificationUtils(this).clearNotifications()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

}
