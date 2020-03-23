package cn.reanni.seuic

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.seuic.blecom.BleController
import com.seuic.blecom.callback.DeviceCallback
import com.seuic.blecom.callback.ScanCallback
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*

/**
 * 东大集成的蓝牙连接
 */
@Suppress("DEPRECATION")
@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity(), BaseQuickAdapter.OnItemClickListener,
    BaseQuickAdapter.OnItemLongClickListener {

    companion object {
        private const val ACTION_SEUIC_SCAN_CODE = "cn.reanni.seuic.action.SEUIC_SCAN_CODE"// 东大集成
        private const val SEUIC_SCA_EXTRA_DATA = "scanner_data"

        private const val SP_KEY_HISTORY = "sp_key_history"

        private const val FREE = 0 //闲置中
        private const val SEARCHING = 1 //搜索中
        private const val SEARCHED = 3 //搜索完成
        private const val CONNECTING = 4 //连接中
        private const val CONNECTED = 5 //连接成功
        private const val OVERTIME = 6 //连接超时
        private const val DISCONNECT = 7 //断开连接
    }

    @IntDef(FREE, SEARCHING, SEARCHED, CONNECTING, CONNECTED, OVERTIME, DISCONNECT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Action

    private val TAG by lazy { javaClass.simpleName }
    private var isCanConnectOrSearch = true     // 是否可以开始连接或搜索
    private var prevAddress = "F4:F2:5B:75:FD:C2"
    private var connectedAddress: String? = null
    private var actionAddress = "无"
    private lateinit var dialog: ProgressDialog
    private val bleController: BleController by lazy { BleController.getInstance(application) }


    private val searchBluetoothDevices by lazy { mutableListOf<String>() }
    private val historyBluetoothDevices by lazy {
        Sp.getString(this, SP_KEY_HISTORY)?.run {
            fromJson<MutableList<String>>()
        } ?: mutableListOf()
    }
    private val listAdapter by lazy {
        object : BaseQuickAdapter<String, BaseViewHolder>(
            R.layout.item_device, searchBluetoothDevices
        ) {
            override fun convert(helper: BaseViewHolder, item: String?) {
                helper.setText(R.id.tv_device, item)
                helper.setBackgroundColor(
                    R.id.tv_device,
                    if (helper.layoutPosition % 2 == 0) getColor(R.color.colorAccent)
                    else getColor(R.color.colorPrimary)
                )
            }
        }
    }
    private val emptyView: TextView by lazy {
        layoutInflater.inflate(R.layout.layout_empty_view, null) as TextView
    }
    private val timer by lazy {
        object : CountDownTimer(15000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                e("api超时机制失效,手动超时")
                ui(OVERTIME)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var subscribe = RxPermissions(this)
            .request(Manifest.permission.ACCESS_COARSE_LOCATION)
            .subscribe { granted ->
                if (granted) {
                    setContentView(R.layout.activity_main)
                    rv.layoutManager = LinearLayoutManager(application)
                    rv.adapter = listAdapter.apply {
                        onItemClickListener = this@MainActivity
                        onItemLongClickListener = this@MainActivity
                        emptyView = this@MainActivity.emptyView
                    }
                } else {
                    finish()
                }
            }
//        bleController.scanBleDevices() // 连接BLE设备
//        bleController.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleController.disconnect()//关闭BLE连接
    }

    fun onClick(view: View) {
        when (view.id) {
            R.id.bt_history -> {
                tv_result.visibility = GONE
                rv.visibility = VISIBLE
                emptyView.text = "没有历史蓝牙设备~~"
                listAdapter.setNewData(historyBluetoothDevices)
            }
            R.id.bt_action -> {
                e("开始搜索")
                if (isCanConnectOrSearch) {
                    search()
                } else {
                    showToast("正在搜索中,请等结束后再次点击搜索")
                }

            }
        }
    }

    override fun onItemClick(adapter: BaseQuickAdapter<*, *>?, view: View?, position: Int) {
        if (isCanConnectOrSearch) {
            connect(listAdapter.data[position])
        } else {
            showToast("请等搜索结束再点击连接")
        }
    }

    override fun onItemLongClick(
        adapter: BaseQuickAdapter<*, *>?, view: View?, position: Int
    ): Boolean {
        if (listAdapter.data == historyBluetoothDevices) {
            showToast("删除历史: ${listAdapter.getItem(position)} 成功")
            listAdapter.remove(position)
            Sp.putString(this, SP_KEY_HISTORY, historyBluetoothDevices.toJson())
        }
        return true
    }

    private fun search() {
        isCanConnectOrSearch = false
        bleController.disconnect()
        actionAddress = "无"
        connectedAddress = null

        ui(SEARCHING)

        // 扫描BLE设备  scanTimeOut:扫描超时ms，若设置<=0则默认5s ; scanCallback：扫描结果回调
        bleController.scanBleDevices(5000, object : ScanCallback {
            // device：发现的设备
            // rssi:信号强度
            // scanRecord:远程设备提供的配对号(公告)
            override fun onScanning(
                device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?
            ) {//可根据mac或name等来过滤
                e("device : $device  name:${device.name}   rssi : $rssi    scanRecord : $scanRecord")
                searchBluetoothDevices.apply {
                    if (!contains(device.address)) {
                        add(device.address)
                        listAdapter.notifyItemInserted(size)
                    }
                }
            }

            override fun onCompleted() {// 全部搜索完成，下面可以连接了
                "全部搜索完成,共搜索到${searchBluetoothDevices.size}台蓝牙设备".let {
                    e(it)
                    showToast(it)
                }
                isCanConnectOrSearch = true
                runOnUiThread {
                    ui(SEARCHED)
                }
            }
        })
    }

    private fun connect(address: String) {
        e("开始连接")
        actionAddress = address
        ui(CONNECTING)
        // devicesAddress:待连接设备的mac
        // connectionTimeOut：连接超时，若设置<=0则默认6s;
        // autoConnect: 是否自动回连
        // bleDeviceCallback：连接读写的callback结果的回调
        bleController.connect(address, 7000, true, object : DeviceCallback {

            override fun onConnectionSuccess() { // 工牌连接成功
                e("工牌连接成功")
                runOnUiThread {
                    ui(CONNECTED)
                    connectedAddress = address
                    historyBluetoothDevices.apply { if (!contains(address)) add(address) }
                        .let {
                            Sp.putString(this@MainActivity, SP_KEY_HISTORY, it.toJson())
                        }
                }
            }

            override fun onConnectionFailed() { // 工牌连接超时或者被断开，请重试
                e("工牌连接超时或者被断开，请重试    connectedAddress: $connectedAddress")
                runOnUiThread {
                    ui(OVERTIME)
                }
                connectedAddress?.let { connect(it) } //正常连接后断开就重连,如果一直没连上connectedAddress=null不会重连
            }

            override fun onReadBarcode(scanResult: String?) { // 工牌扫描结果
                scanResult?.run {
                    substring(0, length - 1)//去掉结尾的"/r"回车符
                }?.let {
                    e("工牌扫描结果:$it")
                    runOnUiThread {
                        tv_result.apply {
                            text = "扫描到的结果：\n$it"
                            visibility = VISIBLE
                        }
                    }
                    sendBroadcast(Intent().apply {
                        action = ACTION_SEUIC_SCAN_CODE
                        putExtra(SEUIC_SCA_EXTRA_DATA, it)
                    })
                }
            }

            override fun onReadRawData(p0: ByteArray?) {
                //用户忽略
            }

            override fun onReadCmdData(p0: Int, p1: Int, p2: Int, p3: ByteArray?) {
                //用户忽略
            }

            override fun onWriteSuccess() {
                //写成功
            }

            override fun onWriteFailed(p0: Int) {
                //写失败
            }

        })
    }

    private fun ui(@Action action: Int) {
        when (action) {
//            FREE -> ""
            SEARCHING -> {
                tv_address.text = actionAddress; tv_state.text = "搜索中"
                tv_result.visibility = GONE;
                rv.visibility = VISIBLE
                searchBluetoothDevices.clear(); listAdapter.setNewData(searchBluetoothDevices)
                dialog = ProgressDialog.show(this, "", "搜索中...")
            }
            SEARCHED -> {
                tv_state.text = "搜索完成"
                emptyView.text = "没有搜索到蓝牙设备~~"
                dialog.dismiss()
            }
            CONNECTING -> {
                tv_address.text = actionAddress; tv_state.text = "连接中"
                dialog = ProgressDialog.show(this, "", "连接中...")
                timer.start()
            }
            CONNECTED -> {
                tv_state.text = "连接成功"
                rv.visibility = View.INVISIBLE
//                searchBluetoothDevices.clear(); listAdapter.notifyDataSetChanged()
                dialog.dismiss()
                timer.cancel()
            }
            OVERTIME -> {
                tv_state.text = "连接超时"
                rv.visibility = View.INVISIBLE
//                searchBluetoothDevices.clear(); listAdapter.notifyDataSetChanged()
                dialog.dismiss()
                timer.cancel()
            }
//            DISCONNECT -> ""
        }
    }

    private fun e(message: String) {
        Log.e(TAG, message)
    }

    private fun showToast(body: String) {
        Toast.makeText(application, body, Toast.LENGTH_SHORT).show()
    }

}
