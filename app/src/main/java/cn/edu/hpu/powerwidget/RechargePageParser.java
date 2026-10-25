package cn.edu.hpu.powerwidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the buy-record (充值记录) page. */
final class RechargePageParser {
    static final class ParseResult {
        final Integer page;
        final Integer pageCount;
        final List<RechargeRecord> records;

        ParseResult(Integer page, Integer pageCount, List<RechargeRecord> records) {
            this.page = page;
            this.pageCount = pageCount;
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
        }
    }

    private static final Pattern PAGE = Pattern.compile("第\\s*(\\d+)\\s*页\\s*/\\s*共\\s*(\\d+)\\s*页");
    private static final Pattern REMOVE_BLOCKS = Pattern.compile("(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern TABLE_ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr\\s*>");
    private static final Pattern TABLE_CELL = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td\\s*>");
    private static final Pattern TAGS = Pattern.compile("(?is)<[^>]*>");
    private static final Pattern DATE = Pattern.compile("\\d{4}/\\d{2}/\\d{2}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private RechargePageParser() {}

    static ParseResult parse(String html) {
        String source = html == null ? "" : html;
        String bodyText = normalize(HtmlFormParser.decodeEntities(
                TAGS.matcher(REMOVE_BLOCKS.matcher(source).replaceAll(" ")).replaceAll(" ")));

        Matcher pageMatch = PAGE.matcher(bodyText);
        Integer page = null;
        Integer pageCount = null;
        if (pageMatch.find()) {
            try { page = Integer.valueOf(pageMatch.group(1)); pageCount = Integer.valueOf(pageMatch.group(2)); }
            catch (NumberFormatException ignored) {}
        }

        List<RechargeRecord> records = new ArrayList<>();
        Matcher row = TABLE_ROW.matcher(source);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = TABLE_CELL.matcher(row.group(1));
            while (cell.find()) cells.add(normalize(HtmlFormParser.decodeEntities(TAGS.matcher(cell.group(1)).replaceAll(" "))));
            if (cells.size() < 5) continue;
            String time = cells.get(0);
            if (!DATE.matcher(time).find()) continue;
            Double kwh = firstNumber(cells.get(2));
            Double amount = firstNumber(cells.get(3));
            if (kwh == null || amount == null) continue;
            records.add(new RechargeRecord(time, cells.get(1), kwh, amount, cells.get(4)));
        }
        return new ParseResult(page, pageCount, records);
    }

    private static Double firstNumber(String value) {
        Matcher matcher = NUMBER.matcher(value == null ? "" : value.replace(",", ""));
        if (!matcher.find()) return null;
        try { return Double.valueOf(matcher.group()); } catch (NumberFormatException error) { return null; }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
                .replaceAll("\\s+", " ").trim();
    }
}
