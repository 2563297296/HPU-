package cn.edu.hpu.powerwidget;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HtmlFormParser {
    private static final Pattern FORM = Pattern.compile("(?is)<form\\b([^>]*)>(.*?)</form\\s*>");
    private static final Pattern INPUT = Pattern.compile("(?is)<input\\b([^>]*)>");
    private static final Pattern SELECT = Pattern.compile("(?is)<select\\b([^>]*)>(.*?)</select\\s*>");
    private static final Pattern OPTION = Pattern.compile("(?is)<option\\b([^>]*)>(.*?)</option\\s*>");
    private static final Pattern TEXTAREA = Pattern.compile("(?is)<textarea\\b([^>]*)>(.*?)</textarea\\s*>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?is)([a-z_:][a-z0-9_:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");
    private static final Pattern BOOLEAN_ATTRIBUTE = Pattern.compile("(?is)(?:^|\\s)(disabled|checked|selected|readonly)(?:\\s|=|$)");

    private HtmlFormParser() {}

    static HtmlForm parse(String html, URL documentUrl) throws PowerSiteException {
        Matcher formMatcher = FORM.matcher(html == null ? "" : html);
        String formAttributes = null;
        String formBody = null;
        while (formMatcher.find()) {
            LinkedHashMap<String, String> attrs = attributes(formMatcher.group(1));
            if ("form1".equalsIgnoreCase(text(attrs.get("id")))
                    || "form1".equalsIgnoreCase(text(attrs.get("name")))) {
                formAttributes = formMatcher.group(1);
                formBody = formMatcher.group(2);
                break;
            }
        }
        if (formBody == null) {
            throw new PowerSiteException("学校页面结构已变化：找不到查询表单");
        }

        LinkedHashMap<String, String> attrs = attributes(formAttributes);
        String actionText = text(attrs.get("action"));
        URL action;
        try {
            action = new URL(documentUrl, actionText.isEmpty() ? documentUrl.toString() : actionText);
        } catch (MalformedURLException error) {
            throw new PowerSiteException("学校页面结构已变化：查询表单地址无效", error);
        }

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        LinkedHashMap<String, Set<String>> options = new LinkedHashMap<>();
        LinkedHashMap<String, Map<String, String>> labels = new LinkedHashMap<>();
        Matcher input = INPUT.matcher(formBody);
        while (input.find()) {
            LinkedHashMap<String, String> inputAttrs = attributes(input.group(1));
            String name = text(inputAttrs.get("name"));
            if (name.isEmpty() || hasBoolean(inputAttrs, "disabled")) continue;
            String type = text(inputAttrs.get("type")).toLowerCase();
            if (type.isEmpty()) type = "text";
            if ("submit".equals(type) || "image".equals(type) || "button".equals(type)
                    || "reset".equals(type) || "file".equals(type)) {
                continue;
            }
            if (("checkbox".equals(type) || "radio".equals(type)) && !hasBoolean(inputAttrs, "checked")) {
                continue;
            }
            fields.put(name, decodeEntities(text(inputAttrs.get("value"))));
        }

        Matcher select = SELECT.matcher(formBody);
        while (select.find()) {
            LinkedHashMap<String, String> selectAttrs = attributes(select.group(1));
            String name = text(selectAttrs.get("name"));
            if (name.isEmpty() || hasBoolean(selectAttrs, "disabled")) continue;
            String selectedValue = null;
            String firstValue = null;
            LinkedHashSet<String> values = new LinkedHashSet<>();
            Map<String, String> labelsForSelect = new LinkedHashMap<>();
            Matcher option = OPTION.matcher(select.group(2));
            while (option.find()) {
                LinkedHashMap<String, String> optionAttrs = attributes(option.group(1));
                if (hasBoolean(optionAttrs, "disabled")) continue;
                String value = text(optionAttrs.get("value"));
                if (value.isEmpty()) value = stripTags(option.group(2)).trim();
                value = decodeEntities(value);
                values.add(value);
                String label = decodeEntities(stripTags(option.group(2)).trim());
                labelsForSelect.put(value, label);
                if (firstValue == null) firstValue = value;
                if (hasBoolean(optionAttrs, "selected")) {
                    selectedValue = value;
                }
            }
            if (selectedValue == null) selectedValue = firstValue == null ? "" : firstValue;
            fields.put(name, selectedValue);
            options.put(name, values);
            labels.put(name, labelsForSelect);
        }

        Matcher textarea = TEXTAREA.matcher(formBody);
        while (textarea.find()) {
            LinkedHashMap<String, String> areaAttrs = attributes(textarea.group(1));
            String name = text(areaAttrs.get("name"));
            if (name.isEmpty() || hasBoolean(areaAttrs, "disabled")) continue;
            fields.put(name, decodeEntities(stripTags(textarea.group(2))));
        }

        return new HtmlForm(action, fields, options, labels);
    }

    private static LinkedHashMap<String, String> attributes(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String value = matcher.group(2);
            if (value == null) value = matcher.group(3);
            if (value == null) value = matcher.group(4);
            result.put(matcher.group(1).toLowerCase(), value == null ? "" : value);
        }
        Matcher booleanMatcher = BOOLEAN_ATTRIBUTE.matcher(raw == null ? "" : raw);
        while (booleanMatcher.find()) {
            String name = booleanMatcher.group(1).toLowerCase();
            if (!result.containsKey(name)) result.put(name, "");
        }
        return result;
    }

    private static boolean hasBoolean(LinkedHashMap<String, String> attrs, String name) {
        return attrs.containsKey(name);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTags(String value) {
        return value.replaceAll("(?is)<[^>]*>", "");
    }

    static String decodeEntities(String value) {
        if (value == null) return "";
        String result = value;
        for (int pass = 0; pass < 2; pass++) {
            result = result
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'")
                    .replace("&nbsp;", " ");
            result = decodeNumeric(result);
        }
        return result;
    }

    private static String decodeNumeric(String value) {
        Pattern numeric = Pattern.compile("(?i)&#(x?)([0-9a-f]+);");
        Matcher matcher = numeric.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            try {
                int code = Integer.parseInt(matcher.group(2), matcher.group(1).isEmpty() ? 10 : 16);
                matcher.appendReplacement(output, Matcher.quoteReplacement(new String(Character.toChars(code))));
            } catch (RuntimeException error) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
