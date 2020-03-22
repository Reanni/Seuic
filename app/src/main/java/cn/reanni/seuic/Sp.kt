package cn.reanni.seuic

import android.content.Context

object Sp {

    val NAME = "config"

    /**
     * 存储String类型的值
     * @param mContext this
     * @param key      key值
     * @param value    要存储的String值
     */
    fun putString(mContext: Context, key: String, value: String) {
        val sharedPreferences = mContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(key, value).commit()
    }

    /**
     * 获取String类型的值
     * @param mContext this
     * @param key      key
     * @param defValue 默认值
     * @return
     */
    fun getString(mContext: Context, key: String, defValue: String = ""): String? {
        val sharedPreferences = mContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(key, defValue)
    }
}