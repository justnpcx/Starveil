package com.justnpcx.starveil.core.model

/**
 * A region a node can be recognised as, and the words that give it away.
 *
 * Rule-based on purpose. §5 of the product guide rules out selling an "AI picks
 * your node" black box, and a classifier that cannot explain itself is exactly
 * that. Every node here can say which word put it in which group, and the same
 * name always lands in the same group — which is also what makes it testable.
 *
 * Two kinds of token, because they need different matching:
 *  - [words] are matched as substrings: `香港` cannot appear inside an unrelated
 *    name, and neither can `🇭🇰`.
 *  - [codes] are two-letter ISO codes and are matched only as whole tokens (or
 *    as a token starting with the code followed by a digit). Substring matching
 *    would put `Russia` in the US group and `Japan` in the JP group.
 */
enum class NodeRegion(
    val displayName: String,
    internal val words: List<String>,
    internal val codes: List<String>,
) {
    HONG_KONG(
        "香港",
        listOf("香港", "hong kong", "hongkong", "深港", "沪港", "🇭🇰"),
        listOf("hk"),
    ),
    TAIWAN(
        "台湾",
        listOf("台湾", "台灣", "台北", "新北", "彰化", "taiwan", "taipei", "🇹🇼"),
        listOf("tw"),
    ),
    JAPAN(
        "日本",
        listOf("日本", "东京", "東京", "大阪", "埼玉", "名古屋", "福冈", "福岡", "japan", "tokyo", "osaka", "🇯🇵"),
        listOf("jp"),
    ),
    KOREA(
        "韩国",
        listOf("韩国", "韓國", "首尔", "首爾", "春川", "korea", "seoul", "🇰🇷"),
        listOf("kr"),
    ),
    SINGAPORE(
        "新加坡",
        listOf("新加坡", "狮城", "獅城", "singapore", "🇸🇬"),
        listOf("sg"),
    ),
    UNITED_STATES(
        "美国",
        listOf(
            "美国", "美國", "洛杉矶", "洛杉磯", "圣何塞", "聖何塞", "硅谷", "西雅图", "西雅圖",
            "纽约", "紐約", "达拉斯", "芝加哥", "凤凰城", "迈阿密", "波特兰", "阿什本",
            "united states", "los angeles", "san jose", "seattle", "new york", "dallas",
            "chicago", "phoenix", "miami", "portland", "ashburn", "🇺🇸",
        ),
        listOf("us"),
    ),
    UNITED_KINGDOM(
        "英国",
        listOf("英国", "英國", "伦敦", "倫敦", "united kingdom", "britain", "london", "🇬🇧"),
        listOf("uk", "gb"),
    ),
    GERMANY(
        "德国",
        listOf("德国", "德國", "法兰克福", "法蘭克福", "germany", "frankfurt", "🇩🇪"),
        listOf("de"),
    ),
    FRANCE("法国", listOf("法国", "法國", "巴黎", "france", "paris", "🇫🇷"), listOf("fr")),
    NETHERLANDS("荷兰", listOf("荷兰", "荷蘭", "阿姆斯特丹", "netherlands", "amsterdam", "🇳🇱"), listOf("nl")),
    RUSSIA("俄罗斯", listOf("俄罗斯", "俄羅斯", "莫斯科", "russia", "moscow", "🇷🇺"), listOf("ru")),
    CANADA("加拿大", listOf("加拿大", "多伦多", "多倫多", "温哥华", "溫哥華", "canada", "toronto", "vancouver", "🇨🇦"), listOf("ca")),
    AUSTRALIA("澳大利亚", listOf("澳大利亚", "澳洲", "悉尼", "雪梨", "australia", "sydney", "🇦🇺"), listOf("au")),
    TURKEY("土耳其", listOf("土耳其", "伊斯坦布尔", "伊斯坦堡", "turkey", "istanbul", "🇹🇷"), listOf("tr")),
    ARGENTINA("阿根廷", listOf("阿根廷", "argentina", "🇦🇷"), listOf("ar")),
    BRAZIL("巴西", listOf("巴西", "brazil", "🇧🇷"), listOf("br")),
    MALAYSIA("马来西亚", listOf("马来西亚", "馬來西亞", "吉隆坡", "malaysia", "kuala lumpur", "🇲🇾"), listOf("my")),
    THAILAND("泰国", listOf("泰国", "泰國", "曼谷", "thailand", "bangkok", "🇹🇭"), listOf("th")),
    VIETNAM("越南", listOf("越南", "胡志明", "vietnam", "🇻🇳"), listOf("vn")),
    PHILIPPINES("菲律宾", listOf("菲律宾", "菲律賓", "马尼拉", "馬尼拉", "philippines", "manila", "🇵🇭"), listOf("ph")),
    INDONESIA("印尼", listOf("印尼", "雅加达", "雅加達", "indonesia", "jakarta", "🇮🇩"), listOf("id")),
    EMIRATES("阿联酋", listOf("阿联酋", "阿聯酋", "迪拜", "杜拜", "emirates", "dubai", "🇦🇪"), listOf("ae")),
    INDIA("印度", listOf("印度", "孟买", "孟買", "india", "mumbai", "🇮🇳"), listOf("in")),
}

/** Which region a node's name points at, and the word that did it. */
data class RegionMatch(
    val region: NodeRegion,
    /** The token that matched, as it appears in the name. */
    val token: String,
    /** Where it matched; the earliest match wins, so the leading word decides. */
    val index: Int,
)

object NodeRegions {

    /** Letters and digits; everything else (spaces, dashes, emoji) separates. */
    private val TOKEN_SEPARATORS = Regex("[^a-z0-9]+")

    /**
     * The region a node belongs to, or null when nothing matches.
     *
     * Earliest match wins, so `香港→日本 中转` is a Hong Kong node — the leading
     * word is the one the provider put first, and it is also the one a reader
     * would take. Ties go to the enum's declaration order, which is stable.
     */
    fun classify(name: String): RegionMatch? {
        val lower = name.lowercase()
        if (lower.isBlank()) return null
        val tokens = TOKEN_SEPARATORS.split(lower).filter { it.isNotEmpty() }
        var best: RegionMatch? = null

        for (region in NodeRegion.entries) {
            for (word in region.words) {
                val at = lower.indexOf(word)
                if (at >= 0 && (best == null || at < best.index)) {
                    best = RegionMatch(region, word, at)
                }
            }
            for (code in region.codes) {
                val token = tokens.firstOrNull { it.matchesCode(code) } ?: continue
                val at = lower.indexOf(token)
                if (at >= 0 && (best == null || at < best.index)) {
                    best = RegionMatch(region, token, at)
                }
            }
        }
        return best
    }

    /**
     * A two-letter code is only a code on its own: `us` and `us01` are the
     * United States, `usa` and `russia` are not — `usa` is handled by the word
     * table, and `russia` contains `us` but is not a country code in disguise.
     */
    private fun String.matchesCode(code: String): Boolean =
        this == code || (length > code.length && startsWith(code) && this[code.length].isDigit())
}
