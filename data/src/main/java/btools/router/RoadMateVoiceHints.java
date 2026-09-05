package btools.router;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads BRouter's turn instructions out of a computed track.
 *
 * BRouter keeps everything about a voice hint package-private: {@code
 * VoiceHintList.list}, and every field of {@code VoiceHint} and {@code
 * MessageData}. A class compiled into {@code btools.router} can see them; from
 * {@code dev.pgm.roadmate} the only alternatives are reflection — which breaks
 * silently whenever the bundled jar changes shape — or forking the library.
 * This is the smallest of the three, and if the upstream fields ever move this
 * file stops compiling instead of quietly returning nothing at runtime.
 *
 * Nothing here interprets: the codes are handed over as-is and mapped to the
 * app's own vocabulary in Kotlin, so the Android side stays free of BRouter
 * constants.
 */
public final class RoadMateVoiceHints {

    private RoadMateVoiceHints() {
    }

    /** One instruction, flattened into plain fields the Kotlin side can read. */
    public static final class Hint {
        /** BRouter command code — {@code VoiceHint.C}, {@code TL}, {@code TR}, … */
        public final int command;
        public final double latitude;
        public final double longitude;
        /** Metres from this instruction to the next one. */
        public final int distanceToNextMeters;
        /** 1-based exit to take, or 0 when this is not a roundabout. */
        public final int roundaboutExit;
        /** Index of the matching point in the track geometry. */
        public final int pointIndex;
        /** Name of the road being joined, or null when the profile didn't keep it. */
        public final String roadName;

        Hint(int command, double latitude, double longitude, int distanceToNextMeters,
             int roundaboutExit, int pointIndex, String roadName) {
            this.command = command;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceToNextMeters = distanceToNextMeters;
            this.roundaboutExit = roundaboutExit;
            this.pointIndex = pointIndex;
            this.roadName = roadName;
        }
    }

    /**
     * Extracts every hint on {@code track}, in order. Returns an empty list
     * when the track carries none — a route computed with
     * {@code turnInstructionMode == 0}, or one short enough to have no turns.
     * Never throws: a route that draws without instructions beats no route.
     */
    public static List<Hint> from(OsmTrack track) {
        List<Hint> out = new ArrayList<>();
        if (track == null || track.voiceHints == null || track.voiceHints.list == null) {
            return out;
        }
        for (VoiceHint hint : track.voiceHints.list) {
            if (hint == null) {
                continue;
            }
            try {
                out.add(new Hint(
                        hint.cmd,
                        hint.ilat / 1_000_000.0 - 90.0,
                        hint.ilon / 1_000_000.0 - 180.0,
                        (int) Math.round(hint.distanceToNext),
                        hint.roundaboutExit,
                        hint.indexInTrack,
                        roadName(hint)));
            } catch (RuntimeException ignored) {
                // One malformed hint must not cost the driver the whole route.
            }
        }
        return out;
    }

    /**
     * The {@code name=} tag of the way being joined, when the routing profile
     * kept way tags around. Most car profiles do not, so null is the normal
     * answer and callers have to read fine without a street name.
     */
    private static String roadName(VoiceHint hint) {
        MessageData way = hint.goodWay;
        if (way == null || way.wayKeyValues == null) {
            return null;
        }
        for (String token : way.wayKeyValues.split(" ")) {
            if (token.startsWith("name=")) {
                String name = token.substring("name=".length()).trim();
                return name.isEmpty() ? null : name;
            }
        }
        return null;
    }
}
