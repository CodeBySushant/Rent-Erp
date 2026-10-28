package com.renterp.domain.notification.service;

import com.renterp.domain.notification.entity.Notification.Type;

import java.util.Map;

/**
 * M12: notification titles and the few fixed words in bodies, in the
 * recipient's language (en / hi / ne - the languages the app supports).
 * Names, amounts ("Rs 1,200"), months and dates are not translated.
 *
 * Bodies are built by the services as " · "-separated parts, e.g.
 * "2082-06 · Rs 12,000 · due 2082-06-10". Each part that is (or starts with)
 * a known English word is translated; everything else is kept as is.
 */
final class NotificationText {

    private NotificationText() {
    }

    private static final Map<Type, String[]> TITLES = Map.ofEntries(
            // {en, hi, ne}
            Map.entry(Type.BILL_ISSUED, new String[]{"Your bill is ready", "आपका बिल तैयार है", "तपाईंको बिल तयार छ"}),
            Map.entry(Type.PAYMENT_RECORDED, new String[]{"Payment recorded", "भुगतान दर्ज किया गया", "भुक्तानी दर्ता गरियो"}),
            Map.entry(Type.PAYMENT_PROOF_WAITING, new String[]{"Payment waiting for approval",
                    "भुगतान स्वीकृति की प्रतीक्षा में", "भुक्तानी स्वीकृतिको पर्खाइमा"}),
            Map.entry(Type.PAYMENT_APPROVED, new String[]{"Payment approved", "भुगतान स्वीकृत", "भुक्तानी स्वीकृत भयो"}),
            Map.entry(Type.PAYMENT_REJECTED, new String[]{"Payment rejected", "भुगतान अस्वीकृत", "भुक्तानी अस्वीकृत भयो"}),
            Map.entry(Type.READING_SUBMITTED, new String[]{"Meter reading submitted", "मीटर रीडिंग जमा की गई",
                    "मिटर रिडिङ पेस गरियो"}),
            Map.entry(Type.REQUEST_CREATED, new String[]{"New request", "नया अनुरोध", "नयाँ अनुरोध"}),
            Map.entry(Type.REQUEST_DECIDED, new String[]{"Request updated", "अनुरोध अपडेट हुआ", "अनुरोध अद्यावधिक भयो"}),
            Map.entry(Type.JOIN_REQUESTED, new String[]{"New join request", "जुड़ने का नया अनुरोध", "सामेल हुने नयाँ अनुरोध"}),
            Map.entry(Type.JOIN_ACCEPTED, new String[]{"Join request accepted", "जुड़ने का अनुरोध स्वीकार",
                    "सामेल हुने अनुरोध स्वीकार भयो"}),
            Map.entry(Type.JOIN_REJECTED, new String[]{"Join request rejected", "जुड़ने का अनुरोध अस्वीकार",
                    "सामेल हुने अनुरोध अस्वीकार भयो"}),
            Map.entry(Type.MOVE_OUT_NOTICE, new String[]{"Move-out notice", "कमरा छोड़ने की सूचना", "कोठा छोड्ने सूचना"}),
            Map.entry(Type.MOVE_OUT_SETTLED, new String[]{"Move-out settled", "कमरा छोड़ने का हिसाब पूरा",
                    "कोठा छोड्ने हिसाब मिलान भयो"}),
            Map.entry(Type.SUPPORT_TICKET_RESOLVED, new String[]{"Support replied", "सहायता टीम ने जवाब दिया",
                    "सहायता टोलीले जवाफ दियो"}));

    // Whole parts.
    private static final Map<String, String[]> WORDS = Map.ofEntries(
            Map.entry("Room change", new String[]{"कमरा बदलना", "कोठा परिवर्तन"}),
            Map.entry("Vacate", new String[]{"कमरा खाली करना", "कोठा छोड्ने"}),
            Map.entry("Maintenance", new String[]{"मरम्मत", "मर्मत"}),
            Map.entry("Other", new String[]{"अन्य", "अन्य"}),
            Map.entry("pending", new String[]{"लंबित", "बाँकी"}),
            Map.entry("approved", new String[]{"स्वीकृत", "स्वीकृत"}),
            Map.entry("rejected", new String[]{"अस्वीकृत", "अस्वीकृत"}),
            Map.entry("completed", new String[]{"पूर्ण", "पूरा भयो"}),
            Map.entry("cancelled", new String[]{"रद्द", "रद्द"}));

    // Leading words of a part ("due 2082-06-10").
    private static final Map<String, String[]> PREFIXES = Map.ofEntries(
            Map.entry("due ", new String[]{"देय तिथि ", "म्याद "}),
            Map.entry("Refund ", new String[]{"वापसी ", "फिर्ता "}),
            Map.entry("still owed ", new String[]{"बकाया ", "बाँकी "}),
            Map.entry("from ", new String[]{"शुरुआत ", "सुरु "}));

    static String title(Type type, String lang) {
        String[] t = TITLES.get(type);
        if (t == null) {
            return type.title;
        }
        return t[index(lang)];
    }

    static String body(String body, String lang) {
        int i = index(lang);
        if (body == null || i == 0) {
            return body;
        }
        String[] parts = body.split(" · ", -1);
        StringBuilder out = new StringBuilder();
        for (int p = 0; p < parts.length; p++) {
            if (p > 0) {
                out.append(" · ");
            }
            out.append(part(parts[p], i));
        }
        return out.toString();
    }

    private static String part(String s, int i) {
        String[] whole = WORDS.get(s.trim());
        if (whole != null) {
            return whole[i - 1];
        }
        for (Map.Entry<String, String[]> e : PREFIXES.entrySet()) {
            if (s.startsWith(e.getKey())) {
                return e.getValue()[i - 1] + s.substring(e.getKey().length());
            }
        }
        return s;
    }

    private static int index(String lang) {
        if ("hi".equals(lang)) {
            return 1;
        }
        if ("ne".equals(lang)) {
            return 2;
        }
        return 0;
    }
}
