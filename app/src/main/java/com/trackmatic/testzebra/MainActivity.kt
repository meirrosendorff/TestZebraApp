package com.trackmatic.testzebra

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trackmatic.testzebra.databinding.ActivityMainBinding
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var macAddress: String? = null

    /**
     * Allows pairing to zebra printer using nfc, we receive the mac address via nfc and then print
     */
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val nfcIntent by lazy { PendingIntent.getActivity(this, 0, Intent(this, this.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.connect.setOnClickListener {
            lifecycleScope.launch {
                connectAndPrint(macAddress ?: "", binding.name.text.toString())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, nfcIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }


    /**
     * This allows pairing without printing anything, just opens and closes a connection
     */
    private fun connect(address: String) {
        try {
            val thePrinterConn: Connection = BluetoothConnection(address)
            thePrinterConn.open()
            thePrinterConn.close()
            macAddress = address
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Connect and print string
     */
    private fun connectAndPrint(address: String, phrase: String) {
        try {
            val thePrinterConn: Connection = BluetoothConnection(address)
            thePrinterConn.open()
            if (thePrinterConn.isConnected) {
                val zplData = "\n\n\n\n\n  $phrase   \n\n\n\n\n" // just some cleanup for now
                thePrinterConn.write(zplData.toByteArray())
                thePrinterConn.close()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val scannedTags = intent?.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (scannedTags != null && scannedTags.isNotEmpty()) {
            val msg: NdefMessage = scannedTags[0] as NdefMessage
            val payload: String = String(msg.records[0].payload)
            val macAddress = payload.split("&").firstOrNull { it.contains("mB=") }?.removePrefix("mB=")
            lifecycleScope.launch {
                connect(macAddress ?: "")
            }
            intent.removeExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
    }

}