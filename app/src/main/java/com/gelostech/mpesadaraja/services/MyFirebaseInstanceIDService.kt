package com.gelostech.mpesadaraja.services

import android.R.id.edit
import android.content.SharedPreferences
import com.google.android.gms.flags.impl.SharedPreferencesFactory.getSharedPreferences
import android.support.v4.content.LocalBroadcastManager
import android.content.Intent
import android.util.Log
import com.gelostech.mpesadaraja.commoners.Config
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService


class MyFirebaseInstanceIDService : FirebaseInstanceIdService() {

    companion object {
        private val TAG = MyFirebaseInstanceIDService::class.java.simpleName
    }

    override fun onTokenRefresh() {
        super.onTokenRefresh()
        val refreshedToken = FirebaseInstanceId.getInstance().token

        // Saving reg id to shared preferences
        storeRegIdInPref(refreshedToken)

        // sending reg id to your server
        sendRegistrationToServer(refreshedToken)

        // Notify UI that registration has completed, so the progress indicator can be hidden.
        val registrationComplete = Intent(Config.REGISTRATION_COMPLETE)
        registrationComplete.putExtra("token", refreshedToken)
        LocalBroadcastManager.getInstance(this).sendBroadcast(registrationComplete)
    }

    private fun sendRegistrationToServer(token: String?) {
        // sending gcm token to server
        Log.e(TAG, "sendRegistrationToServer: " + token!!)

        val userID = FirebaseAuth.getInstance().currentUser?.uid
        if (userID != null) {
            val dbRef = FirebaseDatabase.getInstance().reference
            dbRef.child("users").child(userID).child("token").setValue(token)
        }
    }

    private fun storeRegIdInPref(token: String?) {
        val pref = applicationContext.getSharedPreferences(Config.SHARED_PREF, 0)
        val editor = pref.edit()
        editor.putString("regId", token)
        editor.apply()
    }
}