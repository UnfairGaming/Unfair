package cn.unfair.management.altmanager;

import java.util.concurrent.ThreadLocalRandom;

public final class RandomOfflineNameGenerator {
    private static final int MAX_NAME_LENGTH = 16;
    private static final String[] FIRST_NAMES = {
            "alex", "andy", "aiden", "arthur", "ben", "blake", "brad", "brent",
            "caleb", "chase", "clark", "cole", "david", "dylan", "ethan", "evan",
            "finn", "gabe", "gavin", "henry", "isaac", "jake", "jason", "jordan",
            "josh", "kyle", "liam", "logan", "lucas", "mason", "miles", "nolan",
            "noah", "owen", "paul", "quinn", "ray", "ryan", "sam", "sean",
            "tyler", "vince", "wyatt", "zack", "ella", "emma", "mia", "zoe",
            "adam", "aaron", "allen", "brian", "bruce", "charlie", "chris", "daniel",
            "edgar", "eli", "felix", "frank", "george", "harry", "ian", "jack",
            "kevin", "leo", "mark", "nick", "oliver", "peter", "reed", "steve",
            "thomas", "victor", "will", "zane", "amy", "anna", "bella", "carla",
            "diana", "erica", "frida", "grace", "hanna", "ivy", "julia", "kira"
    };
    private static final String[] LAST_NAMES = {
            "adams", "baker", "barnes", "bell", "brooks", "brown", "carter", "clark",
            "cooper", "cox", "davis", "evans", "foster", "gray", "green", "griffin",
            "hayes", "hill", "hudson", "james", "jones", "king", "lane", "lewis",
            "miller", "moore", "morris", "murray", "parker", "porter", "price", "reed",
            "riley", "rogers", "ross", "scott", "smith", "stone", "taylor", "turner",
            "ward", "walker", "white", "wright", "young", "palmer", "coleman", "harris",
            "anderson", "bailey", "brooks", "campbell", "carson", "cook", "edwards", "fisher",
            "garcia", "gibson", "gordon", "hall", "hernandez", "howard", "hughes", "jefferson",
            "johnson", "keller", "martin", "nelson", "owens", "perry", "peterson", "powell",
            "ramsey", "roberts", "sanders", "stevens", "thompson", "watson", "webb", "wilson"
    };

    private RandomOfflineNameGenerator() {
    }

    public static String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 32; attempt++) {
            String candidate = buildCandidate(random);
            if (isValidCandidate(candidate)) {
                return candidate;
            }
        }

        String fallback = capitalize(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)])
                + capitalize(LAST_NAMES[random.nextInt(LAST_NAMES.length)]);
        return fallback.length() <= MAX_NAME_LENGTH ? fallback : fallback.substring(0, MAX_NAME_LENGTH);
    }

    private static String buildCandidate(ThreadLocalRandom random) {
        String first = capitalize(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]);
        String last = capitalize(LAST_NAMES[random.nextInt(LAST_NAMES.length)]);
        int style = random.nextInt(8);

        switch (style) {
            case 0:
                return first + last;
            case 1:
                return first + last + random.nextInt(10);
            case 2:
                return first + last + random.nextInt(100);
            case 3:
                return first + "_" + last;
            case 4:
                return first + "_" + last + random.nextInt(10);
            case 5:
                return first.substring(0, 1) + last + random.nextInt(1000);
            case 6:
                return first + last.substring(0, 1) + random.nextInt(100);
            default:
                return first + last + random.nextInt(1000);
        }
    }

    private static boolean isValidCandidate(String text) {
        if (text.isEmpty() || text.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
