package com.hid.bluetoothscannerapp.blescanner
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import com.hid.bluetoothscannerapp.MainActivity
import com.hid.bluetoothscannerapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

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

    @SuppressLint("SetJavaScriptEnabled")
    private fun startKeycloakLogin() {
        // Set up WebView and load the Keycloak login page
        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.clearCache(true)
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE

        // Load the Keycloak login URL
        webView.loadUrl("http://192.168.177.242:8081/login")

        // Handle redirection and token extraction
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && url.contains("session_state") && url.contains("code")) {
                    Log.d("TAG", "Redirect URL: $url")
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            delay(2000)
                            webView.evaluateJavascript("(function() { return JSON.stringify(document.body.innerText); })();") { json ->
                                try {
                                    val jsonFormatted = json.replace("\\", "").replace("\"{", "{").replace("}\"", "}")
                                    val gson = Gson()
                                    val trimmedJsonString = jsonFormatted.trim('"')
                                    val responseFromWebView = gson.fromJson(trimmedJsonString, JwtToken::class.java)
                                    Log.d("TAG", "classGson: $responseFromWebView")
                                    storeTokens(responseFromWebView.id_token)
                                } catch (e: Exception) {
                                    Log.e("TAG", "Error parsing JSON or storing token: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TAG", "Error executing JavaScript or coroutine scope: ${e.message}", e)
                        }
                    }
                }

                return false
            }
        }
    }


    private fun storeTokens(atHash: String) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("id_token", atHash)
        editor.apply()


        val retrievedIdToken = sharedPreferences.getString("id_token", null)
        Log.d("StoreTokens", "Retrieved at_hash: $retrievedIdToken")

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()

        // Show a toast message to confirm storage
        Toast.makeText(this@LoginActivity, "id_token: $retrievedIdToken", Toast.LENGTH_LONG).show()
    }


    data class JwtToken(
        val access_token: String,
        val expires_in: Int,
        val refresh_expires_in: Int,
        val refresh_token: String,
        val token_type: String,
        val id_token: String,
        val not_before_policy: Int,
        val session_state: String,
        val scope: String
    )

}
