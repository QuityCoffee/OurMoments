package ru.phb.ourmoments

enum class Environment {
    TEST,
    PROD
}

object AppConfig {
    // По умолчанию ставим ТЕСТ, чтобы случайно не сломать Прод при разработке
    var currentEnv: Environment = Environment.TEST

    // Динамически отдаем нужный URL в зависимости от выбранной среды
    val apiUrl: String
        get() = when (currentEnv) {
            Environment.PROD -> "http://api.quityrcr.beget.tech/api.php"
            Environment.TEST -> "http://api.quityrcr.beget.tech/test/apitest.php"
        }

    // При желании можно сделать разные ключи безопасности
    val apiKey: String
        get() = when (currentEnv) {
            Environment.PROD -> "MyLoveSecret2026quityromgmailcom"
            Environment.TEST -> "MyLoveSecret2026quityromgmailcom" // Если поменял ключ в test/api.php
        }
}