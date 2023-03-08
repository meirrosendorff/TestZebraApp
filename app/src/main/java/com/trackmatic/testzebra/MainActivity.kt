package com.trackmatic.testzebra

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trackmatic.testzebra.databinding.ActivityMainBinding
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.graphics.internal.ZebraImageAndroid
import com.zebra.sdk.printer.SGD
import com.zebra.sdk.printer.ZebraPrinterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    /**
     * Allows pairing to zebra printer using nfc, we receive the mac address via nfc and then print
     */
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val nfcIntent by lazy { PendingIntent.getActivity(this, 0, Intent(this, this.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
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
     * Receives NFC message from printer containing the printers mac address
     * If valid mac address
     * Connect to printer and print
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val scannedTags = intent?.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (scannedTags != null && scannedTags.isNotEmpty()) {
            val msg: NdefMessage = scannedTags[0] as NdefMessage
            val payload = String(msg.records[0].payload)
            val macAddress = payload.split("&").firstOrNull { it.contains("mB=") }?.removePrefix("mB=")
            if (macAddress == null) {
                // todo handle invalid NFC scan
                return
            }
            lifecycleScope.launch {
                connectAndPrint(macAddress)
            }
            intent.removeExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
    }


    /**
     * This allows pairing without printing anything, just opens and closes a connection
     */
    private fun connect(address: String) {
        try {
            val thePrinterConn: Connection = BluetoothConnection(address)
            thePrinterConn.open()
            thePrinterConn.close()

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Connect and print string
     */
    private fun connectAndPrint(address: String) {
        try {
            val thePrinterConn: Connection = BluetoothConnection(address)
            thePrinterConn.open()
            val zebraPrinter = ZebraPrinterFactory.getInstance(thePrinterConn)
            if (thePrinterConn.isConnected) {
                val width = SGD.GET("ezpl.print_width", thePrinterConn).toFloatOrNull()
                lifecycleScope.launch {
                    val bitmap = buildBitmap(zplData) ?: return@launch
                    val padding = 20
                    val widthToUse = width?.let { it - 2 * padding }
                    val scale = widthToUse?.let { it / bitmap.width.toFloat() } ?: 1F
                    val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    val zebraImageToPrint = ZebraImageAndroid(resized);
                    zebraPrinter.printImage(zebraImageToPrint, padding, 100, -1, -1, false)
                    thePrinterConn.close()
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    suspend fun buildBitmap(htmlInput: String): Bitmap? {
        val completableBitmap = CompletableDeferred<Bitmap?>()

        WebView.enableSlowWholeDocumentDraw()
        val webView = WebView(this)
        webView.layout(0, 0, 1024, 1)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                if (view == null) {
                    completableBitmap.complete(null)
                    return
                }
                lifecycleScope.launch {
                    view.measure(0, 0)
                    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                    val webViewHeight = view.measuredHeight
                    val width = view.width
                    val image = Bitmap.createBitmap(width, webViewHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(image)
                    view.draw(canvas)
                    completableBitmap.complete(image)
                }
            }

        }

        webView.loadDataWithBaseURL("http://static.trackmatic.co.za/", htmlInput, "text/html", null, "")

        return completableBitmap.await()
    }

    companion object {
        val zplData = """
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>trackmatic</title>
<style type="text/css">
body {
  background-color: #444;
}
.take {
  font-family: Arial, Helvetica, sans-serif;
}
</style>
</head>

<body>
<table width="600" border="0" align="center" cellpadding="0" cellspacing="0">
  <tr>
    <td><table width="100%" border="0" cellspacing="0" cellpadding="0">
      <tr>
        <td align="right" bgcolor="#2ABBF7"><table width="100%" border="0" cellspacing="0" cellpadding="0">
          <tr>
            <td height="25" align="right"><a href="*|ARCHIVE|*" style=" color:#fff; font-family:Arial, Helvetica, sans-serif; font-size:10px; line-height:16px; text-decoration:none;">View in your browser</a></td>
            <td width="20">&nbsp;</td>
          </tr>
        </table></td>
      </tr>
      <tr>
        <td><img style="display:block" name="r2_c1" src="http://static.trackmatic.co.za/templates/email_assets/footer.jpg" width="600" id="r2_c1" alt="" /></td>
      </tr>
      <tr>
        <tr>
          <td><img style="display:block" name="banner" src="http://static.trackmatic.co.za/templates/email_assets/banner_failure.jpg" /></td>
        </tr>
        <td><table width="100%" border="0" cellspacing="0" cellpadding="20">
          <tr>
            <td bgcolor="#FFFFFF">
              <p style="font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#333; line-height:20px;">
                A route was excluded from uploading to Trackmatic. Please review the message below:
              </p>
              <p style="font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#333; line-height:20px; background: #F5F5F5; padding: 10px; margin: 10px;">
                ##MESSAGE##
              </p>
            </td>
          </tr>
        </table></td>
      </tr>
      <tr>
        <td bgcolor="#FFFFFF">&nbsp;</td>
      </tr>
      <tr>
        <td bgcolor="#FFFFFF"><table width="100%" border="0" cellspacing="0" cellpadding="10">
          <tr>
            <td align="center" class="take"><em>take control. <span style="color:#2ABBF7">where &amp; when it counts.</span></em></td>
          </tr>
        </table></td>
      </tr>
      <tr>
        <td bgcolor="#FFFFFF">&nbsp;</td>
      </tr>
      <tr>
        <td bgcolor="#FFFFFF"><table width="100%" border="0" cellspacing="0" cellpadding="10">
          <tr>
            <td align="center"><span style="font-family:Arial, Helvetica, sans-serif; font-weight:normal; font-size:14px; color:#ccc;">Get in touch with us</span></td>
          </tr>
        </table></td>
      </tr>
      <tr>
        <td><table width="100%" border="0" cellspacing="0" cellpadding="0">
          <tr>
            <td bgcolor="#444444">&nbsp;</td>
            <td bgcolor="#444444">&nbsp;</td>
          </tr>
          <tr>
            <td><table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td width="84" bgcolor="#444444">&nbsp;</td>
                <td><img style="display:block" name="r15_c4" src="http://static.trackmatic.co.za/templates/email_assets/icon_tel.png" height="36" id="r15_c4" alt="" /></td>
                <td width="86" bgcolor="#444444">&nbsp;</td>
              </tr>
            </table></td>
            <td><table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td width="84" bgcolor="#444444">&nbsp;</td>
                <td><img style="display:block" name="r15_c12" src="http://static.trackmatic.co.za/templates/email_assets/icon_email.png" height="36" id="r15_c12" alt="" /></td>
                <td width="86" bgcolor="#444444">&nbsp;</td>
              </tr>
            </table></td>
            <td><table width="100%" border="0" cellspacing="0" cellpadding="0">
              <tr>
                <td width="84" bgcolor="#444444">&nbsp;</td>
                <td><img style="display:block" name="r15_c22" src="http://static.trackmatic.co.za/templates/email_assets/icon_web.png" height="36" id="r15_c22" alt="" /></td>
                <td width="86" bgcolor="#444444">&nbsp;</td>
              </tr>
            </table></td>
          </tr>
          <tr>
            <td height="15" align="center" bgcolor="#444444"><span style="font-family:Arial, Helvetica, sans-serif; font-size:11px; color:#fff;">+27 (11) 531 3400</span></td>
            <td align="center" bgcolor="#444444"><a href="mailto:info@trackmatic.co.za" style="font-family:Arial, Helvetica, sans-serif; font-size:11px; color:#fff; text-decoration:none;">info@trackmatic.co.za</a></td>
            <td align="center" bgcolor="#444444"><a href="http://www.trackmatic.co.za" target="_blank" style="font-family:Arial, Helvetica, sans-serif; font-size:11px; color:#2ABBF7; text-decoration:none;">trackmatic.co.za</a></td>
          </tr>
        </table></td>
      </tr>
            <tr>
              <td><img style="display:block" name ="footer" src="http://static.trackmatic.co.za/templates/email_assets/footer.jpg" /></td>
            </tr>
          </table></td>
      </tr>
    </table></td>
  </tr>
</table>
</body>
</html>
                """.trimIndent()
    }
}