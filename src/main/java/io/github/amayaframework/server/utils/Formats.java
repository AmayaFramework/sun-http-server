package io.github.amayaframework.server.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Formats {
    private static final String PATTERN = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final TimeZone GMT_TZ = TimeZone.getTimeZone("GMT");
    private static final ThreadLocal<DateFormat> DATE_FORMAT;

    static {
        DATE_FORMAT = ThreadLocal.withInitial(() -> {
            DateFormat df = new SimpleDateFormat(PATTERN, Locale.US);
            df.setTimeZone(GMT_TZ);
            return df;
        });
    }

    public static String formatDate(Date date) {
        return DATE_FORMAT.get().format(date);
    }

    public static long getTimeMillis(long seconds) {
        if (seconds == -1) {
            return -1;
        } else {
            return seconds * 1000;
        }
    }

    public static boolean isValidHeaderKey(String token) {
        if (token == null) return false;

        boolean isValidChar;
        char[] chars = token.toCharArray();
        String validSpecialChars = "!#$%&'*+-.^_`|~";
        for (char c : chars) {
            isValidChar = ((c >= 'a') && (c <= 'z')) ||
                    ((c >= 'A') && (c <= 'Z')) ||
                    ((c >= '0') && (c <= '9'));
            if (!isValidChar && validSpecialChars.indexOf(c) == -1) {
                return false;
            }
        }
        return !token.isEmpty();
    }
}
