package dev.pgm.roadmate.ml

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Best-effort attempt to steer SpeechRecognizer's capture toward the car's
 * microphone instead of the phone's — entirely offline, no CarAudioRecord,
 * no cloud STT (voice must stay on-device; only weather is allowed to need
 * internet).
 *
 * SpeechRecognizer owns its mic session internally and has no API to name a
 * capture device directly. AudioManager.setCommunicationDevice() (API 31+)
 * is documented against communication use cases (AudioSource.VOICE_COMMUNICATION),
 * not AudioSource.VOICE_RECOGNITION (what SpeechRecognizer actually uses) —
 * whether the system speech service honors this preference is UNVERIFIED;
 * there's no way to confirm without a connected car. It's the most correct
 * offline-only lever the public API exposes, though, so it's worth setting
 * rather than leaving mic routing entirely up to chance.
 *
 * Safe to call unconditionally on a plain phone: when no car-type device is
 * present, [preferCarMicrophoneIfAvailable] simply returns false and does
 * nothing.
 */
class CarMicrophonePreference(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Returns true if a car-type audio device was found and requested. */
    fun preferCarMicrophoneIfAvailable(): Boolean {
        val carDevice = audioManager
            ?.availableCommunicationDevices
            ?.firstOrNull { it.type in CAR_DEVICE_TYPES }
            ?: return false
        return audioManager.setCommunicationDevice(carDevice)
    }

    fun clearPreference() {
        audioManager?.clearCommunicationDevice()
    }

    private companion object {
        val CAR_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUS,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )
    }
}
