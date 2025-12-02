package com.nearpays.netplus.netplus_contactless

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.danbamitale.epmslib.entities.CardData
import com.danbamitale.epmslib.entities.KeyHolder
import com.danbamitale.epmslib.entities.clearPinKey
import com.danbamitale.epmslib.extensions.formatCurrencyAmount
import com.dsofttech.dprefs.utils.DPrefs
import com.google.gson.Gson
import com.nearpays.netplus.netplus_contactless.models.CardResult
import com.nearpays.netplus.netplus_contactless.models.Status
import com.netpluspay.contactless.sdk.start.UpdatedContactlessSdk
import com.netpluspay.contactless.sdk.utils.ContactlessReaderResult
import com.netpluspay.nibssclient.models.IsoAccountType
import com.netpluspay.nibssclient.models.MakePaymentParams
import com.netpluspay.nibssclient.models.UserData
import com.netpluspay.nibssclient.service.NetposPaymentClient
import com.pixplicity.easyprefs.library.Prefs
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** NetplusContactlessPlugin */
class NetplusContactlessPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var activityPluginBinding: ActivityPluginBinding? = null

    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private lateinit var enableBtLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private var pendingCall: MethodCall? = null
    private var pendingResult: Result? = null

    private val tag = "NetPlusContactlessFlutterPlugin"
    private val channelName = "netplus_contactless"
    private val gson: Gson = Gson()
    private var userData: UserData? = null
    var netposPaymentClient: NetposPaymentClient = NetposPaymentClient

    private val compositeDisposable: io.reactivex.disposables.CompositeDisposable =
        io.reactivex.disposables.CompositeDisposable()

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, channelName)
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (!(call.method == "configureTerminal" || call.method == "hasNFC" || call.method == "isGPSEnabled")) {
            if (userData == null) {
                result.error(tag, "Terminal not Configured", null)
                return
            }
        }
        when (call.method) {
            "hasNFC" -> this.hasNFC(result)
            "isGPSEnabled" -> this.isGPSEnabled(result)
            "configureTerminal" -> this.configureTerminal(call, result)
            "launchContactless" -> this.launchContactless(call, result)
            "makePayment" -> this.makePayment(call, result)
            "checkBalance" -> this.checkBalance(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        activity = binding.activity
        DPrefs.initializeDPrefs(activity!!.applicationContext)
        Prefs.Builder()
            .setContext(activity!!)
            .setMode(ContextWrapper.MODE_PRIVATE)
            .setPrefsName(activity!!.packageName)
            .setUseDefaultSharedPreference(true)
            .build()
        val componentActivity = binding.activity as? ComponentActivity
            ?: throw IllegalStateException("Activity must be a ComponentActivity to use ActivityResultLauncher.")
        // Register the launcher here and store the reference
        resultLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
            Log.d("NetPlusPlugin", "RESULT CALLED HERE $data")
            if (result.resultCode == ContactlessReaderResult.RESULT_OK) {
                data?.let { i ->
                    val cardReadData = i.getStringExtra("data")
//                    val cardResult = gson.fromJson(cardReadData, CardResult::class.java)
                    if (cardReadData != null) {
                        callback(
                            "onLaunchContactlessResult", mapOf(
                                "status" to true,
                                "data" to cardReadData
                            )
                        )
                    }
                }
            }
            if (result.resultCode == ContactlessReaderResult.RESULT_ERROR) {
                data?.let { i ->
                    val error = i.getStringExtra("data")
                    error?.let {
                        callback(
                            "onLaunchContactlessResult", mapOf(
                                "status" to false,
                                "data" to it
                            )
                        )
                    }
                }
            }
        }
        enableBtLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("NetPlusPlugin", "Bluetooth Enabled")
            }
        }

        permissionsLauncher = componentActivity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                pendingCall?.let { call ->
                    pendingResult?.let { res ->
                        if (call.method == "launchContactless") {
                            launchContactless(call, res)
                        }
                    }
                }
            } else {
                pendingResult?.error(tag, "Permissions not granted", null)
            }
            println("done")
            pendingCall = null
            pendingResult = null
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activity = null
        activityPluginBinding = null
        compositeDisposable.clear()
    }

    private fun callback(method: String, args: Any) {
        channel.invokeMethod(method, args)
    }

    private fun hasNFC(result: Result) {
        val nfcAvailable = NfcAdapter.getDefaultAdapter(activity!!) != null
        result.success(nfcAvailable)
    }

    private fun isGPSEnabled(result: Result){
        result.success(isLocationServiceEnabled(activity!!))
    }

    //NetPlusContactless SDK
    private fun configureTerminal(
        call: MethodCall,
        result: Result
    ) {
        val businessName: String? = call.argument<String?>("businessName")
        val partnerName: String? = call.argument<String?>("partnerName")
        val partnerId: String? = call.argument<String?>("partnerId")
        val terminalId: String? = call.argument<String?>("terminalId")
        val terminalSerialNumber: String? = call.argument<String?>("terminalSerialNumber")
        val businessAddress: String? = call.argument<String?>("businessAddress")
        val customerName: String? = call.argument<String?>("customerName")
        val mid: String? = call.argument<String?>("mid")
        val institutionalCode: String? = call.argument<String?>("institutionalCode")
        val bankAccountNumber: String? = call.argument<String?>("bankAccountNumber")

        userData = UserData(
            businessName ?: "",
            partnerName ?: "",
            partnerId ?: "",
            terminalId ?: "",
            terminalSerialNumber ?: getAndroidId(activity!!) ?: "123456789ABCDE",
            businessAddress ?: "",
            customerName ?: "",
            mid ?: "",
            institutionalCode ?: "",
            ""
        )
        try {
            compositeDisposable.add(
                netposPaymentClient.init(activity!!, Gson().toJson(userData))
                    .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                    .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                    .subscribe { data, error ->
                        data?.let { response ->
                            val keyHolder = response.first
                            val configData = response.second
                            val pinKey = keyHolder?.clearPinKey
                            val resultMap = mapOf(
                                "keyHolder" to if (keyHolder == null) {
                                    null
                                } else {
                                    gson.toJson(keyHolder)
                                },
                                "configData" to if (configData == null) {
                                    null
                                } else {
                                    gson.toJson(configData)
                                },
                                "pinKey" to pinKey
                            )
                            result.success(resultMap)
                            println("DONE")
//                            if (pinKey != null) {
//                                val resultMap = mapOf(
//                                    "keyHolder" to gson.toJson(keyHolder),
//                                    "configData" to gson.toJson(configData),
//                                    "pinKey" to pinKey
//                                )
//                                result.success(resultMap)
//                            }
                        }
                        error?.let {
                            result.error(tag, error.message, null)
                        }
                    },
            )
        } catch (e: Error) {
            println(e)
        }


    }

    private fun launchContactless(
        call: MethodCall,
        result: Result,
    ) {
        val key: String? = call.argument<String?>("pinKey")
        val keyHolder: String? = call.argument<String?>("keyHolder")
        val kitOption: String? = call.argument<String?>("nfcKitOption")
        val amountToPay: Double? = call.argument<Double>("amount")
        val cashBack: Double? = call.argument<Double>("cashBackAmount")

        if (key.isNullOrEmpty() || keyHolder.isNullOrEmpty() || amountToPay == null) {
            result.error(tag, "Invalid parameters", null)
            return
        }

        if (!checkAndRequestPermissions(call, result)) return

        if (kitOption == "false") {
            if (!isLocationServiceEnabled(activity!!)) {
                result.error(
                    tag, "Please enable Location Service to use external NFC reader", mapOf(
                        "location" to false
                    )
                )
                return
            }

            if (!isBluetoothEnabled(activity!!)) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableBtIntent)
//                result.error(tag, "Please enable Bluetooth to use external NFC reader", null)
//                return
            }
        }
        val savedKeyHolder = gson.fromJson(keyHolder, KeyHolder::class.java)
        savedKeyHolder?.run {
            UpdatedContactlessSdk.readContactlessCard(
                activity!!,
                resultLauncher = resultLauncher,
                key,
                kitOption ?: "true",
                amountToPay.times(100),
                cashBack ?: 0.0,
                token = userData!!.partnerId,
                tid = userData!!.terminalId,
                mid = userData!!.mid,
            ) {

            }
        } ?: run {
            configureTerminal(call, result)
        }
    }

    private fun makePayment(
        call: MethodCall,
        result: Result,
    ) {
        val cardReadData: String? = call.argument<String?>("cardReadData")
        val remark: String? = call.argument<String?>("remark")
        val amount: Double? = call.argument<Double>("amount")
        val acctType: String? = call.argument<String?>("accountType")
        if (cardReadData.isNullOrEmpty() || amount == null) {
            result.error(tag, "Invalid parameters", null)
            return
        }
        val cardResult = gson.fromJson(cardReadData, CardResult::class.java)
        val cardData =
            cardResult.cardReadResult.let {
                CardData(it.track2Data, it.iccString, it.pan, "051")
            }

        cardData.pinBlock = cardResult.cardReadResult.pinBlock
        val makePaymentParams =
            MakePaymentParams(
                amount = amount.times(100).toLong(),
                terminalId = userData!!.terminalId,
                cardData = cardData,
                accountType = getAccountType(acctType),
                remark = remark,
            )

        compositeDisposable.add(
            netposPaymentClient.makePayment(
                activity!!,
                userData!!.terminalId,
                gson.toJson(makePaymentParams),
                cardResult.cardScheme,
                "CUSTOMER",

                ).subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                    { transactionWithRemark ->
                        val code = transactionWithRemark?.responseCode
                        val success = code == Status.APPROVED.statusCode
                        val data = gson.toJson(transactionWithRemark)
                        val resultMap = mapOf(
                            "status" to success,
                            "responseCode" to code,
                            "responseMessage" to getResponseMessage(code ?: ""),
                            "data" to data,
                        )
                        result.success(resultMap)
                    },
                    { throwable ->
                        val error = throwable.localizedMessage
                        result.error(tag, error, null)
                    },
                )
        )
    }

    private fun checkBalance(
        call: MethodCall,
        result: Result,
    ) {
        val cardReadData: String? = call.argument<String?>("cardReadData")

        if (cardReadData.isNullOrEmpty()) {
            result.error(tag, "Invalid parameters", null)
            return
        }
        val cardResult = gson.fromJson(cardReadData, CardResult::class.java)
        val cardData =
            cardResult.cardReadResult.let {
                CardData(it.track2Data, it.iccString, it.pan, "051").also { cardD ->
                    cardD.pinBlock = it.pinBlock
                }
            }

        compositeDisposable.add(
            netposPaymentClient.balanceEnquiry(activity!!, cardData, IsoAccountType.SAVINGS.name)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe { data, error ->
                    if (data != null) {
                        val responseString =
                            if (data.responseCode == Status.APPROVED.statusCode) {
                                "Response: APPROVED\nResponse Code: ${data.responseCode}\n\nAccount Balance:\n" +
                                        data.accountBalances.joinToString(
                                            "\n",
                                        ) { accountBalance ->
                                            "${accountBalance.accountType}: ${
                                                accountBalance.amount.div(100)
                                                    .formatCurrencyAmount()
                                            }"
                                        }
                            } else {
                                "Response: ${data.responseMessage}\nResponse Code: ${data.responseCode}"
                            }
                        result.success(responseString)
                    } else if (error != null) {
                        result.error(tag, error.localizedMessage, null)
                    }
                },
        )
    }

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String? {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun getAccountType(accountType: String?): IsoAccountType {
        return when (accountType) {
            "savings" -> IsoAccountType.SAVINGS
            "current" -> IsoAccountType.CURRENT
            "credit" -> IsoAccountType.CREDIT
            else -> IsoAccountType.DEFAULT_UNSPECIFIED
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.isEnabled
        }
        return false
    }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return try {
            // Check if GPS provider is enabled
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            isGpsEnabled
        } catch (e: Exception) {
            // Handle security exceptions or other issues if necessary
            false
        }
    }

    fun getResponseMessage(code: String): String {
        val responseMessages =
            mapOf(
                "00" to "Approved",
                "01" to "Refer to card issuer",
                "02" to "Refer to card issuer, special condition",
                "03" to "Invalid merchant",
                "04" to "Pick-up card",
                "05" to "Do not honor",
                "06" to "Error",
                "07" to "Pick-up card, special condition",
                "08" to "Honor with identification",
                "09" to "Request in progress",
                "10" to "Approved, partial",
                "11" to "Approved, VIP",
                "12" to "Invalid transaction",
                "13" to "Invalid amount",
                "14" to "Invalid card number",
                "15" to "No such issuer",
                "16" to "Approved, update track 3",
                "17" to "Customer cancellation",
                "18" to "Customer dispute",
                "19" to "Re-enter transaction",
                "20" to "Invalid response",
                "21" to "No action taken",
                "22" to "Suspected malfunction",
                "23" to "Unacceptable transaction fee",
                "24" to "File update not supported",
                "25" to "Unable to locate record",
                "26" to "Duplicate record",
                "27" to "File update edit error",
                "28" to "File update file locked",
                "29" to "File update failed",
                "30" to "Format error",
                "31" to "Bank not supported",
                "32" to "Completed partially",
                "33" to "Expired card, pick-up",
                "34" to "Suspected fraud, pick-up",
                "35" to "Contact acquirer, pick-up",
                "36" to "Restricted card, pick-up",
                "37" to "Call acquirer security, pick-up",
                "38" to "PIN tries exceeded, pick-up",
                "39" to "No credit account",
                "40" to "Function not supported",
                "41" to "Lost card",
                "42" to "No universal account",
                "43" to "Stolen card",
                "44" to "No investment account",
                "51" to "Not sufficient funds",
                "52" to "No check account",
                "53" to "No savings account",
                "54" to "Expired card",
                "55" to "Incorrect PIN",
                "56" to "No card record",
                "57" to "Transaction not permitted to cardholder",
                "58" to "Transaction not permitted on terminal",
                "59" to "Suspected fraud",
                "60" to "Contact acquirer",
                "61" to "Exceeds withdrawal limit",
                "62" to "Restricted card",
                "63" to "Security violation",
                "64" to "Original amount incorrect",
                "65" to "Exceeds withdrawal frequency",
                "66" to "Call acquirer security",
                "67" to "Hard capture",
                "68" to "Response received too late",
                "75" to "PIN tries exceeded",
                "77" to "Intervene, bank approval required",
                "78" to "Intervene, bank approval required for partial amount",
                "79" to "Declined",
                "81" to "Inactive, Dormant Account",
                "88" to "",
                "89" to "Acquirer Limit Exceeded/ Administration Error",
                "90" to "Cut-off in progress",
                "91" to "Issuer or switch inoperative",
                "92" to "Routing error.",
                "93" to "Violation of law",
                "94" to "Duplicate transaction",
                "95" to "Reconcile error",
                "96" to "System malfunction",
                "98" to "Exceeds cash limit",
                "99" to "Insufficient Funds/ Incorrect Pin",
                "N3" to "",
                "XX" to "",
                "H9" to "",
            )
        return responseMessages.getOrDefault(code, "")
    }

    private fun checkAndRequestPermissions(call: MethodCall, result: Result): Boolean {
        val kitOption: String? = call.argument<String?>("nfcKitOption")
        if (kitOption != "false") return true

        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity!!, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            pendingCall = call
            pendingResult = result
            permissionsLauncher.launch(missingPermissions.toTypedArray())
            return false
        }
        return true
    }
}
