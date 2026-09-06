package com.studyspark.app.ai.verify

import com.studyspark.app.ai.llm.GeneratedQuizDraft

/**
 * Phone-first light checks. Full sandbox verification lives in verify-service (PC).
 * Items that need execution stay unverified until the PC service confirms them.
 */
class LightQuizVerifier {
    fun validateStructure(draft: GeneratedQuizDraft): VerificationResult {
        if (draft.choices.size < 2) {
            return VerificationResult.Rejected("Need at least 2 choices")
        }
        if (draft.correctIndex !in draft.choices.indices) {
            return VerificationResult.Rejected("correctIndex out of range")
        }
        if (draft.prompt.isBlank()) {
            return VerificationResult.Rejected("Empty prompt")
        }
        val needsExecution = draft.format in setOf("output", "syntax") && !draft.codeSnippet.isNullOrBlank()
        return if (needsExecution) {
            VerificationResult.NeedsSandbox("Code execution required for factual guarantee")
        } else {
            VerificationResult.AcceptedLight("knowledge/structure checks passed")
        }
    }
}

sealed class VerificationResult {
    data class AcceptedLight(val method: String) : VerificationResult()
    data class NeedsSandbox(val reason: String) : VerificationResult()
    data class Rejected(val reason: String) : VerificationResult()
}
