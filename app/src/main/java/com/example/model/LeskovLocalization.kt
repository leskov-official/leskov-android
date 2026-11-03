package com.example.model

enum class AppLanguage(val code: String) {
    RU("ru"),
    ET("et"),
    EN("en");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return when (code?.lowercase()?.trim()) {
                "et" -> ET
                "en" -> EN
                else -> RU
            }
        }
    }
}

object LeskovStrings {
    fun playbackError(lang: AppLanguage): String = when (lang) {
        AppLanguage.RU -> "Не удалось продолжить трек. Проверьте доступ и интернет."
        AppLanguage.ET -> "Loo jätkamine ebaõnnestus. Kontrollige võrku ja juurdepääsu."
        AppLanguage.EN -> "Unable to continue this track. Check your connection and access."
    }

    fun sheetNotifications(lang: AppLanguage): String = when (lang) {
        AppLanguage.RU -> "Уведомления"
        AppLanguage.ET -> "Teavitused"
        AppLanguage.EN -> "Notifications"
    }

    fun sheetNotificationsSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.RU -> "Разрешения и системные уведомления"
        AppLanguage.ET -> "Load ja süsteemiteavitused"
        AppLanguage.EN -> "Permissions and system notifications"
    }

    fun notificationStatus(lang: AppLanguage, status: String): String = when (status) {
        "permission_denied" -> when (lang) {
            AppLanguage.RU -> "Уведомления не разрешены в Android"
            AppLanguage.ET -> "Androidi teavitused pole lubatud"
            AppLanguage.EN -> "Android notifications are disabled"
        }
        "firebase_missing" -> when (lang) {
            AppLanguage.RU -> "Для push нужна реальная конфигурация Firebase"
            AppLanguage.ET -> "Push-teavitused vajavad Firebase'i seadistust"
            AppLanguage.EN -> "Push requires a real Firebase configuration"
        }
        else -> when (lang) {
            AppLanguage.RU -> "Разрешение получено. Для доставки push нужна серверная интеграция"
            AppLanguage.ET -> "Luba antud. Push vajab ka serveri seadistust"
            AppLanguage.EN -> "Permission granted. Push also requires server integration"
        }
    }

    fun navLabel(tabRoute: String, lang: AppLanguage): String {
        return when (tabRoute) {
            "home" -> when (lang) {
                AppLanguage.ET -> "Avaleht"
                AppLanguage.EN -> "Home"
                AppLanguage.RU -> "Главная"
            }
            "feed" -> when (lang) {
                AppLanguage.ET -> "Voog"
                AppLanguage.EN -> "Feed"
                AppLanguage.RU -> "Лента"
            }
            "search" -> when (lang) {
                AppLanguage.ET -> "Otsing"
                AppLanguage.EN -> "Search"
                AppLanguage.RU -> "Поиск"
            }
            "messages" -> when (lang) {
                AppLanguage.ET -> "Vestlused"
                AppLanguage.EN -> "Chats"
                AppLanguage.RU -> "Чаты"
            }
            "profile" -> when (lang) {
                AppLanguage.ET -> "Profiil"
                AppLanguage.EN -> "Profile"
                AppLanguage.RU -> "Профиль"
            }
            "more" -> when (lang) {
                AppLanguage.ET -> "Veel"
                AppLanguage.EN -> "More"
                AppLanguage.RU -> "Ещё"
            }
            else -> ""
        }
    }

    fun artistSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Artisti ametlik ruum"
        AppLanguage.EN -> "The artist's official space"
        AppLanguage.RU -> "Официальное пространство артиста"
    }

    fun chatsSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Privaatsed vestlused ja suhtlus sõpradega"
        AppLanguage.EN -> "Private conversations and chatting with friends"
        AppLanguage.RU -> "Личные переписки и общение с друзьями"
    }

    fun authGateTitle(tabIndex: Int, lang: AppLanguage): String {
        return when (tabIndex) {
            1 -> when (lang) {
                AppLanguage.ET -> "Logige sisse, et avada voog ja osaleda aruteludes"
                AppLanguage.EN -> "Sign in to open the feed and join discussions"
                AppLanguage.RU -> "Войдите, чтобы открыть ленту и участвовать в обсуждениях"
            }
            3 -> when (lang) {
                AppLanguage.ET -> "Privaatsed vestlused ja suhtlus sõpradega"
                AppLanguage.EN -> "Private chats and connecting with friends"
                AppLanguage.RU -> "Личные переписки и общение с друзьями"
            }
            4 -> when (lang) {
                AppLanguage.ET -> "Teie profiil, postitused ja seaded"
                AppLanguage.EN -> "Your profile, publications, and settings"
                AppLanguage.RU -> "Ваш профиль, публикации и настройки"
            }
            else -> when (lang) {
                AppLanguage.ET -> "Autoriseerimine LESKOV-is"
                AppLanguage.EN -> "Sign in to LESKOV"
                AppLanguage.RU -> "Авторизация в LESKOV"
            }
        }
    }

    fun authGateSubtitle(tabIndex: Int, lang: AppLanguage): String {
        return when (tabIndex) {
            1 -> when (lang) {
                AppLanguage.ET -> "Uudiste ja kommentaaride üldine voog"
                AppLanguage.EN -> "General feed of news and comments"
                AppLanguage.RU -> "Общая лента новостей и комментариев"
            }
            3 -> when (lang) {
                AppLanguage.ET -> "Turvaline suhtlus ja kiirsõnumid"
                AppLanguage.EN -> "Secure communication and instant messaging"
                AppLanguage.RU -> "Безопасное общение и мгновенные сообщения"
            }
            4 -> when (lang) {
                AppLanguage.ET -> "Muusika, esitusloendite ja konto haldamine"
                AppLanguage.EN -> "Manage music, playlists, and account"
                AppLanguage.RU -> "Управление музыкой, плейлистами и аккаунтом"
            }
            else -> artistSubtitle(lang)
        }
    }

    fun publicNote(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Muusika kuulamine ja otsing on saadaval ilma registreerimiseta."
        AppLanguage.EN -> "Listening to music and search are available without registration."
        AppLanguage.RU -> "Прослушивание музыки и поиск доступны без регистрации."
    }

    fun loginButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Logi sisse"
        AppLanguage.EN -> "Sign in"
        AppLanguage.RU -> "Войти"
    }

    fun registerButton(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Loo konto"
        AppLanguage.EN -> "Create account"
        AppLanguage.RU -> "Создать аккаунт"
    }

    fun terms(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Tingimused"
        AppLanguage.EN -> "Terms"
        AppLanguage.RU -> "Условия"
    }

    fun privacy(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Privaatsus"
        AppLanguage.EN -> "Privacy"
        AppLanguage.RU -> "Конфиденциальность"
    }

    fun backToHome(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "← Avalehele"
        AppLanguage.EN -> "← Back to Home"
        AppLanguage.RU -> "← На Главную"
    }

    fun sheetTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Seaded ja teenused"
        AppLanguage.EN -> "Settings & Services"
        AppLanguage.RU -> "Настройки и сервисы"
    }

    fun sheetSettings(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Konto seaded"
        AppLanguage.EN -> "Account Settings"
        AppLanguage.RU -> "Настройки аккаунта"
    }

    fun sheetSettingsSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Oma profiili haldamine"
        AppLanguage.EN -> "Manage your profile"
        AppLanguage.RU -> "Управление вашим профилем"
    }

    fun sheetTerms(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Kasutustingimused"
        AppLanguage.EN -> "Terms of Service"
        AppLanguage.RU -> "Условия использования"
    }

    fun sheetTermsSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Teenuse kasutamise reeglid"
        AppLanguage.EN -> "Service terms and rules"
        AppLanguage.RU -> "Правила использования сервиса"
    }

    fun sheetPrivacy(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Privaatsuspoliitika"
        AppLanguage.EN -> "Privacy Policy"
        AppLanguage.RU -> "Политика конфиденциальности"
    }

    fun sheetPrivacySubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Kuidas me andmeid kaitseme"
        AppLanguage.EN -> "How we protect your data"
        AppLanguage.RU -> "Как мы защищаем данные"
    }

    fun sheetSupport(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Tugi ja kontakt"
        AppLanguage.EN -> "Support & Contact"
        AppLanguage.RU -> "Поддержка и контакт"
    }

    fun sheetSupportSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "info@lesv.info (klõpsake kirjutamiseks või kopeerimiseks)"
        AppLanguage.EN -> "info@lesv.info (tap to email or copy)"
        AppLanguage.RU -> "info@lesv.info (нажмите для отправки или копирования)"
    }

    fun sheetClearCache(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Tühjenda rakenduse vahemälu"
        AppLanguage.EN -> "Clear app cache"
        AppLanguage.RU -> "Очистить кэш приложения"
    }

    fun sheetClearCacheSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Vabasta ajutised failid ilma välja logimata"
        AppLanguage.EN -> "Free temporary space without signing out"
        AppLanguage.RU -> "Освободить временные файлы без выхода из аккаунта"
    }

    fun cacheClearedToast(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Rakenduse vahemälu on tühjendatud"
        AppLanguage.EN -> "App cache cleared"
        AppLanguage.RU -> "Кэш приложения очищен"
    }

    // Authenticated sign out strings
    fun sheetSignOut(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Logi välja"
        AppLanguage.EN -> "Sign out"
        AppLanguage.RU -> "Выйти с аккаунта"
    }

    fun sheetSignOutSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Lõpeta seanss selles seadmes"
        AppLanguage.EN -> "End session on this device"
        AppLanguage.RU -> "Завершить сессию на этом устройстве"
    }

    // Guest sign in strings
    fun sheetSignIn(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Logi sisse"
        AppLanguage.EN -> "Sign in"
        AppLanguage.RU -> "Войти в аккаунт"
    }

    fun sheetSignInSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Autoriseerimine või uue konto loomine"
        AppLanguage.EN -> "Sign in or create a new profile"
        AppLanguage.RU -> "Авторизация или регистрация нового профиля"
    }

    fun signOutConfirmTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Kas soovite välja logida?"
        AppLanguage.EN -> "Sign out of your account?"
        AppLanguage.RU -> "Выйти с аккаунта?"
    }

    fun signOutConfirmDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Kas olete kindel, et soovite välja logida? Teie seanss lõpetatakse serveris ja peate uuesti sisse logima."
        AppLanguage.EN -> "Are you sure you want to sign out? Your session will be closed on the server and you will need to sign in again."
        AppLanguage.RU -> "Вы действительно хотите выйти из своего аккаунта? Сессия будет завершена на сервере, потребуется повторный вход."
    }

    fun signOutBtn(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Logi välja"
        AppLanguage.EN -> "Sign out"
        AppLanguage.RU -> "Выйти"
    }

    fun signedOutToast(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Olete kontost välja loginud"
        AppLanguage.EN -> "You have signed out"
        AppLanguage.RU -> "Вы вышли из аккаунта"
    }

    // Legacy method aliases for test compatibility
    fun sheetDeleteCookies(lang: AppLanguage): String = sheetSignOut(lang)
    fun sheetDeleteCookiesSubtitle(lang: AppLanguage): String = sheetSignOutSubtitle(lang)
    fun deleteCookiesConfirmTitle(lang: AppLanguage): String = signOutConfirmTitle(lang)
    fun deleteCookiesConfirmDesc(lang: AppLanguage): String = signOutConfirmDesc(lang)
    fun deleteCookiesBtn(lang: AppLanguage): String = signOutBtn(lang)
    fun cookiesDeletedToast(lang: AppLanguage): String = signedOutToast(lang)

    fun clearCacheConfirmTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Kas tühjendada rakenduse vahemälu?"
        AppLanguage.EN -> "Clear application cache?"
        AppLanguage.RU -> "Очистить кэш приложения?"
    }

    fun clearCacheConfirmDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "See vabastab ajutised vahemälufailid. Teie konto seanss ja sisselogimine säilivad."
        AppLanguage.EN -> "This will free temporary cached files. Your account session will remain active."
        AppLanguage.RU -> "Это освободит временные файлы кэша. Ваша сессия и вход в аккаунт сохранятся."
    }

    fun clearBtn(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Tühjenda"
        AppLanguage.EN -> "Clear"
        AppLanguage.RU -> "Очистить"
    }

    fun cancelBtn(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Loobu"
        AppLanguage.EN -> "Cancel"
        AppLanguage.RU -> "Отмена"
    }

    fun offlineTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Ühendus puudub"
        AppLanguage.EN -> "No connection"
        AppLanguage.RU -> "Нет соединения"
    }

    fun offlineDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "LESKOV töötab turvalise ühenduse kaudu. Kontrollige internetti ja proovige uuesti."
        AppLanguage.EN -> "LESKOV works over a secure connection. Check your internet and try again."
        AppLanguage.RU -> "LESKOV работает через защищённое соединение. Проверьте интернет и попробуйте снова."
    }

    fun retryBtn(lang: AppLanguage): String = when (lang) {
        AppLanguage.ET -> "Proovi uuesti"
        AppLanguage.EN -> "Try again"
        AppLanguage.RU -> "Попробовать снова"
    }
}
