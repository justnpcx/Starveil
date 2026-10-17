// Top-level build file. All version pins live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Same AGP artifact as the two above: declaring it here is what lets
    // :benchmark ask for it by alias without a version, instead of Gradle
    // finding AGP already on the classpath and refusing to check compatibility.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// The performance tests print their measurement (CONFIGBUILD_500_NODES_MS, and
// whatever comes after it). Without this Gradle swallows stdout, so the number
// only ever appears when the assertion fails — too late to see a regression
// coming, and no way to tell a slow runner from a slow change.
subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            showStandardStreams = true
        }
    }
}

/**
 * Every string needs a Chinese counterpart, and the format specifiers have to
 * agree.
 *
 * Android's own missing-translation lint only fires when `values-zh` exists at
 * all, and it says nothing about `%1$s` — a translation that drops a specifier
 * compiles, ships, and throws `IllegalFormatException` the moment the screen is
 * opened in that language. Neither is visible in a build that only ever runs in
 * one locale, which is exactly the build this project had.
 *
 * Runs over the source tree rather than the merged resources: a module's own
 * file is what a translator edits, and the merged one is a build output.
 */
tasks.register("checkTranslations") {
    group = "verification"
    description = "Fails when a strings.xml has no values-zh counterpart, or the format specifiers disagree."

    val resFiles = fileTree(rootDir) {
        include("**/src/main/res/values*/strings.xml")
        exclude("**/build/**")
    }
    inputs.files(resFiles).withPropertyName("resFiles")

    doLast {
        val specifier = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]")

        fun readStrings(file: File): Map<String, Pair<String, List<String>>> {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val nodes = doc.getElementsByTagName("string")
            val out = linkedMapOf<String, Pair<String, List<String>>>()
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as org.w3c.dom.Element
                if (element.getAttribute("translatable") == "false") continue
                val text = element.textContent
                out[element.getAttribute("name")] =
                    text to specifier.findAll(text).map { it.value }.sorted().toList()
            }
            return out
        }

        val problems = mutableListOf<String>()
        var modules = 0
        var strings = 0

        resFiles.files.sortedBy { it.path }.forEach { file ->
            if (file.parentFile.name != "values") return@forEach
            val moduleDir = file.parentFile.parentFile.parentFile.parentFile.parentFile
            val resDir = moduleDir.resolve("src/main/res")
            val source = readStrings(file)
            val translations = resDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("values-") }
                .orEmpty()
                .sortedBy { it.name }
            if (translations.isEmpty()) {
                problems += "${moduleDir.name}: no values-<locale> directory at all"
                return@forEach
            }
            modules++
            strings += source.size
            for (localeDir in translations) {
                val localeFile = localeDir.resolve("strings.xml")
                if (!localeFile.exists()) {
                    problems += "${moduleDir.name}: ${localeDir.name}/ has no strings.xml"
                    continue
                }
                val translated = readStrings(localeFile)
                (source.keys - translated.keys).forEach {
                    problems += "${moduleDir.name}/${localeDir.name}: missing \"$it\""
                }
                (translated.keys - source.keys).forEach {
                    problems += "${moduleDir.name}/${localeDir.name}: \"$it\" is not in values/"
                }
                (source.keys intersect translated.keys).forEach { key ->
                    val (original, expected) = source.getValue(key)
                    val (text, actual) = translated.getValue(key)
                    if (expected != actual) {
                        problems += "${moduleDir.name}/${localeDir.name}: \"$key\" has $actual, " +
                            "the original has $expected\n    original: $original\n    translated: $text"
                    }
                }
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Translation check failed:\n" + problems.joinToString("\n") { "  - $it" },
            )
        }
        println("checkTranslations: $modules modules, $strings strings, all translated")
    }
}
