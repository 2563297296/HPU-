package cn.edu.hpu.powerwidget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Direct client for both HPU electricity query campuses. */
final class SchoolPowerClient {
    public static final String NORTH_URL = Obf.d("5205b63ff827cab94915a137a57595e31414a63aa57e8bb9");
    public static final String SOUTH_URL = Obf.d("5205b63fb132caa70242ec7ebd24cba40f44ec7bb125d5ae0a5e862aed7c90fa4e5fa33cfb65");

    private static final String HOST = Obf.d("4915a137a57595e31414a63aa57e8b");
    private static final String SOUTH_HOST = Obf.d("0b49f161ba2bdcb80844f761bf");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_REDIRECTS = 8;
    private static final int MAX_PAGES = 30;
    private static final long PAGE_DELAY_MS = 220L;
    private static final Pattern CHARSET = Pattern.compile(
            "(?i)charset\\s*=\\s*[\"']?([^;\"'\\s]+)");
    private static final Pattern META_CHARSET = Pattern.compile(
            "(?i)<meta[^>]+charset\\s*=\\s*[\"']?([^\"'\\s/>]+)");

    static final class Campus {
        static final String NORTH = "north";
        static final String SOUTH = "south";
        final String id;
        final String url;
        final String host;
        final int allowedPort;
        Campus(String id, String url, String host, int allowedPort) { this.id=id; this.url=url; this.host=host; this.allowedPort=allowedPort; }
        static Campus fromRoom(Room room) { return "south".equals(room.campus) ? south() : north(); }
        static Campus north() { return new Campus(NORTH, NORTH_URL, HOST, 443); }
        static Campus south() { return new Campus(SOUTH, SOUTH_URL, SOUTH_HOST, 8080); }
    }

    private SchoolPowerClient() {}

    static UsageResult fetch(Room room) throws PowerSiteException {
        String rangeEnd = PowerDates.today();
        String rangeStart = PowerDates.shiftDays(rangeEnd, -90);
        return fetch(room, rangeStart, rangeEnd);
    }

    static UsageResult fetch(Room room, String rangeStart, String rangeEnd) throws PowerSiteException {
        MeterSelector lighting = new MeterSelector(UsageRecord.TYPE_LIGHTING, "照明",
                room.lightingBuilding, room.lightingFloor, room.lightingRoom);
        MeterSelector air = new MeterSelector(UsageRecord.TYPE_AIR, "空调",
                room.airBuilding, room.airFloor, room.airRoom);
        MeterUsage lightingUsage = queryMeter(new Session(Campus.fromRoom(room)), lighting, rangeStart, rangeEnd);
        MeterUsage airUsage = queryMeter(new Session(Campus.fromRoom(room)), air, rangeStart, rangeEnd);
        return UsageAggregator.aggregate(room, lightingUsage, airUsage, rangeStart, rangeEnd,
                System.currentTimeMillis());
    }

    static List<RechargeRecord> fetchRecharge(Room room, String rangeStart, String rangeEnd) throws PowerSiteException {
        Session session = new Session(Campus.fromRoom(room));
        MeterSelector meter = new MeterSelector(UsageRecord.TYPE_LIGHTING, "购电",
                room.lightingBuilding, room.lightingFloor, room.lightingRoom);

        Page page = session.request(session.campus.url, "GET", null);
        HtmlForm form = HtmlFormParser.parse(page.html, page.url);

        requireOption(form, "drlouming", meter.building, "购电楼栋");
        form.put("drlouming", meter.building); form.put("drceng", ""); form.put("drfangjian", "");
        page = postEvent(session, form, "drlouming", "购电楼栋切换");
        form = HtmlFormParser.parse(page.html, page.url);

        requireOption(form, "drceng", meter.floor, "购电楼层");
        form.put("drlouming", meter.building); form.put("drceng", meter.floor); form.put("drfangjian", "");
        page = postEvent(session, form, "drceng", "购电楼层切换");
        form = HtmlFormParser.parse(page.html, page.url);

        requireOption(form, "drfangjian", meter.room, "购电寝室");
        form.put("drlouming", meter.building); form.put("drceng", meter.floor); form.put("drfangjian", meter.room);
        form.put("radio", "buyR");
        form.put("ImageButton1.x", "8");
        form.put("ImageButton1.y", "8");
        page = postEvent(session, form, "", "购电寝室选择");
        form = HtmlFormParser.parse(page.html, page.url);

        if (form.get("txtstart") == null || form.get("txtend") == null) {
            throw new PowerSiteException("购电记录页结构已变化：找不到日期查询框");
        }
        form.put("txtstart", rangeStart);
        form.put("txtend", rangeEnd);
        form.put("btnser", "查询");
        form.put("__EVENTTARGET", "");
        form.put("__EVENTARGUMENT", "");
        page = session.request(form.action.toString(), "POST", form.fields);

        RechargePageParser.ParseResult first = RechargePageParser.parse(page.html);
        List<RechargeRecord> all = new ArrayList<>(first.records);
        if (first.page != null && first.pageCount != null) {
            if (first.pageCount < 0 || first.pageCount > MAX_PAGES) {
                throw new PowerSiteException("购电记录返回页数异常（" + first.pageCount + " 页）");
            }
            for (int pageNo = 2; pageNo <= first.pageCount; pageNo++) {
                delayBetweenPages();
                URL nextUrl;
                try {
                    nextUrl = new URL(page.url, "buyRecord.aspx?p=" + pageNo);
                } catch (MalformedURLException error) {
                    throw new PowerSiteException("购电记录分页地址无效", error);
                }
                Page nextPage = session.request(nextUrl.toString(), "GET", null);
                RechargePageParser.ParseResult parsed = RechargePageParser.parse(nextPage.html);
                if (parsed.page == null || parsed.pageCount == null) {
                    throw new PowerSiteException("购电记录第 " + pageNo + " 页缺少分页信息");
                }
                if (parsed.page != pageNo) {
                    throw new PowerSiteException("请求购电记录第 " + pageNo + " 页，却返回了第 " + parsed.page + " 页");
                }
                if (parsed.pageCount != first.pageCount) {
                    throw new PowerSiteException("购电记录分页总数在抓取期间发生变化");
                }
                all.addAll(parsed.records);
            }
        }
        Collections.sort(all, new Comparator<RechargeRecord>() {
            @Override public int compare(RechargeRecord left, RechargeRecord right) {
                return right.time.compareTo(left.time);
            }
        });
        return all;
    }

    static boolean checkCatalogSource(String campusId, int timeoutMs) throws PowerSiteException {
        Campus campus = Campus.SOUTH.equals(campusId) ? Campus.south() : Campus.north();
        Session session = new Session(campus);
        session.setCatalogTimeout(timeoutMs);
        Page page = session.request(campus.url, "GET", null);
        HtmlFormParser.parse(page.html, page.url);
        return true;
    }

    static long pingLatency(String campusId) {
        Campus campus = Campus.SOUTH.equals(campusId) ? Campus.south() : Campus.north();
        HttpURLConnection connection = null;
        long start = System.currentTimeMillis();
        try {
            URL url = new URL(campus.url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400) return -1;
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[64];
                while (input.read(buffer) != -1) break;
            }
            return System.currentTimeMillis() - start;
        } catch (Exception error) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static MeterUsage queryMeter(Session session, MeterSelector meter,
                                         String rangeStart, String rangeEnd)
            throws PowerSiteException {
        Page page = session.request(session.campus.url, "GET", null);
        HtmlForm form = HtmlFormParser.parse(page.html, page.url);

        requireOption(form, "drlouming", meter.building, meter.label + "楼栋");
        form.put("drlouming", meter.building);
        form.put("drceng", "");
        form.put("drfangjian", "");
        page = postEvent(session, form, "drlouming", meter.label + "楼栋切换");
        form = HtmlFormParser.parse(page.html, page.url);

        requireOption(form, "drceng", meter.floor, meter.label + "楼层");
        form.put("drlouming", meter.building);
        form.put("drceng", meter.floor);
        form.put("drfangjian", "");
        page = postEvent(session, form, "drceng", meter.label + "楼层切换");
        form = HtmlFormParser.parse(page.html, page.url);

        requireOption(form, "drfangjian", meter.room, meter.label + "寝室");
        form.put("drlouming", meter.building);
        form.put("drceng", meter.floor);
        form.put("drfangjian", meter.room);
        form.put("radio", "usedR");
        form.put("ImageButton1.x", "8");
        form.put("ImageButton1.y", "8");
        page = postEvent(session, form, "", meter.label + "寝室选择");
        form = HtmlFormParser.parse(page.html, page.url);

        if (form.get("txtstart") == null || form.get("txtend") == null) {
            throw new PowerSiteException(meter.label + "记录页结构已变化：找不到日期查询框");
        }
        form.put("txtstart", rangeStart);
        form.put("txtend", rangeEnd);
        form.put("btnser", "查询");
        form.put("__EVENTTARGET", "");
        form.put("__EVENTARGUMENT", "");
        page = session.request(form.action.toString(), "POST", form.fields);

        UsagePageParser.ParseResult first = UsagePageParser.parse(page.html, meter.type);
        if (first.balanceKwh == null) {
            throw new PowerSiteException(meter.label + "结果页结构已变化：找不到剩余电量");
        }
        if (first.page == null || first.pageCount == null) {
            throw new PowerSiteException(meter.label + "结果页结构已变化：找不到分页信息");
        }
        if (first.pageCount < 0 || first.pageCount > MAX_PAGES) {
            throw new PowerSiteException(meter.label + "返回页数异常（" + first.pageCount + " 页）");
        }
        if (first.pageCount > 0 && first.records.isEmpty()) {
            throw new PowerSiteException(meter.label + "第一页没有用电记录");
        }
        if (first.pageCount == 0 && !first.records.isEmpty()) {
            throw new PowerSiteException(meter.label + "分页信息与记录内容不一致");
        }

        List<UsageRecord> allRecords = new ArrayList<>(first.records);
        for (int pageNo = 2; pageNo <= first.pageCount; pageNo++) {
            delayBetweenPages();
            URL nextUrl;
            try {
                nextUrl = new URL(page.url, "usedRecord.aspx?p=" + pageNo);
            } catch (MalformedURLException error) {
                throw new PowerSiteException(meter.label + "分页地址无效", error);
            }
            Page nextPage = session.request(nextUrl.toString(), "GET", null);
            UsagePageParser.ParseResult parsed =
                    UsagePageParser.parse(nextPage.html, meter.type);
            if (parsed.page == null || parsed.pageCount == null) {
                throw new PowerSiteException(meter.label + "第 " + pageNo + " 页缺少分页信息");
            }
            if (parsed.page != pageNo) {
                throw new PowerSiteException(meter.label + "请求第 " + pageNo
                        + " 页，却返回了第 " + parsed.page + " 页");
            }
            if (parsed.pageCount != first.pageCount) {
                throw new PowerSiteException(meter.label + "分页总数在抓取期间发生变化");
            }
            if (parsed.records.isEmpty()) {
                throw new PowerSiteException(meter.label + "第 " + pageNo + " 页没有记录");
            }
            allRecords.addAll(parsed.records);
        }

        List<UsageRecord> filtered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (UsageRecord record : allRecords) {
            if (record.date.compareTo(rangeStart) < 0 || record.date.compareTo(rangeEnd) > 0) {
                continue;
            }
            String key = record.date + "|" + record.room + "|" + record.kwh + "|"
                    + record.unitPrice + "|" + record.type;
            if (seen.add(key)) filtered.add(record);
        }
        Collections.sort(filtered, new Comparator<UsageRecord>() {
            @Override
            public int compare(UsageRecord left, UsageRecord right) {
                return right.date.compareTo(left.date);
            }
        });
        return new MeterUsage(meter.type, meter.label, first.balanceKwh,
                first.pageCount, filtered);
    }

    private static void requireOption(HtmlForm form, String name, String value, String label)
            throws PowerSiteException {
        if (!form.hasOption(name, value)) {
            throw new PowerSiteException("学校页面结构或寝室编号已变化：找不到" + label + " " + value);
        }
    }

    private static Page postEvent(Session session, HtmlForm form, String target, String action)
            throws PowerSiteException {
        form.put("__EVENTTARGET", target);
        form.put("__EVENTARGUMENT", "");
        return session.request(form.action.toString(), "POST", form.fields);
    }

    private static void delayBetweenPages() throws PowerSiteException {
        try {
            Thread.sleep(PAGE_DELAY_MS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new PowerSiteException("查询已取消", error);
        }
    }

    private static final class MeterSelector {
        final String type;
        final String label;
        final String building;
        final String floor;
        final String room;

        MeterSelector(String type, String label, String building, String floor, String room) {
            this.type = type;
            this.label = label;
            this.building = building;
            this.floor = floor;
            this.room = room;
        }
    }

    private static final class Page {
        final String html;
        final URL url;

        Page(String html, URL url) {
            this.html = html;
            this.url = url;
        }
    }

    private static final class Session {
        private final Campus campus;
        private int catalogTimeoutMs;
        private final LinkedHashMap<String, String> cookies = new LinkedHashMap<>();
        Session(Campus campus) { this.campus = campus; }
        void setCatalogTimeout(int value) { catalogTimeoutMs = value; }

        Page request(String urlText, String method, Map<String, String> form)
                throws PowerSiteException {
            String currentUrl = urlText;
            String currentMethod = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
            byte[] body = form == null ? null : encodeForm(form).getBytes(StandardCharsets.UTF_8);

            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                HttpURLConnection connection = null;
                try {
                    URL url = checkedUrl(currentUrl, campus);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    int timeout = catalogTimeoutMs > 0 ? catalogTimeoutMs : CONNECT_TIMEOUT_MS;
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);
                    connection.setUseCaches(false);
                    connection.setRequestMethod(currentMethod);
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                    connection.setRequestProperty("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    String cookie = cookieHeader();
                    if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);

                    if ("POST".equals(currentMethod)) {
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type",
                                "application/x-www-form-urlencoded");
                        connection.setRequestProperty("Origin", url.getProtocol() + "://"
                                + url.getAuthority());
                        connection.setRequestProperty("Referer", url.toString());
                        byte[] payload = body == null ? new byte[0] : body;
                        connection.setFixedLengthStreamingMode(payload.length);
                        try (OutputStream output = connection.getOutputStream()) {
                            output.write(payload);
                        }
                    }

                    int status = connection.getResponseCode();
                    captureCookies(connection);
                    if (isRedirect(status)) {
                        String location = connection.getHeaderField("Location");
                        if (location == null || location.trim().isEmpty()) {
                            throw new PowerSiteException("学校站点返回了缺少地址的跳转");
                        }
                        if (redirect == MAX_REDIRECTS) {
                            throw new PowerSiteException("学校站点跳转次数过多");
                        }
                        currentUrl = new URL(url, location.trim()).toString();
                        checkedUrl(currentUrl, campus);
                        if (status != 307 && status != 308) {
                            currentMethod = "GET";
                            body = null;
                        }
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw new PowerSiteException("学校站点返回 HTTP " + status);
                    }

                    byte[] bytes;
                    try (InputStream input = connection.getInputStream()) {
                        bytes = readLimited(input);
                    }
                    String contentType = connection.getHeaderField("Content-Type");
                    return new Page(decode(bytes, contentType), url);
                } catch (PowerSiteException error) {
                    throw error;
                } catch (java.net.SocketTimeoutException error) {
                    throw new PowerSiteException("学校站点响应超时，请稍后重试", error);
                } catch (IOException error) {
                    throw new PowerSiteException("无法连接学校用电查询站点，请检查网络", error);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            throw new PowerSiteException("学校站点跳转次数过多");
        }

        private String cookieHeader() {
            StringBuilder value = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (value.length() > 0) value.append("; ");
                value.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return value.toString();
        }

        private void captureCookies(HttpURLConnection connection) {
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                if (header.getKey() == null || !"Set-Cookie".equalsIgnoreCase(header.getKey())) {
                    continue;
                }
                for (String line : header.getValue()) {
                    if (line == null) continue;
                    int semicolon = line.indexOf(';');
                    String pair = semicolon >= 0 ? line.substring(0, semicolon) : line;
                    int equals = pair.indexOf('=');
                    if (equals <= 0) continue;
                    String name = pair.substring(0, equals).trim();
                    String value = pair.substring(equals + 1).trim();
                    if (!name.isEmpty()) cookies.put(name, value);
                }
            }
        }
    }

    private static String encodeForm(Map<String, String> form) throws PowerSiteException {
        StringBuilder output = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : form.entrySet()) {
                if (output.length() > 0) output.append('&');
                output.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                output.append('=');
                output.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                        "UTF-8"));
            }
            return output.toString();
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new PowerSiteException("表单编码失败", impossible);
        }
    }

    private static URL checkedUrl(String value, Campus campus) throws PowerSiteException {
        try {
            URL url = new URL(value);
            boolean https = "https".equalsIgnoreCase(url.getProtocol());
            boolean http = "http".equalsIgnoreCase(url.getProtocol());
            boolean hostOk = campus.host.equalsIgnoreCase(url.getHost());
            boolean portOk = url.getPort() == -1 || url.getPort() == campus.allowedPort;
            boolean protocolOk = campus == null ? false
                    : ("north".equals(campus.id) ? https : http);
            if (!protocolOk || !hostOk || !portOk) {
                throw new PowerSiteException("拒绝访问非学校地址：" + url.getHost());
            }
            if (url.getUserInfo() != null) {
                throw new PowerSiteException("拒绝带用户信息的学校站点地址");
            }
            return url;
        } catch (MalformedURLException error) {
            throw new PowerSiteException("学校站点地址无效", error);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("学校页面响应过大");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String decode(byte[] bytes, String contentType) {
        String charsetName = null;
        if (contentType != null) {
            Matcher matcher = CHARSET.matcher(contentType);
            if (matcher.find()) charsetName = matcher.group(1);
        }
        if (charsetName == null) {
            String head = new String(bytes, 0, Math.min(bytes.length, 2048),
                    StandardCharsets.ISO_8859_1);
            Matcher matcher = META_CHARSET.matcher(head);
            if (matcher.find()) charsetName = matcher.group(1);
        }
        Charset charset = StandardCharsets.UTF_8;
        if (charsetName != null) {
            String normalized = charsetName.toUpperCase(Locale.ROOT);
            try {
                if (normalized.contains("GB2312") || normalized.contains("GBK")) {
                    charset = Charset.forName("GB18030");
                } else {
                    charset = Charset.forName(charsetName);
                }
            } catch (RuntimeException ignored) {
                charset = standardFallback(normalized);
            }
        }
        return new String(bytes, charset);
    }

    private static Charset standardFallback(String name) {
        if (name.contains("GB") || name.contains("CP936")) return Charset.forName("GB18030");
        return StandardCharsets.UTF_8;
    }
}
