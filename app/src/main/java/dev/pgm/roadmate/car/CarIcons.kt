package dev.pgm.roadmate.car

import androidx.annotation.DrawableRes
import androidx.car.app.Screen
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

/**
 * A tinted [CarIcon] from a drawable resource — the same three lines every car
 * screen needs. [CarColor.DEFAULT] is the host's own foreground colour, which
 * is what buttons and rows expect; pass a category colour or
 * [CarColor.createCustom] only where the glyph is meant to stand out.
 */
internal fun Screen.carIcon(
    @DrawableRes res: Int,
    tint: CarColor = CarColor.DEFAULT,
): CarIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, res))
    .setTint(tint)
    .build()
