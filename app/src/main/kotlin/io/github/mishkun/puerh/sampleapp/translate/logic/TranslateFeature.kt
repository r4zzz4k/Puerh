package io.github.mishkun.puerh.sampleapp.translate.logic

import io.github.mishkun.puerh.sampleapp.translate.logic.TranslateFeature.State.SelectedLanguage

private typealias ReducerResult = Pair<TranslateFeature.State, Set<TranslateFeature.Eff>>

object TranslateFeature {

    fun initialEff() = emptySet<Eff>()

    fun initialState() = State(
        inputText = "",
        translatedText = "",
        isLoading = false,
        errorMessage = null,
        languagesState = State.LanguagesState(
            availableLanguages = Language.values().toList(),
            selectedFrom = SelectedLanguage.Auto,
            selectedTo = Language.ENGLISH
        )
    )

    data class State(
        val inputText: String,
        val translatedText: String,
        val languagesState: LanguagesState,
        val errorMessage: String?,
        val isLoading: Boolean
    ) {
        data class LanguagesState(
            val availableLanguages: List<Language>,
            val selectedFrom: SelectedLanguage,
            val selectedTo: Language
        )

        sealed class SelectedLanguage {
            object Auto : SelectedLanguage()
            data class Concrete(val language: Language) : SelectedLanguage()
            companion object {
                fun fromLanguage(language: Language?): SelectedLanguage = when (language) {
                    null -> Auto
                    else -> Concrete(language)
                }
            }
        }
    }

    sealed class Msg {
        data class OnTextInput(val input: String) : Msg()
        data class OnLanguageFromChange(val language: Language?) : Msg()
        data class OnLanguageToChange(val language: Language) : Msg()
        object OnLanguageSwapClick : Msg()

        data class OnTranslationResult(
            val translatedText: String,
            val detectedLanguage: Language?
        ) : Msg()
        data class OnTranslationError(val apiError: ApiError) : Msg()
    }

    sealed class Eff {
        data class TranslateText(
            val languageCodeFrom: String?,
            val languageCodeTo: String,
            val text: String
        ) : Eff()
    }

    fun reducer(msg: Msg, state: State): ReducerResult = when (msg) {
        is Msg.OnTextInput -> {
            val eff = state.languagesState.toTranslateTextEff(msg.input)
            val newState = state.copy(inputText = msg.input, isLoading = true)
            newState to setOf(eff)
        }
        is Msg.OnTranslationError -> {
            val newState = state.copy(isLoading = false, errorMessage = msg.apiError.message)
            newState to emptySet()
        }
        is Msg.OnTranslationResult -> {
            val newFromLanguage = when (val oldFrom = state.languagesState.selectedFrom) {
                is SelectedLanguage.Concrete -> oldFrom
                is SelectedLanguage.Auto -> SelectedLanguage.fromLanguage(msg.detectedLanguage)
            }
            val newState = state.copy(
                isLoading = false,
                translatedText = msg.translatedText,
                languagesState = state.languagesState.copy(
                    selectedFrom = newFromLanguage
                )
            )
            newState to emptySet()
        }
        is Msg.OnLanguageFromChange -> {
            val newState = state.copy(
                isLoading = true,
                languagesState = state.languagesState.copy(
                    selectedFrom = SelectedLanguage.fromLanguage(msg.language)
                )
            )
            newState to setOf(newState.languagesState.toTranslateTextEff(state.inputText))
        }
        is Msg.OnLanguageToChange -> {
            val newState = state.copy(
                isLoading = true,
                languagesState = state.languagesState.copy(
                    selectedTo = msg.language
                )
            )
            newState to setOf(newState.languagesState.toTranslateTextEff(state.inputText))
        }
        is Msg.OnLanguageSwapClick -> when (state.languagesState.selectedFrom) {
            is SelectedLanguage.Concrete -> {
                val newState = state.copy(
                    isLoading = true,
                    inputText = state.translatedText,
                    translatedText = state.inputText,
                    languagesState = state.languagesState.copy(
                        selectedFrom = SelectedLanguage.fromLanguage(state.languagesState.selectedTo),
                        selectedTo = state.languagesState.selectedFrom.language
                    )
                )
                newState to setOf(newState.languagesState.toTranslateTextEff(newState.inputText))
            }
            else -> state to emptySet()
        }
    }


    private fun State.LanguagesState.toTranslateTextEff(text: String) = Eff.TranslateText(
        languageCodeFrom = when (selectedFrom) {
            is SelectedLanguage.Concrete -> selectedFrom.language.code
            else -> null
        },
        languageCodeTo = selectedTo.code,
        text = text
    )
}
