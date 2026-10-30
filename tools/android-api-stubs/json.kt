package org.json
open class JSONObject {
    constructor()
    constructor(str:String)
    fun put(key:String,value:Any?):JSONObject=this
    fun getDouble(key:String)=0.0
    fun getInt(key:String)=0
    fun getBoolean(key:String)=false
    fun isNull(key:String)=false
    fun keys():Iterator<String> = emptyList<String>().iterator()
    fun getString(key:String)=""
    fun getJSONObject(key:String)=JSONObject()
    fun getJSONArray(key:String)=JSONArray()
    fun optJSONArray(key:String):JSONArray?=null
    fun optJSONObject(key:String):JSONObject?=null
    fun optString(key:String,default:String="")=default
    fun optInt(key:String,default:Int=0)=default
    fun optDouble(key:String,default:Double=0.0)=default
    fun optBoolean(key:String,default:Boolean=false)=default
    fun toString(indentFactor:Int)=""
}
class JSONArray {
    fun put(value:Any?):JSONArray=this
    fun length():Int=0
    fun getJSONObject(index:Int)=JSONObject()
    fun getString(index:Int)=""
}
