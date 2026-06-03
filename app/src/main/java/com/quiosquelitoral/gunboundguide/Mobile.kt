package com.quiosquelitoral.gunboundguide

enum class ShotType {
    NORMAL,   // Trajetória parabólica normal
    BOUNCE,   // Quica no chão (Nak)
    CURVE,    // Curva de volta (Boomer)
    SPREAD    // Dois projéteis espalhados (Turtle)
}

data class Mobile(
    val id: String,
    val displayName: String,
    val maxSpeed: Float,       // velocidade inicial máxima em px/tick
    val gravity: Float,        // gravidade por tick (px/tick²)
    val windFactor: Float,     // sensibilidade ao vento
    val shotType: ShotType = ShotType.NORMAL,
    val shots: Int = 1,
    val spreadAngle: Float = 0f
)

object MobileData {
    val mobiles = listOf(
        Mobile(
            id = "aduka",
            displayName = "Aduka",
            maxSpeed = 16f,
            gravity = 0.38f,
            windFactor = 0.005f
        ),
        Mobile(
            id = "knight",
            displayName = "Knight",
            maxSpeed = 14f,
            gravity = 0.48f,
            windFactor = 0.002f
        ),
        Mobile(
            id = "nak",
            displayName = "Nak",
            maxSpeed = 15f,
            gravity = 0.32f,
            windFactor = 0.008f,
            shotType = ShotType.BOUNCE
        ),
        Mobile(
            id = "armor",
            displayName = "Armor",
            maxSpeed = 13f,
            gravity = 0.55f,
            windFactor = 0.001f
        ),
        Mobile(
            id = "boomer",
            displayName = "Boomer",
            maxSpeed = 14f,
            gravity = 0.35f,
            windFactor = 0.006f,
            shotType = ShotType.CURVE
        ),
        Mobile(
            id = "ice",
            displayName = "Ice",
            maxSpeed = 15f,
            gravity = 0.36f,
            windFactor = 0.005f
        ),
        Mobile(
            id = "lightning",
            displayName = "Lightning",
            maxSpeed = 22f,
            gravity = 0.18f,
            windFactor = 0.0005f
        ),
        Mobile(
            id = "turtle",
            displayName = "Turtle",
            maxSpeed = 13f,
            gravity = 0.42f,
            windFactor = 0.004f,
            shotType = ShotType.SPREAD,
            shots = 2,
            spreadAngle = 6f
        )
    )
}
