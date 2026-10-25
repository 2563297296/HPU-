package cn.edu.hpu.powerwidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UsagePageParser {
    private static final Pattern BALANCE = Pattern.compile(
            "剩余电量\\s*[：:]\\s*(-?\\d+(?:\\.\\d+)?)\\s*度");
    private static final Pattern PAGE = Pattern.compile(
            "第\\s*(\\d+)\\s*页\\s*/\\s*共\\s*(\\d+)\\s*页");
    private static final Pattern REMOVE_BLOCKS = Pattern.compile(
            "(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern TABLE_ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr\\s*>");
    private static final Pattern TABLE_CELL = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td\\s*>");
    private static final Pattern TAGS = Pattern.compile("(?is)<[^>]*>");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    static final class ParseResult {
        final String text;
        final Double balanceKwh;
        final Integer page;
        final Integer pageCount;
        final List<UsageRecord> records;

        ParseResult(String text, Double balanceKwh, Integer page, Integer pageCount,
                    List<UsageRecord> records) {
            this.text = text;
            this.balanceKwh = balanceKwh;
            this.page = page;
            this.pageCount = pageCount;
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
        }
    }

    private UsagePageParser() {}

    static ParseResult parse(String html, String recordType) {
        String source = html == null ? "" : html;
        String bodyText = normalize(HtmlFormParser.decodeEntities(
                TAGS.matcher(REMOVE_BLOCKS.matcher(source).replaceAll(" ")).replaceAll(" ")));

        Matcher balance = BALANCE.matcher(bodyText);
        Double balanceKwh = null;
        if (balance.find()) {
            try {
                balanceKwh = Double.valueOf(balance.group(1));
            } catch (NumberFormatException ignored) {
                // A malformed balance remains absent and the client reports the page change.
            }
        }

        Matcher pageMatch = PAGE.matcher(bodyText);
        Integer page = null;
        Integer pageCount = null;
        if (pageMatch.find()) {
            try {
                page = Integer.valueOf(pageMatch.group(1));
                pageCount = Integer.valueOf(pageMatch.group(2));
            } catch (NumberFormatException ignored) {
                page = null;
                pageCount = null;
            }
        }

        List<UsageRecord> records = new ArrayList<>();
        Matcher row = TABLE_ROW.matcher(source);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = TABLE_CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(normalize(HtmlFormParser.decodeEntities(
                        TAGS.matcher(cell.group(1)).replaceAll(" "))));
            }
            if (cells.size() < 4 || !DATE.matcher(cells.get(0)).matches()) continue;
            Double kwh = firstNumber(cells.get(2));
            Double unitPrice = firstNumber(cells.get(3));
            if (kwh == null || unitPrice == null) continue;
            records.add(new UsageRecord(
                    cells.get(0), recordType, cells.get(1), kwh, unitPrice,
                    PowerMath.round2(kwh * unitPrice)));
        }
        return new ParseResult(bodyText, balanceKwh, page, pageCount, records);
    }

    private static Double firstNumber(String value) {
        Matcher matcher = NUMBER.matcher(value.replace(",", ""));
        if (!matcher.find()) return null;
        try {
            return Double.valueOf(matcher.group());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ')
                .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
                .replaceAll("\\s+", " ").trim();
    }
}
