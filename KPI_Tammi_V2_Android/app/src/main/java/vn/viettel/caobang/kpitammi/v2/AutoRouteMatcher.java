package vn.viettel.caobang.kpitammi.v2;

import java.text.Normalizer;
import java.util.Locale;

/** Keep accents and every name token; tolerate spacing and separator display differences. */
public final class AutoRouteMatcher {
    private AutoRouteMatcher() {}
    public static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFC)
            .replaceAll("[\\s_\\-–—]+", " ").trim().toLowerCase(Locale.ROOT);
    }
    public static boolean exact(String a, String b) {
        return !normalize(a).isEmpty() && normalize(a).equals(normalize(b));
    }
}
