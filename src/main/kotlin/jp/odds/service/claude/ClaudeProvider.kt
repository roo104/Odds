package jp.odds.service.claude

import jp.odds.dto.ClaudeAskResponse
import jp.odds.dto.ClaudeProviderType

/**
 * One way of getting an answer out of Claude. Both implementations take the same prompt and hand
 * back the same shape, so switching between them is a toggle rather than a rewrite.
 */
interface ClaudeProvider {

    val type: ClaudeProviderType

    /** Null when this provider can serve a request right now; otherwise why it cannot. */
    fun unavailableReason(): String?

    suspend fun ask(prompt: String): ClaudeAskResponse
}

/**
 * Shared framing for both providers. Deliberately narrow: the prompt text arrives from a browser,
 * so nothing here invites tool use or file access.
 */
const val CLAUDE_SYSTEM_PROMPT: String =
    "You are a helpful assistant embedded in a football and handball odds tracking app. " +
        "Answer the user's question directly in plain prose. You have no tools and no file access - " +
        "do not attempt to read files, run commands, or search the web. If a question needs data you " +
        "were not given, say what you would need."
