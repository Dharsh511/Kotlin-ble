package com.hid.bluetoothscannerapp.blescanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hid.bluetoothscannerapp.MainActivity
import com.hid.bluetoothscannerapp.R
import okhttp3.*
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Apply window insets to manage padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val loginButton: Button = findViewById(R.id.login)
        loginButton.setOnClickListener {
            // Start WebView for Keycloak login
            startKeycloakLogin()
        }
    }

    private fun startKeycloakLogin() {
        // Set up WebView and load the Keycloak login page
        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.clearCache(true)
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE

        // Load the Keycloak login URL
        webView.loadUrl("http://192.168.252.124:8081/login")

        // Handle redirection and token extraction
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && url.startsWith("myapp://callback")) {
                    // Extract JWT tokens from the redirect URL
                    handleRedirect(Uri.parse(url))
                    return true
                }
                return false
            }
        }
    }

    private fun handleRedirect(uri: Uri) {
        // Extract the JWT token from the URL
        val idToken = uri.getQueryParameter("id_token")
        val accessToken = uri.getQueryParameter("access_token")
        val refreshToken = uri.getQueryParameter("refresh_token")

        if (idToken != null) {
            // Token is successfully extracted, store it and proceed
            storeTokens(idToken, accessToken, refreshToken)
            Log.d("LoginActivity", "ID Token: $idToken")

            // Make authenticated request with OkHttp
            makeAuthenticatedRequest(idToken)

            // Proceed to the next activity (e.g., main Bluetooth scanner activity)
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("id_token", idToken)
            startActivity(intent)
            finish()
        } else {
            Log.e("LoginActivity", "Error: Tokens not found in URL")
        }
    }

    private fun storeTokens(idToken: String, accessToken: String?, refreshToken: String?) {
        // Save tokens in SharedPreferences
        val sharedPreferences = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("id_token", idToken)
        editor.putString("access_token", accessToken)
        editor.putString("refresh_token", refreshToken)
        editor.apply()
    }

    private fun makeAuthenticatedRequest(token: String) {
        // Make an authenticated request using the token
        val request = Request.Builder()
            .url("http://192.168.252.124:8081/secured-endpoint")
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginActivity", "API request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("LoginActivity", "API response: ${response.body?.string()}")
                } else {
                    Log.e("LoginActivity", "API request unsuccessful: ${response.message}")
                }
            }
        })
    }
}
