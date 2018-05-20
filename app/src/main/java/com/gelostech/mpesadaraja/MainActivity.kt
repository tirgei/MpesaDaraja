package com.gelostech.mpesadaraja

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.EditText
import android.text.TextUtils
import android.util.Log
import android.view.View
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

    override fun onStart() {
        super.onStart()

        if (FirebaseAuth.getInstance().currentUser == null)
            startActivity(Intent(this, Login::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDaraja()

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
            Constants.TEST_MSISDN,
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

}
