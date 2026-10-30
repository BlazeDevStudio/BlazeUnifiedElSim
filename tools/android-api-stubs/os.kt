package android.os
class Bundle
class Looper { companion object {fun getMainLooper()=Looper()} }
class Handler(l:Looper) {fun post(r:Runnable){};fun postDelayed(r:Runnable,d:Long){};fun removeCallbacks(r:Runnable){} }
