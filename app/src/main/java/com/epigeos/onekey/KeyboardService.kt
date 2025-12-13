package com.epigeos.onekey

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.preference.PreferenceManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.time.TimeSource

class KeyboardService : InputMethodService() {

    val dictionary = mapOf(".-" to 'a', "-..." to 'b', "-.-." to 'c', "-.." to 'd', "." to 'e', "..-." to 'f', "--." to 'g', "...." to 'h', ".." to 'i', ".---" to 'j', "-.-" to 'k', ".-.." to 'l', "--" to 'm', "-." to 'n', "---" to 'o', ".--." to 'p', "--.-" to 'q', ".-." to 'r', "..." to 's', "-" to 't', "..-" to 'u', "...-" to 'v', ".--" to 'w', "-..-" to 'x', "-.--" to 'y', "--.." to 'z', ".----" to '1', "..---" to '2', "...--" to '3', "....-" to '4', "....." to '5', "-...." to '6', "--..." to '7', "---.." to '8', "----." to '9', "-----" to '0', ".-.-.-" to '.', "--..--" to ',', "---..." to ':', "..--.." to '?', ".----." to '\'', "-....-" to '-', "-..-." to '/', "-.--." to '(', "-.--.-" to ')', ".-..-." to '"', "-...-" to '=', ".-.-." to '+', ".--.-." to '@')
    var wasLastLetterWrong = false
    var isNextLetterUppercase = false
    var isCapsLocked = false

    fun getSettings(){
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        button?.height = preferences.getString("button_height", "348")?.toInt()!!
        unitMs = 60000/(50 * (preferences.getString("wpm", "15")?.toInt()!!))
        swipeDistance = preferences.getString("swipe_distance", "200")?.toFloat()!!
        acceptableError = preferences.getString("acceptable_error", "40")?.toInt()!!
        inputDespiteError = preferences.getBoolean("input_despite_error", true)
        checkErrorForDotdashes = preferences.getBoolean("check_error_for_dotdashes", true)
        checkErrorForDotdashSpace = preferences.getBoolean("check_error_for_dotdash_space", true)
        checkErrorForLetterSpace = preferences.getBoolean("check_error_for_letter_space", false)
        checkErrorForWordSpace = preferences.getBoolean("check_error_for_word_space", false)
        useAutomaticLetterSpace = preferences.getBoolean("use_automatic_letter_space", true)
        useAutomaticWordSpace = preferences.getBoolean("use_automatic_word_space", false)
    }
    var unitMs = 80
    var swipeDistance = 200f
    var acceptableError = 40
    var inputDespiteError = true
    var checkErrorForDotdashes = true
    var checkErrorForDotdashSpace = true
    var checkErrorForLetterSpace = false
    var checkErrorForWordSpace = false
    var useAutomaticLetterSpace = true
    var useAutomaticWordSpace = false

    var button: Button? = null
    var capsStatus: TextView? = null
    var errorMessage: TextView? = null
    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        getSettings()
    }
    override fun onCreateInputView(): View {

        val inflater = layoutInflater
        val inputView = inflater.inflate(R.layout.keyboard_layout, null)
        button = inputView.findViewById<Button>(R.id.button)
        capsStatus = inputView.findViewById<TextView>(R.id.capsStatus)
        errorMessage = inputView.findViewById<TextView>(R.id.errorMessage)


        val timeSource = TimeSource.Monotonic
        var mark: TimeSource.Monotonic.ValueTimeMark? = null
        var dotdashesTyped = 0 // More precisely ones that started being typed, not necessarily finished

        var isSwipe = false
        var touchStartX = 0f
        var touchStartY = 0f
        var dx = 0f
        var dy = 0f

//        @SuppressLint("ClickableViewAccessibility")
        button?.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event?.action == MotionEvent.ACTION_MOVE){
                    dx = event.x - touchStartX
                    dy = touchStartY - event.y
                    if (max(abs(dx), abs(dy)) > swipeDistance){
                        isSwipe = true
                    }
                } else if (event?.action == MotionEvent.ACTION_DOWN) {
                    dotdashesTyped += 1
                    var ms = mark?.elapsedNow()?.inWholeMilliseconds?.toInt()
                    mark = timeSource.markNow()
                    if (ms == null) return false

                    if (wasLastLetterWrong){
                        wasLastLetterWrong = false
                        ms = unitMs
                    }
                    if (isSwipe){ // Prev was a swipe
                        isSwipe = false
                        ms = unitMs
                    }
                    touchStartX = event.x
                    touchStartY = event.y

                    val distanceFromDotdashSpace = abs(unitMs - ms)
                    val distanceFromLetterSpace = abs((unitMs * 3) - ms)
                    val distanceFromWordSpace = abs((unitMs * 7) - ms)
                    if (distanceFromDotdashSpace < distanceFromLetterSpace) { // Dotdash
                        if (checkErrorForDotdashSpace && distanceFromDotdashSpace > acceptableError) saveError(getString(R.string.unacceptable_error_message, "Dotdash space", ms, unitMs))
                    } else if (distanceFromWordSpace < distanceFromLetterSpace && useAutomaticWordSpace) { // Word
                        inputLetters(' ')
                        Log.d("debug", "inserted automatic word space")
                        if (checkErrorForWordSpace && distanceFromWordSpace > acceptableError) saveError(getString(R.string.unacceptable_error_message, "Word space", ms, unitMs*7))
                    } else if (useAutomaticLetterSpace) { // Letter
                        if (checkErrorForLetterSpace && distanceFromLetterSpace > acceptableError) saveError(getString(R.string.unacceptable_error_message, "Letter space", ms, unitMs * 3))
                    }

                    return true
                } else if (event?.action == MotionEvent.ACTION_UP){
                    val ms = mark?.elapsedNow()?.inWholeMilliseconds?.toInt()
                    mark = timeSource.markNow()
                    if (ms == null) return false

                    if (isSwipe) {
                        val angle = atan2(dy, dx)
                        if (angle > -PI/8 && angle <= PI/8){ // Right
                            if (button?.text == ""){
                                inputLetters(' ')
                            }else{
                                inputDotdashes(' ')
                            }
                        } else if (angle > PI/8 && angle <= 3*PI/8){ // Top-right
                            isCapsLocked = !isCapsLocked
                            if (isCapsLocked) capsStatus?.text = "CapsLock" else if (isNextLetterUppercase) capsStatus?.text = "Caps" else capsStatus?.text = ""
                        } else if (angle > 3*PI/8 && angle <= 5*PI/8){ // Top
                            isNextLetterUppercase = !isNextLetterUppercase
                            if (isCapsLocked) capsStatus?.text = "CapsLock" else if (isNextLetterUppercase) capsStatus?.text = "Caps" else capsStatus?.text = ""
                        } else if (angle > 5*PI/8 && angle <= 7*PI/8){ // Top-left
                            backspaceWord()
                        } else if (angle > 7*PI/8 || angle <= -7*PI/8){ // Left
                            backspaceLetter()
                        } else if (angle > -7*PI/8 && angle <= -5*PI/8){ // Bottom-left
                            inputDotdashes('b')
                        } else if (angle > -5*PI/8 && angle <= -3*PI/8){ // Bottom
                            inputLetters('\n')
                        } else if (angle > -3*PI/8 && angle <= -PI/8){ // Bottom-right
                            enter()
                        }
                    }
                    else{
                        val distanceFromDot = abs(unitMs - ms)
                        val distanceFromDash = abs((unitMs * 3) - ms)
                        val isBeyondAcceptableError = checkErrorForDotdashes && min(distanceFromDot, distanceFromDash) > acceptableError;
                        if (distanceFromDot < distanceFromDash) {
                            if (isBeyondAcceptableError) saveError(getString(R.string.unacceptable_error_message, "Dot", ms, unitMs))
                            if (!isBeyondAcceptableError || inputDespiteError) inputDotdashes('.')
                        } else {
                            if (isBeyondAcceptableError) saveError(getString(R.string.unacceptable_error_message, "Dash", ms, unitMs*3))
                            if (!isBeyondAcceptableError || inputDespiteError) inputDotdashes('-')
                        }

                        if (useAutomaticLetterSpace){
                            val currentDotdashesTyped = dotdashesTyped
                            button?.postDelayed({
                                if (currentDotdashesTyped == dotdashesTyped) inputDotdashes(' ') // No new dotdashes started being entered since
                            }, (unitMs * 3).toLong())
                        }
                    }

                    return true
                }
                return false
            }
        })


        return inputView
    }

    private fun saveError(message: String){
        Log.d("showError", message)
        errorMessage?.text = message
    }
    private fun inputDotdashes(char: Char){
        if (char == ' '){
            val eqLetter = dictionary[button?.text]
            if (eqLetter == null){
                saveError(getString(R.string.nonexistent_letter_message, button?.text))
                wasLastLetterWrong = true
            }else{
                inputLetters(eqLetter)
            }
            button?.text = ""
        }else if (char == 'b') {
            button?.text = button?.text?.dropLast(1)
        }else{
            @SuppressLint("SetTextI18n")
            button?.text = "${button?.text}$char"
        }
    }
    private fun inputLetters(char: Char) {
        val inputConnection = currentInputConnection
        if (isNextLetterUppercase || isCapsLocked){
            inputConnection?.commitText(char.uppercase(), 1)
            isNextLetterUppercase = false
            if (isCapsLocked) capsStatus?.text = "CapsLock" else if (isNextLetterUppercase) capsStatus?.text = "Caps" else capsStatus?.text = ""
        }
        else inputConnection?.commitText(char.toString(), 1)
    }
    private fun backspaceLetter(){
        val inputConnection = currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)
        if (selectedText != null){
            inputConnection.commitText("", 1)
        }else{
            inputConnection?.deleteSurroundingText(1, 0)
        }
    }
    private fun backspaceWord(){
        val inputConnection = currentInputConnection
        val selectedText = inputConnection?.getSelectedText(0)
        if (selectedText != null){
            inputConnection.commitText("", 1)
        }else{
            val text = inputConnection?.getTextBeforeCursor(64, 0)
            val spaceIndex = text?.indexOfLast { it == ' ' }
            if (spaceIndex == null) inputConnection?.deleteSurroundingText(64, 0)
            else inputConnection.deleteSurroundingText(text.length - spaceIndex, 0)
        }
    }
    private fun enter(){
        val inputConnection = currentInputConnection
        inputConnection.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED)
    }
}
