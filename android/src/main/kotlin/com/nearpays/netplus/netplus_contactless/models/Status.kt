package com.nearpays.netplus.netplus_contactless.models

enum class Status(val statusCode: String) {
    APPROVED("00"),
    INVALID_PIN("55"),
    OTHERS(""),
}
