package cn.unfair.management.altmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class RandomOfflineNameGenerator {
    private static final int MAX_NAME_LENGTH = 16;
    private static final String NAME_RESOURCE = "/assets/minecraft/unfair/text/RandomNames.txt";
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^A-Za-z0-9]");
    private static final List<String> NAMES = loadNames();

    private RandomOfflineNameGenerator() {
    }

    public static String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String base = NAMES.get(random.nextInt(NAMES.size()));
        return appendSuffix(base, randomSuffix(random));
    }

    private static List<String> loadNames() {
        try (InputStream stream = RandomOfflineNameGenerator.class.getResourceAsStream(NAME_RESOURCE)) {
            if (stream == null) {
                return List.of("Player");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<String> names = reader.lines()
                        .map(RandomOfflineNameGenerator::cleanName)
                        .filter(name -> !name.isEmpty())
                        .map(RandomOfflineNameGenerator::limit)
                        .distinct()
                        .toList();
                return names.isEmpty() ? List.of("Player") : names;
            }
        } catch (IOException exception) {
            return List.of("Player");
        }
    }

    private static String cleanName(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD);
        return INVALID_CHARACTERS.matcher(MARKS.matcher(normalized).replaceAll("")).replaceAll("");
    }

    private static String randomDigits(ThreadLocalRandom random, int minLength, int maxLength) {
        int length = random.nextInt(minLength, maxLength + 1);
        StringBuilder digits = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }

    private static String randomSuffix(ThreadLocalRandom random) {
        StringBuilder suffix = new StringBuilder();
        int segments = random.nextInt(1, 3);
        for (int segment = 0; segment < segments; segment++) {
            String part = "_".repeat(random.nextInt(1, 3)) + randomDigits(random, 1, 3);
            if (suffix.length() + part.length() > MAX_NAME_LENGTH - 1) {
                break;
            }
            suffix.append(part);
        }
        return suffix.toString();
    }

    private static String appendSuffix(String base, String suffix) {
        int baseLength = Math.max(1, MAX_NAME_LENGTH - suffix.length());
        return limit(base.substring(0, Math.min(base.length(), baseLength)) + suffix);
    }

    private static String limit(String value) {
        return value.length() <= MAX_NAME_LENGTH ? value : value.substring(0, MAX_NAME_LENGTH);
    }
}
