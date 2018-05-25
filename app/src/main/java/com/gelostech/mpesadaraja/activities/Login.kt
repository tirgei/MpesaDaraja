package com.gelostech.mpesadaraja.activities

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import com.gelostech.mpesadaraja.R
import com.gelostech.mpesadaraja.User
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.mikepenz.fontawesome_typeface_library.FontAwesome
import com.mikepenz.iconics.IconicsDrawable
import kotlinx.android.synthetic.main.activity_login.*
import org.jetbrains.anko.toast
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.iid.FirebaseInstanceId


class Login : AppCompatActivity() {

    private var verificationInProgress = false
    private var phone:String? = null

    private var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks? = null
    private var forceResendToken:PhoneAuthProvider.ForceResendingToken? = null
    private var verificationId:String? = null

    private lateinit var settings: SharedPreferences
    private lateinit var auth: FirebaseAuth

    companion object {
        const val KEY_VERIFY_IN_PROGRESS = "VERIFY_IN_PROGRESS"
        const val STATE_VERIFY_SUCCESS = 0
        const val STATE_VERIFY_FAILED = 1
        const val STATE_CODE_SENT = 2
        const val STATE_SIGNED_IN = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (FirebaseAuth.getInstance().currentUser != null){
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState)
        }
        initViews()

        auth = FirebaseAuth.getInstance()
        settings = PreferenceManager.getDefaultSharedPreferences(this)

        callbacks = object: PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(p0: PhoneAuthCredential?) {
                updateUi(STATE_VERIFY_SUCCESS,p0)
                signInWithPhoneAuthCredential(p0!!)
            }

            override fun onVerificationFailed(p0: FirebaseException?) {
                verificationInProgress = false

                when (p0) {
                    is FirebaseAuthInvalidCredentialsException -> phone_text.error = ("Invalid phone number.")
                    is FirebaseTooManyRequestsException -> Log.d(javaClass.simpleName, "Quota error: ${p0.localizedMessage}")
                    else -> Log.d(javaClass.simpleName, p0?.localizedMessage)
                }
            }

            override fun onCodeSent(p0: String?, p1: PhoneAuthProvider.ForceResendingToken?) {
                super.onCodeSent(p0, p1)
                verificationId = p0
                forceResendToken = p1
                updateUi(STATE_CODE_SENT)
            }
        }
    }

    private fun initViews() {
        phoneIcon.setImageDrawable(IconicsDrawable(this).icon(FontAwesome.Icon.faw_phone).color(Color.BLACK).sizeDp(25))

        signinBtn.setOnClickListener {
            if(phone_text.text.isNotBlank()){
                val phone = formattedPhone(phone_text.text.toString())
                if(phone != null){
                    verifyPhone(phone)
                }
            }else{
                phone_text.error = "Required"
            }
        }

        change_number.setOnClickListener{
            phone_ui.visibility = View.VISIBLE
            verify_ui.visibility = View.GONE
        }

        verify.setOnClickListener {
            verifyPhoneNumberWithCode()
        }
    }

    private fun verifyPhone(phone:String) {
        this@Login.phone = phone
        verify_msg.text = "Please enter the 6-digit code from the SMS we've sent to $phone"
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phone,
                60.toLong(),
                TimeUnit.SECONDS,
                this,
                callbacks!!)

        verificationInProgress = true
    }

    private fun updateUi(state:Int,credential: PhoneAuthCredential? = null){
        when(state){
            STATE_VERIFY_SUCCESS ->{
                phone_ui.visibility = View.GONE
                verify_ui.visibility = View.VISIBLE
                if (credential != null) {
                    if (credential.smsCode != null) {
                        pinView.setText(credential.smsCode)
                    } else {
                        pinView.setText("------")
                    }
                }
            }
            STATE_VERIFY_FAILED ->{

            }
            STATE_CODE_SENT ->{
                phone_ui.visibility = View.GONE
                verify_ui.visibility = View.VISIBLE
            }
            STATE_SIGNED_IN ->{
                phone_ui.visibility = View.VISIBLE
                verify_ui.visibility = View.GONE
            }
        }
    }

    private fun verifyPhoneNumberWithCode() {
        if(verificationId == null || pinView.text.toString() == "------" || pinView.text.isEmpty()){
            toast("Please enter the 6 digit code")
            return
        }
        val credential = PhoneAuthProvider.getCredential(verificationId!!, pinView.text.toString())
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, { task ->
                    if (task.isSuccessful) {
                        val user = task.result.user
                        Log.d(javaClass.simpleName, "signInWithCredential:success: ${user.uid}")

                        createUser(user)

                    } else {
                        Log.w(javaClass.simpleName, "signInWithCredential:failure", task.exception)
                    }
                })
    }

    private fun createUser(user: FirebaseUser) {
        val dbRef = FirebaseDatabase.getInstance().reference

        val newUser = User()
        newUser.id = user.uid
        newUser.username = "Vincent Tirgei"
        newUser.phone = user.phoneNumber!!.toString()
        newUser.token = FirebaseInstanceId.getInstance().token!!.toString()
        newUser.balance = 0

        dbRef.child("users").child(user.uid).setValue(newUser)

        startActivity(Intent(this@Login, MainActivity::class.java))
        finish()
    }

    private fun formattedPhone(phn:String):String? {
        val dialCode = "+254"
        if(TextUtils.isEmpty(phn)){
            phone_text.error = "Required"
            return null
        }
        if(phn.length < 5){
            phone_text.error = "Incorrect format"
            return null
        }
        var number:String = phn
        try{
            if(!number.startsWith(dialCode)){
                number = dialCode+number
            }
            val pnu = PhoneNumberUtil.getInstance()
            val pn = pnu.parse(number, "KEN")
            number = pnu.format(pn, PhoneNumberUtil.PhoneNumberFormat.E164)
        }catch (e:Exception){}
        return number
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putBoolean(KEY_VERIFY_IN_PROGRESS, verificationInProgress)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        verificationInProgress = savedInstanceState.getBoolean(KEY_VERIFY_IN_PROGRESS)
    }

    public override fun onStart() {
        super.onStart()
        if (verificationInProgress && phone != null) {
            verifyPhone(phone!!)
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }

}
