package autohost.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RegexUtils {
    public static Matcher matcher(String pattern, String input) {
        return Pattern.compile(pattern).matcher(input);
    }

    public static boolean matches(String pattern, String input) {
        return matcher(pattern, input).matches();
    }
}
