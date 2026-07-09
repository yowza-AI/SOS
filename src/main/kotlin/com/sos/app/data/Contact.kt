package com.sos.app.data

/**
 * Emergency contact data model.
 */
data class Contact(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val dropboxAccessToken: String? = null,
    val isVerified: Boolean = false,
    val verifiedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJSON(): String {
        return """
            {
                "id": "$id",
                "name": "$name",
                "email": "$email",
                "phone": "$phone",
                "isVerified": $isVerified,
                "verifiedAt": $verifiedAt,
                "createdAt": $createdAt
            }
        """.trimIndent()
    }
}
