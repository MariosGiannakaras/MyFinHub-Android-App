package app.myfinhub.android.feature.bankalert;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local, advisory parser for user-selected bank alert text. A bank name or masked
 * number is not proof of authenticity, and this parser never reads SMS/inbox data.
 * It intentionally rejects ambiguous, non-EUR and authorization/security messages.
 */
public final class BankAlertParser {
    private BankAlertParser() {}

    public enum Kind { PURCHASE, WITHDRAWAL }

    public static final class Alert {
        public final Kind kind;
        public final long cents;
        public final LocalDate date;
        public final LocalTime time;
        public final String lastFour;
        public final String merchant;
        public final String country;

        private Alert(Kind kind, long cents, LocalDate date, LocalTime time,
                      String lastFour, String merchant, String country) {
            this.kind = kind;
            this.cents = cents;
            this.date = date;
            this.time = time;
            this.lastFour = lastFour;
            this.merchant = merchant;
            this.country = country;
        }
    }

    private static final String AMOUNT = "(?:[0-9]{1,3}(?:[. ][0-9]{3})+,[0-9]{2}|[0-9]{1,3}(?:,[0-9]{3})+\\.[0-9]{2}|[0-9]{1,6}[,.][0-9]{2})";
    private static final String EURO = "(?:EUR|ΕΥΡΩ|€)";
    private static final Pattern MONEY = Pattern.compile("(?<![0-9.,+\\-])(?:" + EURO + "\\s*(" + AMOUNT + ")|(" + AMOUNT + ")\\s*" + EURO + ")(?![0-9.,\\p{L}])");
    private static final Pattern DATE = Pattern.compile("(?<![0-9])(?:(20[0-9]{2})-([0-9]{2})-([0-9]{2})|([0-9]{1,2})[/-]([0-9]{1,2})[/-](20[0-9]{2}|[0-9]{2}))(?![0-9])");
    private static final Pattern TIME = Pattern.compile("(?<![0-9])([0-9]{1,2}):([0-9]{2})(?![0-9:])");
    private static final Pattern CARD = Pattern.compile("(?<![0-9])(?:[*XΧ•]{2,}[ -]*)([0-9]{4})(?![0-9])");
    private static final Pattern BRAND = Pattern.compile("(?<!\\p{L})(?:PIRAEUS|WINBANK|ΠΕΙΡΑΙΩΣ)(?!\\p{L})");
    private static final Pattern PURCHASE = Pattern.compile("(?<!\\p{L})(?:ΑΓΟΡΑ|ΑΓΟΡΑΣ|PURCHASE)(?!\\p{L})");
    private static final Pattern WITHDRAWAL = Pattern.compile("(?<!\\p{L})(?:ΑΝΑΛΗΨΗ|ΑΝΑΛΗΨΗΣ|WITHDRAWAL)(?!\\p{L})");
    private static final Pattern UNSAFE = Pattern.compile("(?<!\\p{L})(?:OTP|EXTRAPIN|PIN|CREDIT|ΠΙΣΤΩΤΙΚΗ|ΠΙΣΤΩΤΙΚΗΣ|CVV|CVC|PASSWORD|ΚΩΔΙΚΟΣ|ΚΩΔΙΚΟΥ|ΚΩΔΙΚΟ|ΕΓΚΡΙΝΕΤΕ|ΕΓΚΡΙΣΗ|AUTHORIZED|AUTHORIZE|AUTHORIZATION|VERIFY|VERIFICATION|ΑΠΟΡΡΙΨΗ|ΑΠΟΡΡΙΦΘΗΚΕ|DECLINED|FAILED|ΑΠΟΤΥΧΙΑ|ΑΠΟΤΥΧΗΜΕΝΗ|ΑΚΥΡΩΣΗ|ΑΚΥΡΩΘΗΚΕ|REVERSED|REFUND|ΕΠΙΣΤΡΟΦΗ|ΑΝΤΙΛΟΓΙΣΜΟΣ|PENDING|ΕΚΚΡΕΜΕΙ)(?!\\p{L})|HTTPS?://|WWW\\.|(?<![0-9])(?:[0-9][ -]?){12,}(?![0-9])");
    private static final Pattern FOREIGN = Pattern.compile("(?<!\\p{L})(?:USD|GBP|CHF|JPY|CAD|AUD|BGN|PLN|TRY|RON|SEK|NOK|DKK)(?!\\p{L})|[$£¥]");
    private static final Pattern MERCHANT = Pattern.compile("(?iu)(?:^|[\\n;])\\s*(?:[ΕΈ]ΜΠΟΡΟΣ|ΚΑΤ[ΑΆ]ΣΤΗΜΑ|MERCHANT|ATM)\\s*[:=]\\s*([^\\n;]{1,90})(?=$|[\\n;])");
    private static final Pattern COUNTRY = Pattern.compile("(?iu)(?:^|[\\n;])\\s*(?:Χ[ΩΏ]ΡΑ|COUNTRY)\\s*[:=]\\s*([A-Z]{2})(?=$|[\\n; ])");

    public static Alert parse(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.length() > 1500) return null;
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'
                || Character.getType(c) == Character.FORMAT)) return null;
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        if (!BRAND.matcher(folded).find() || UNSAFE.matcher(folded).find()
                || FOREIGN.matcher(folded).find()) return null;
        boolean purchase = PURCHASE.matcher(folded).find();
        boolean withdrawal = WITHDRAWAL.matcher(folded).find();
        if (purchase == withdrawal) return null;
        try {
            Matcher money = MONEY.matcher(folded);
            if (!money.find()) return null;
            String token = money.group(1) != null ? money.group(1) : money.group(2);
            if (money.find()) return null; // no balance/fee or multi-transaction guessing
            int point = Math.max(token.lastIndexOf(','), token.lastIndexOf('.'));
            String decimal = token.substring(0, point).replaceAll("[., ]", "") + "." + token.substring(point + 1);
            long cents = new BigDecimal(decimal).movePointRight(2).longValueExact();
            if (cents < 1 || cents > 99_999_999) return null;
            Matcher card = CARD.matcher(folded);
            if (!card.find()) return null;
            String lastFour = card.group(1);
            if (card.find()) return null;
            Matcher dates = DATE.matcher(folded);
            if (!dates.find()) return null;
            LocalDate date;
            if (dates.group(1) != null) {
                date = LocalDate.of(Integer.parseInt(dates.group(1)), Integer.parseInt(dates.group(2)), Integer.parseInt(dates.group(3)));
            } else {
                int year = Integer.parseInt(dates.group(6));
                date = LocalDate.of(year < 100 ? 2000 + year : year, Integer.parseInt(dates.group(5)), Integer.parseInt(dates.group(4)));
            }
            if (dates.find()) return null;
            Matcher times = TIME.matcher(folded);
            LocalTime time = times.find() ? LocalTime.of(Integer.parseInt(times.group(1)), Integer.parseInt(times.group(2))) : null;
            if (time != null && times.find()) return null;
            Matcher merchantMatcher = MERCHANT.matcher(text);
            String merchant = merchantMatcher.find() ? merchantMatcher.group(1).trim() : "";
            // Extract only an explicitly labelled descriptor, never the full raw message.
            if (merchant.length() > 90 || CARD.matcher(merchant.toUpperCase(Locale.ROOT)).find() || !merchant.isEmpty() && merchant.codePoints().noneMatch(Character::isLetter)) merchant = "";
            if (merchantMatcher.find()) return null;
            Matcher countries = COUNTRY.matcher(text);
            String country = countries.find() ? countries.group(1).toUpperCase(Locale.ROOT) : "";
            if (!country.isEmpty() && countries.find()) return null;
            return new Alert(purchase ? Kind.PURCHASE : Kind.WITHDRAWAL, cents, date, time, lastFour, merchant, country);
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
