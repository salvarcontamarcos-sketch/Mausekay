package com.example.mousekey

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { f() }
        }
        box.addView(btn("1. Ativar o serviço (Acessibilidade)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        box.addView(btn("2. Adicionar mapeamento de tecla") { addMapping() })
        box.addView(TextView(this).apply {
            text = "Botão do meio do mouse (scroll): suspende/volta o ponteiro. A tela continua funcionando no toque.\n\nToque num mapeamento abaixo para apagar:"
            setPadding(0, 24, 0, 24)
        })
        Store.load(this).forEachIndexed { i, m ->
            val k = KeyEvent.keyCodeToString(m.key).removePrefix("KEYCODE_")
            val what = if (m.type == "tap") "tocar em (${m.x.toInt()}, ${m.y.toInt()})" else "digitar \"${m.text}\""
            box.addView(btn("$k → $what") {
                Store.remove(this, i)
                render()
            })
        }
        setContentView(ScrollView(this).apply { addView(box) })
    }

    private fun addMapping() {
        val d = AlertDialog.Builder(this)
            .setMessage("Aperte no teclado a tecla que você quer mapear")
            .setNegativeButton("Cancelar", null)
            .create()
        d.setOnKeyListener { _, code, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK) {
                d.dismiss()
                chooseAction(code)
                true
            } else false
        }
        d.show()
    }

    private fun chooseAction(code: Int) {
        val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        AlertDialog.Builder(this)
            .setTitle("Tecla $name")
            .setItems(arrayOf("Tocar numa posição da tela", "Digitar um texto")) { _, w ->
                if (w == 0) {
                    val s = MouseKeyService.instance
                    if (s == null) {
                        Toast.makeText(this, "Ative o serviço primeiro (botão 1)", Toast.LENGTH_LONG).show()
                    } else {
                        s.startPick(code)
                        Toast.makeText(this, "Abra a tela do app. Em 8 segundos aparece a marcação.", Toast.LENGTH_LONG).show()
                        moveTaskToBack(true)
                    }
                } else {
                    val et = EditText(this)
                    AlertDialog.Builder(this)
                        .setTitle("Texto para digitar")
                        .setView(et)
                        .setPositiveButton("Salvar") { _, _ ->
                            Store.add(this, Mapping(code, "txt", 0f, 0f, et.text.toString()))
                            render()
                        }
                        .show()
                }
            }
            .show()
    }
}
