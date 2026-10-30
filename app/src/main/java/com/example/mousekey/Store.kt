package com.example.mousekey

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Mapping(val key: Int, val type: String, val x: Float, val y: Float, val text: String)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("m", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Mapping> {
        val a = JSONArray(p(c).getString("l", "[]"))
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            Mapping(o.getInt("k"), o.getString("t"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getString("s"))
        }
    }

    private fun save(c: Context, l: List<Mapping>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("k", it.key).put("t", it.type).put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("s", it.text))
        }
        p(c).edit().putString("l", a.toString()).apply()
    }

    fun add(c: Context, m: Mapping) {
        val l = load(c)
        l.removeAll { it.key == m.key }
        l.add(m)
        save(c, l)
    }

    fun remove(c: Context, i: Int) {
        val l = load(c)
        if (i in l.indices) { l.removeAt(i); save(c, l) }
    }

    fun find(c: Context, k: Int) = load(c).firstOrNull { it.key == k }
}
