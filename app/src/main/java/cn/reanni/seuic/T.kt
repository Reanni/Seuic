package cn.reanni.seuic

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private val gson = Gson()

fun <T> T.toJson(): String = gson.toJson(this)

fun <T> String.fromJson(): T = gson.fromJson(this, object : TypeToken<T>() {}.type)

//fun <T> T.toJson(src: Any?, typeOfSrc: Type) = gson.toJson(src, typeOfSrc)
//inline fun <reified T> String.fromJson(typeOfT: Type): T = gson.fromJson(this, typeOfT)
//fun <T> fromJson(json: String?, typeOfT: Type) = gson.fromJson<T>(json, typeOfT)
//fun <T> fromJson(json: String?, typeOfT: Class<T>) =    gson.runCatching { fromJson<T>(json, typeOfT) }.getOrNull()


