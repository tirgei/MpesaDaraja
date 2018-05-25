package com.gelostech.mpesadaraja.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.EditText
import android.text.TextUtils
import android.util.Log
import android.view.View
import com.gelostech.mpesadaraja.utils.Connectivity
import com.gelostech.mpesadaraja.commoners.Constants
import com.gelostech.mpesadaraja.R
import com.gelostech.mpesadaraja.User
import com.gelostech.mpesadaraja.commoners.Config
import com.gelostech.mpesadaraja.utils.NotificationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import com.twigafoods.daraja.Daraja
import com.twigafoods.daraja.DarajaListener
import com.twigafoods.daraja.model.AccessToken
import com.twigafoods.daraja.model.LNMExpress
import com.twigafoods.daraja.model.LNMResult
import com.twigafoods.daraja.util.Settings
import okhttp3.*
import org.jetbrains.anko.toast
import java.io.IOException
import com.google.gson.JsonParser
import com.google.gson.JsonObject



class MainActivity : AppCompatActivity() {
    private lateinit var daraja: Daraja
    private lateinit var ACCESS_TOKEN: String
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var checkoutId: String
    private lateinit var userToken:String
    private var isProcessing = false
    private lateinit var dbRef: DatabaseReference
    private lateinit var uid:String

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    override fun onStart() {
        super.onStart()
        // clear the notification area when the app is opened
        NotificationUtils(this).clearNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initDaraja()
        initBroadcastReceiver()

        uid = FirebaseAuth.getInstance().currentUser!!.uid
        dbRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        dbRef.addValueEventListener(balanceListener)

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
                    if (intent.hasExtra("type")) {
                        if (intent.getStringExtra("type") == "topup") {
                            checkStatus()
                        }
                    }
                }
            }
        }
    }

    private val balanceListener = object : ValueEventListener{
        override fun onCancelled(p0: DatabaseError?) {

        }

        override fun onDataChange(p0: DataSnapshot?) {
            if (p0!!.exists()) {
                val user = p0.getValue(User::class.java)
                balance.text = user!!.balance?.toString()
            } else {
                balance.text = "0"
            }
        }
    }

    private fun makePayment() {
        if (isProcessing) {
            toast("Please wait for current transaction to complete...")
            return
        }

        if (!validated(phoneNumber)) {
            phoneNumber.error = "Please provide a phone number"
            return
        }

        if (!validated(amount)) {
            amount.error = "Please enter amount to pay"
            return
        }

        toast("Please wait...")
        userToken = FirebaseInstanceId.getInstance().token!!.toString()

        daraja.requestMPESAExpress(generateLNMExpress(), object : DarajaListener<LNMResult>{
            override fun onResult(result: LNMResult) {
                Log.d(javaClass.simpleName, "LNMResult success: ${result.ResponseDescription}")

                isProcessing = true
                checkoutId = result.CheckoutRequestID
                //Handler().postDelayed({checkStatus(result)}, 5000)
            }

            override fun onError(error: String?) {
                Log.d(javaClass.simpleName, "LNMResult error: $error")
                isProcessing = false
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
            Constants.CALLBACK_URL+"token=$userToken",
            Constants.ACCOUNT_REF,
            "TEST PAYMENT"
    )

    fun checkStatus() {
        val client = OkHttpClient()
        val mediaType = MediaType.parse("application/json")
        val requestBody = RequestBody.create(mediaType, "{\"BusinessShortCode\":\"${Constants.PAYBILL}\" ,\"Password\":\"" + Settings.generatePassword(Constants.PAYBILL, Constants.PASSKEY, Settings.generateTimestamp()) + "\",\"Timestamp\":\"" + Settings.generateTimestamp() + "\",\"CheckoutRequestID\":\"" + checkoutId + "\"}")

        val request = Request.Builder().url(Config.CONFIRMATION_URL)
                .post(requestBody)
                .header("authorization", "Bearer $ACCESS_TOKEN")
                .addHeader("content-type", "application/json")
                .build()

        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call?, e: IOException?) {
                Log.e(TAG, e?.localizedMessage)
                isProcessing = false
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response != null) {
                    isProcessing = false

                    val res = response.body()!!.string()
                    Log.e("CONFIRMATION RESPONSE", res)
                    val jsonObject = JsonParser().parse(res).asJsonObject

                    val resultCode = jsonObject.get("ResultCode").asInt

                    runOnUiThread {
                        when(resultCode) {
                            0 -> {
                                toast("Payment Successful")
                                updateBalance()
                            }
                            else -> toast("Payment failed. Please try again")
                        }
                    }

                }
            }
        })
    }

    private fun updateBalance() {
        val client = OkHttpClient()
        val url = HttpUrl.parse(Constants.UPDATE_BALANCE_URL)
        val urlBuilder = url!!.newBuilder()

        urlBuilder.addQueryParameter("uid", uid)
        urlBuilder.addQueryParameter("checkoutId", checkoutId)
        urlBuilder.addQueryParameter("time", System.currentTimeMillis().toString())
        urlBuilder.addQueryParameter("userToken", FirebaseInstanceId.getInstance().token)
        urlBuilder.addQueryParameter("description", "Balance top up")
        urlBuilder.addQueryParameter("amount", amount.text.toString().trim())

        val transactionObject = urlBuilder.build()
        Log.e("OBJECT", transactionObject.toString())

        val request = Request.Builder().url(transactionObject.toString()).build()
        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call?, e: IOException?) {
                Log.e(TAG, e?.localizedMessage)
            }

            override fun onResponse(call: Call?, response: Response?) {
                if (response != null) {
                    val res = response.body()!!.string()
                    Log.e("Update balance res:", res)
                    clearFields(phoneNumber, amount)
                }
            }
        })
    }

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

    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        dbRef.removeEventListener(balanceListener)
        super.onDestroy()
    }

}
