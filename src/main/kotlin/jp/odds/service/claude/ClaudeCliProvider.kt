package jp.odds.service.claude

import com.fasterxml.jackson.databind.ObjectMapper
import jp.odds.dto.ClaudeAskResponse
import jp.odds.dto.ClaudeProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Answers by shelling out to the locally installed Claude Code CLI in print mode, which runs on
 * whatever credentials that CLI is already logged in with. No API key, no credits.
 *
 * The prompt reaches us from a browser, so the subprocess is locked down: it runs in an empty
 * throwaway directory rather than the project, every tool is denied, customizations (CLAUDE.md,
 * skills, plugins, hooks, MCP servers) are off, and the prompt goes in over stdin rather than argv.
 * What comes back is text and nothing else.
 *
 * Only works on a machine where the CLI is installed and authenticated - it does not survive a
 * deploy. That is what [ClaudeApiProvider] is for.
 */
@Component
class ClaudeCliProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${claude.cli.executable:claude}")
    private val executable: String,
    @Value("\${claude.cli.timeout-seconds:180}")
    private val timeoutSeconds: Long,
    /** Blank means "whatever the CLI is configured to use"; set it to pin a model. */
    @Value("\${claude.cli.model:}")
    private val model: String
) : ClaudeProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val type = ClaudeProviderType.CLI

    /** Empty scratch directory, so the subprocess has nothing of ours within reach. */
    private val sandbox: File by lazy {
        Files.createTempDirectory("odds-claude-sandbox").toFile().apply { deleteOnExit() }
    }

    override fun unavailableReason(): String? =
        if (resolveExecutable() != null) null
        else "Claude CLI '$executable' not found on PATH. Install it, or switch to the API provider."

    override suspend fun ask(prompt: String): ClaudeAskResponse = withContext(Dispatchers.IO) {
        val binary = resolveExecutable()
            ?: throw IllegalStateException(unavailableReason()!!)

        val command = buildList {
            add(binary.absolutePath)
            add("--print")
            add("--output-format"); add("json")
            add("--system-prompt"); add(CLAUDE_SYSTEM_PROMPT)
            add("--disallowed-tools"); add(DENIED_TOOLS)
            add("--safe-mode")
            add("--strict-mcp-config")
            add("--disable-slash-commands")
            add("--no-session-persistence")
            if (model.isNotBlank()) {
                add("--model"); add(model)
            }
        }

        val builder = ProcessBuilder(command).directory(sandbox)
        // CLI mode is meant to run on the CLI's own login. If a key happens to be exported in the
        // backend's environment the CLI would silently bill it instead, so take it away.
        builder.environment().remove("ANTHROPIC_API_KEY")
        builder.environment().remove("ANTHROPIC_AUTH_TOKEN")

        log.debug("Invoking Claude CLI: {}", command.joinToString(" "))
        val startedAt = System.currentTimeMillis()
        val process = builder.start()

        // Drain stderr on its own thread; a full pipe would otherwise wedge the child.
        val stderr = async(Dispatchers.IO) { process.errorStream.readBytes().decodeToString() }
        process.outputStream.use { it.write(prompt.toByteArray()) }
        val stdout = process.inputStream.readBytes().decodeToString()

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Claude CLI timed out after ${timeoutSeconds}s")
        }
        val errorText = stderr.await()
        if (process.exitValue() != 0) {
            // A non-zero exit still prints the result envelope, and its `result` field is the
            // readable reason - reach for that before falling back to raw output.
            val detail = runCatching { objectMapper.readTree(stdout).path("result").asText("") }
                .getOrDefault("")
                .ifBlank { errorText.ifBlank { stdout } }
            throw IllegalStateException(
                "Claude CLI exited with ${process.exitValue()}: ${detail.take(2000)}"
            )
        }

        parse(stdout, System.currentTimeMillis() - startedAt)
    }

    private fun parse(stdout: String, elapsedMs: Long): ClaudeAskResponse {
        val json = runCatching { objectMapper.readTree(stdout) }.getOrElse {
            // Shouldn't happen with --output-format json, but a plain-text fallback beats a 500.
            return ClaudeAskResponse(stdout.trim(), type, model.ifBlank { null }, elapsedMs)
        }
        if (json.path("is_error").asBoolean(false)) {
            throw IllegalStateException("Claude CLI reported an error: ${json.path("result").asText()}")
        }
        val text = json.path("result").asText("").ifBlank { stdout.trim() }
        return ClaudeAskResponse(
            text = text,
            provider = type,
            model = mainModel(json) ?: model.ifBlank { null },
            durationMs = json.path("duration_ms").asLong(elapsedMs),
            costUsd = json.path("total_cost_usd").let { if (it.isNumber) it.asDouble() else null }
        )
    }

    /**
     * The envelope has no single model field - it reports usage per model, including the small one
     * used for side tasks like naming the session. The costly one is the one that answered.
     */
    private fun mainModel(json: com.fasterxml.jackson.databind.JsonNode): String? {
        val usage = json.path("modelUsage").takeIf { it.isObject } ?: return null
        return usage.properties()
            .maxByOrNull { it.value.path("costUSD").asDouble(0.0) }
            ?.let { it.value.path("canonicalModel").asText(null) ?: it.key }
    }

    /** Honours an absolute path if one is configured, otherwise walks PATH like a shell would. */
    private fun resolveExecutable(): File? {
        if (executable.contains(File.separatorChar)) {
            return File(executable).takeIf { it.canExecute() }
        }
        return System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, executable) }
            .firstOrNull { it.canExecute() }
    }

    private companion object {
        /**
         * Everything that could touch the machine. The prompt is untrusted input from a browser,
         * so the subprocess is a text generator and nothing more.
         */
        const val DENIED_TOOLS =
            "Bash,BashOutput,KillShell,Edit,Write,NotebookEdit,Read,Glob,Grep,WebFetch,WebSearch," +
                "Task,Agent,TodoWrite,SlashCommand,Skill,Artifact"
    }
}
