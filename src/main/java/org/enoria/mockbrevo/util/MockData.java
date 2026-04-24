package org.enoria.mockbrevo.util;

import java.util.Random;

public final class MockData {

    private static final String[] CITIES = {
            "Paris", "Lyon", "Marseille", "Bordeaux", "Nantes", "Lille",
            "Toulouse", "Strasbourg", "Nice", "Rennes", "Montpellier"
    };
    private static final String[] STREETS = {
            "rue de la Paix", "avenue Victor Hugo", "boulevard Saint-Michel",
            "rue du Commerce", "avenue des Champs", "rue de la République",
            "place Bellecour", "rue Nationale", "allée des Tilleuls"
    };
    private static final String[] ZIP_PREFIXES = {"75", "69", "13", "33", "44", "59", "31", "67", "06", "35", "34"};
    private static final String[] DOMAINS = {"mailjet-sandbox.com", "sendinblue-relay.com", "brevo-edge.net"};
    private static final String[] TAGS = {"newsletter", "transactional", "promo", "campagne", "relance"};

    private MockData() {}

    public static Random seededFrom(Object... seeds) {
        long h = 1125899906842597L;
        for (Object s : seeds) {
            int code = s == null ? 0 : s.hashCode();
            h = 31L * h + code;
        }
        return new Random(h);
    }

    public static String pick(Random r, String[] choices) {
        return choices[r.nextInt(choices.length)];
    }

    public static String city(Random r) {
        return pick(r, CITIES);
    }

    public static String street(Random r) {
        int n = r.nextInt(200) + 1;
        return n + " " + pick(r, STREETS);
    }

    public static String zipCode(Random r) {
        return pick(r, ZIP_PREFIXES) + String.format("%03d", r.nextInt(1000));
    }

    public static String country(Random r) {
        return "France";
    }

    public static String ipAddress(Random r) {
        return (r.nextInt(224) + 10) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + (r.nextInt(253) + 1);
    }

    public static String senderDomain(Random r) {
        int n = r.nextInt(999) + 100;
        return "mb" + n + "." + pick(r, DOMAINS);
    }

    public static String tag(Random r) {
        return pick(r, TAGS);
    }

    public static int bounded(Random r, int min, int max) {
        if (max <= min) return min;
        return min + r.nextInt(max - min);
    }
}
