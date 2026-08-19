package jp.odds.entity

/**
 * The sports we collect, each with the clock a live match is read against: [regulationMinutes] is
 * a full match and [periodMinutes] one half, so "how much is left" can be answered without
 * hard-coding 90 or 60 at the call site.
 */
enum class SportType(val regulationMinutes: Int, val periodMinutes: Int) {
    Football(regulationMinutes = 90, periodMinutes = 45),
    Handball(regulationMinutes = 60, periodMinutes = 30);

    /** Sofascore paths spell the sport in lowercase, which is exactly the enum name. */
    val slug: String get() = name.lowercase()
}
