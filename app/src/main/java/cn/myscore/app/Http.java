package cn.myscore.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.ProxyInfo;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Multi-path HTTPS transport that adapts seamlessly across VPN/TUN, HTTP/SOCKS proxies,
 * direct cellular/Wi-Fi, and the first-party HTTPS source fallback.
 */
final class Http {
    static final String RELAY_BASE = "https://43.160.228.109";
    private static final long ROUTE_MEMORY_MS = 3 * 60_000L;
    private static final int MAX_REDIRECTS = 5;
    private static final Pattern ESPN_BOARD = Pattern.compile("^https://site\\.api\\.espn\\.com/apis/site/v2/sports/([a-z0-9./_-]+)/scoreboard\\?dates=([0-9-]+)&limit=1000$");
    private static final Pattern ESPN_SUMMARY = Pattern.compile("^https://site\\.api\\.espn\\.com/apis/site/v2/sports/([a-z0-9./_-]+)/summary\\?event=([0-9]+)$");
    private static final Pattern ESPN_SCHEDULE = Pattern.compile("^https://site\\.api\\.espn\\.com/apis/site/v2/sports/([a-z0-9./_-]+)/teams/([0-9]+)/schedule(\\?fixture=true)?$");
    private static final Pattern ESPN_ROSTER = Pattern.compile("^https://site\\.api\\.espn\\.com/apis/site/v2/sports/([a-z0-9./_-]+)/teams/([0-9]+)/roster$");
    private static final Pattern F1_CORE = Pattern.compile("^https://sports\\.core\\.api\\.espn\\.com/v2/sports/racing/leagues/f1/events/([0-9]+)/competitions/([0-9]+)(?:/competitors/([0-9]+)/statistics)?\\?lang=en&region=us$");
    private static final Pattern FIVE_TIER = Pattern.compile("^https://app\\.5eplay\\.com/api/tournament/session_list\\?(.*)$");
    private static final Pattern FIVE_TEAM = Pattern.compile("^https://esports-data\\.5eplaycdn\\.com/v1/api/csgo/team/matches_v1/([a-zA-Z0-9_]+)\\?page=([0-9]+)&limit=30&status=(future|past)$");
    private static final Pattern FIVE_DETAIL = Pattern.compile("^https://esports-data\\.5eplaycdn\\.com/v1/api/csgo/matches/(csgo_mc_[0-9]+)/data$");

    static {
        try {
            Security.setProperty("networkaddress.cache.negative.ttl", "0");
            Security.setProperty("networkaddress.cache.ttl", "30");
        } catch (RuntimeException ignored) {}
    }

    interface Opener {
        HttpURLConnection open(URL url) throws IOException;
    }

    private record Route(String id, Opener opener, boolean relay) {}

    private static final class RouteState {
        final long epoch;
        volatile String preferred = "";
        volatile long until;
        final Set<String> penalized = ConcurrentHashMap.newKeySet();
        RouteState(long epoch) { this.epoch = epoch; }
    }

    private static volatile boolean initialized;
    private static volatile ConnectivityManager connectivity;
    private static volatile Network activeNetwork;
    private static volatile Network physicalNetwork;
    private static volatile boolean vpnActive;
    private static volatile ProxyInfo platformProxy;
    private static final Set<Network> PHYSICAL_CANDIDATES = ConcurrentHashMap.newKeySet();
    private static final AtomicLong EPOCH = new AtomicLong(1L);
    private static final ConcurrentHashMap<String, RouteState> ROUTE_STATES = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private Http() {}

    static synchronized void init(Context context) {
        if (initialized || context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) return;
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        if (cm == null) return;
        connectivity = cm;
        initialized = true;
        refreshSnapshot(cm);
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { refreshAndNotify(); }
                @Override public void onLost(Network network) { refreshAndNotify(); }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { refreshAndNotify(); }
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties props) { refreshAndNotify(); }
            });
        } catch (RuntimeException ignored) {}
        try {
            NetworkRequest physicalRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
            cm.registerNetworkCallback(physicalRequest, new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    PHYSICAL_CANDIDATES.add(network);
                    refreshAndNotify();
                }
                @Override public void onLost(Network network) {
                    PHYSICAL_CANDIDATES.remove(network);
                    refreshAndNotify();
                }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    PHYSICAL_CANDIDATES.add(network);
                    refreshAndNotify();
                }
            });
        } catch (RuntimeException ignored) {}
    }

    private static void refreshAndNotify() {
        ConnectivityManager cm = connectivity;
        if (cm == null) return;
        if (refreshSnapshot(cm)) bumpEpoch();
    }

    private static boolean refreshSnapshot(ConnectivityManager cm) {
        try {
            Network nextActive = cm.getActiveNetwork();
            NetworkCapabilities activeCaps = nextActive == null ? null : cm.getNetworkCapabilities(nextActive);
            boolean nextVpn = activeCaps != null && activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            ProxyInfo nextProxy = cm.getDefaultProxy();
            if (nextProxy == null && nextActive != null) {
                LinkProperties lp = cm.getLinkProperties(nextActive);
                if (lp != null) nextProxy = lp.getHttpProxy();
            }
            Network nextPhysical = pickPhysical(cm, nextActive, nextVpn);
            boolean changed = !Objects.equals(activeNetwork, nextActive)
                || !Objects.equals(physicalNetwork, nextPhysical)
                || vpnActive != nextVpn
                || !sameProxy(platformProxy, nextProxy);
            activeNetwork = nextActive;
            physicalNetwork = nextPhysical;
            vpnActive = nextVpn;
            platformProxy = nextProxy;
            return changed;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Network pickPhysical(ConnectivityManager cm, Network currentActive, boolean isVpn) {
        if (!isVpn && currentActive != null) return currentActive;
        Network best = null;
        int bestScore = -1;
        for (Network candidate : PHYSICAL_CANDIDATES) {
            try {
                NetworkCapabilities caps = cm.getNetworkCapabilities(candidate);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;
                int score = (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 100 : 0)
                    + (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? 30
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? 40
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? 20 : 5);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            } catch (RuntimeException ignored) {}
        }
        return best;
    }

    private static boolean sameProxy(ProxyInfo a, ProxyInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getHost(), b.getHost()) && a.getPort() == b.getPort();
    }

    static void bumpEpoch() {
        EPOCH.incrementAndGet();
        ROUTE_STATES.clear();
        Logos.retryFailures();
        for (Runnable listener : LISTENERS) {
            try { listener.run(); } catch (RuntimeException ignored) {}
        }
    }

    static long epoch() {
        return EPOCH.get();
    }

    static AutoCloseable onNetworkChanged(Runnable listener) {
        if (listener == null) return () -> {};
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    static String statusSummary() {
        boolean vpn = vpnActive;
        Proxy proxy = explicitProxy("site.api.espn.com");
        if (vpn && physicalNetwork != null) return "当前网络：VPN 已开启 · 国内源物理直连分流 · 海外源多路自适应";
        if (vpn) return "当前网络：VPN 已开启 · 代理/直连与自有节点多路自适应";
        if (proxy != null) return "当前网络：系统代理已配置 · 国内源直连优先 · 海外源代理/直连自适应";
        return "当前网络：直连模式 · 官方源优先 + 自有 HTTPS 节点自动兜底";
    }

    static boolean domesticHost(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.endsWith(".qq.com") || h.equals("qq.com")
            || h.endsWith(".gtimg.cn") || h.endsWith(".gtimg.com")
            || h.endsWith(".tjstats.com")
            || h.endsWith(".5eplay.com") || h.equals("5eplay.com") || h.endsWith(".5eplaycdn.com")
            || h.endsWith(".bilibili.com") || h.equals("bilibili.com") || h.endsWith(".hdslb.com")
            || h.endsWith(".migu.cn") || h.endsWith(".miguvideo.com");
    }

    private static String hostGroup(String host) {
        if (host == null) return "other";
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith("espn.com") || h.endsWith("espncdn.com")) return "espn";
        if (h.endsWith("github.com") || h.endsWith("githubusercontent.com")) return "github";
        if (h.endsWith("5eplay.com") || h.endsWith("5eplaycdn.com")) return "5eplay";
        if (h.endsWith("qq.com") || h.endsWith("gtimg.cn") || h.endsWith("gtimg.com") || h.endsWith("tjstats.com")) return "qq";
        if (h.endsWith("bilibili.com") || h.endsWith("hdslb.com")) return "bili";
        if (h.endsWith("migu.cn") || h.endsWith("miguvideo.com")) return "migu";
        if (h.endsWith("premierleague.com")) return "fpl";
        if (h.endsWith("thesportsdb.com")) return "sportsdb";
        return h;
    }

    private static Proxy explicitProxy(String targetHost) {
        Proxy jvm = jvmProxy(targetHost);
        if (jvm != null) return jvm;
        ProxyInfo info = platformProxy;
        if (info == null) return null;
        try {
            String host = info.getHost();
            int port = info.getPort();
            if (host == null || host.isBlank() || port <= 0 || port > 65535) return null;
            String[] exclusions = info.getExclusionList();
            if (targetHost != null && exclusions != null) {
                String lower = targetHost.toLowerCase(Locale.ROOT);
                for (String ex : exclusions) {
                    if (ex == null || ex.isBlank()) continue;
                    String rule = ex.trim().toLowerCase(Locale.ROOT);
                    if (rule.startsWith(".")) rule = rule.substring(1);
                    if (lower.equals(rule) || lower.endsWith("." + rule)) return null;
                }
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Proxy jvmProxy(String targetHost) {
        String httpsHost = System.getProperty("https.proxyHost");
        int httpsPort = parsePort(System.getProperty("https.proxyPort"), 443);
        if (httpsHost != null && !httpsHost.isBlank() && httpsPort > 0 && !jvmExcluded(targetHost)) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpsHost.trim(), httpsPort));
        }
        String httpHost = System.getProperty("http.proxyHost");
        int httpPort = parsePort(System.getProperty("http.proxyPort"), 80);
        if (httpHost != null && !httpHost.isBlank() && httpPort > 0 && !jvmExcluded(targetHost)) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpHost.trim(), httpPort));
        }
        String socksHost = System.getProperty("socksProxyHost");
        int socksPort = parsePort(System.getProperty("socksProxyPort"), 1080);
        if (socksHost != null && !socksHost.isBlank() && socksPort > 0) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost.trim(), socksPort));
        }
        return null;
    }

    private static boolean jvmExcluded(String host) {
        if (host == null || host.isBlank()) return false;
        String nonProxy = System.getProperty("http.nonProxyHosts");
        if (nonProxy == null || nonProxy.isBlank()) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        for (String part : nonProxy.split("\\|")) {
            String rule = part.trim().toLowerCase(Locale.ROOT);
            if (rule.isEmpty()) continue;
            if (rule.startsWith("*.")) rule = rule.substring(2);
            else if (rule.startsWith("*")) rule = rule.substring(1);
            if (!rule.isEmpty() && (lower.equals(rule) || lower.endsWith("." + rule))) return true;
        }
        return false;
    }

    private static int parsePort(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int p = Integer.parseInt(raw.trim());
            return p > 0 && p <= 65535 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String relayUrl(String upstream) {
        if (upstream == null || upstream.isBlank()) return null;
        Matcher board = ESPN_BOARD.matcher(upstream);
        if (board.matches()) {
            Competition c = byPath(board.group(1));
            if (c == null) return null;
            String dates = board.group(2);
            if ("f1".equals(c.id)) {
                if (dates.matches("[0-9]{4}")) return RELAY_BASE + "/api/v1/source?kind=board&league=f1&date=" + dates + "-01-01";
                if (dates.matches("[0-9]{8}-[0-9]{8}") && dates.substring(0, 4).equals(dates.substring(9, 13))) {
                    return RELAY_BASE + "/api/v1/source?kind=board&league=f1&date=" + dates.substring(0, 4) + "-01-01";
                }
                return null;
            }
            if (dates.matches("[0-9]{8}")) {
                String iso = dates.substring(0, 4) + "-" + dates.substring(4, 6) + "-" + dates.substring(6, 8);
                return RELAY_BASE + "/api/v1/source?kind=board&league=" + c.id + "&date=" + iso;
            }
            return null;
        }
        Matcher summary = ESPN_SUMMARY.matcher(upstream);
        if (summary.matches()) {
            Competition c = byPath(summary.group(1));
            return c == null ? null : RELAY_BASE + "/api/v1/source?kind=summary&league=" + c.id + "&event=" + summary.group(2);
        }
        Matcher schedule = ESPN_SCHEDULE.matcher(upstream);
        if (schedule.matches()) {
            Competition c = byPath(schedule.group(1));
            if (c == null) return null;
            return RELAY_BASE + "/api/v1/source?kind=schedule&league=" + c.id + "&id=" + schedule.group(2)
                + (schedule.group(3) != null ? "&future=true" : "");
        }
        Matcher roster = ESPN_ROSTER.matcher(upstream);
        if (roster.matches()) {
            Competition c = byPath(roster.group(1));
            return c == null ? null : RELAY_BASE + "/api/v1/source?kind=roster&league=" + c.id + "&id=" + roster.group(2);
        }
        Matcher f1 = F1_CORE.matcher(upstream);
        if (f1.matches()) {
            String driver = f1.group(3);
            return driver == null
                ? RELAY_BASE + "/api/v1/source?kind=f1Core&event=" + f1.group(1) + "&session=" + f1.group(2)
                : RELAY_BASE + "/api/v1/source?kind=f1Stats&event=" + f1.group(1) + "&session=" + f1.group(2) + "&id=" + driver;
        }
        Matcher tier = FIVE_TIER.matcher(upstream);
        if (tier.matches()) {
            Map<String, String> q = queryMap(tier.group(1));
            if (!"1".equals(q.get("game_type"))) return null;
            String page = q.getOrDefault("page", "1");
            if (!page.matches("[1-8]")) return null;
            String status = q.get("game_status");
            String limit = q.get("limit");
            String grades = q.getOrDefault("grades", "");
            if ("0".equals(status) && "100".equals(limit) && "1%2C2%2C7".equalsIgnoreCase(grades)) {
                return RELAY_BASE + "/api/v1/source?kind=fiveTier&page=" + page;
            }
            if ("1".equals(status) && "100".equals(limit) && grades.isEmpty()) {
                return RELAY_BASE + "/api/v1/source?kind=fiveTier&current=true&page=" + page;
            }
            return null;
        }
        Matcher fiveTeam = FIVE_TEAM.matcher(upstream);
        if (fiveTeam.matches()) {
            return RELAY_BASE + "/api/v1/source?kind=fiveTeam&id=" + fiveTeam.group(1)
                + "&status=" + fiveTeam.group(3) + "&page=" + fiveTeam.group(2);
        }
        Matcher fiveDetail = FIVE_DETAIL.matcher(upstream);
        if (fiveDetail.matches()) {
            return RELAY_BASE + "/api/v1/source?kind=fiveDetail&id=" + fiveDetail.group(1);
        }
        return null;
    }

    private static Competition byPath(String path) {
        for (Competition c : Competition.ALL) if (!c.path.isEmpty() && c.path.equals(path)) return c;
        return null;
    }

    private static Map<String, String> queryMap(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    private static List<Route> routes(URL url, boolean allowRelay) {
        String host = url.getHost();
        boolean domestic = domesticHost(host);
        Network active = activeNetwork;
        Network phys = physicalNetwork;
        boolean vpn = vpnActive;
        Proxy proxy = explicitProxy(host);
        Map<String, Route> ordered = new LinkedHashMap<>();
        if (domestic) {
            if (phys != null && (vpn || !phys.equals(active))) {
                ordered.put("phys-direct", new Route("phys-direct", u -> (HttpURLConnection) phys.openConnection(u, Proxy.NO_PROXY), false));
            }
            if (proxy != null) {
                ordered.put("no-proxy", new Route("no-proxy", u -> (HttpURLConnection) (active != null && !vpn ? active.openConnection(u, Proxy.NO_PROXY) : u.openConnection(Proxy.NO_PROXY)), false));
            }
            ordered.put("default", new Route("default", u -> (HttpURLConnection) (active != null ? active.openConnection(u) : u.openConnection()), false));
            if (proxy != null) {
                ordered.put("proxy", new Route("proxy", u -> (HttpURLConnection) u.openConnection(proxy), false));
            }
        } else {
            if (proxy != null) {
                ordered.put("proxy", new Route("proxy", u -> (HttpURLConnection) u.openConnection(proxy), false));
            }
            ordered.put("default", new Route("default", u -> (HttpURLConnection) (active != null ? active.openConnection(u) : u.openConnection()), false));
            if (phys != null && (vpn || !phys.equals(active))) {
                ordered.put("phys-direct", new Route("phys-direct", u -> (HttpURLConnection) phys.openConnection(u, Proxy.NO_PROXY), false));
            } else if (proxy != null) {
                ordered.put("no-proxy", new Route("no-proxy", u -> (HttpURLConnection) u.openConnection(Proxy.NO_PROXY), false));
            }
        }
        if (allowRelay && relayUrl(url.toString()) != null) {
            if (phys != null && (vpn || !phys.equals(active))) {
                ordered.put("relay-direct", new Route("relay-direct", u -> (HttpURLConnection) phys.openConnection(u, Proxy.NO_PROXY), true));
            } else if (proxy != null) {
                ordered.put("relay-direct", new Route("relay-direct", u -> (HttpURLConnection) u.openConnection(Proxy.NO_PROXY), true));
            }
            ordered.put("relay", new Route("relay", u -> (HttpURLConnection) (active != null ? active.openConnection(u) : u.openConnection()), true));
        }
        List<Route> list = new ArrayList<>(ordered.values());
        if (list.size() <= 1) return list;
        long currentEpoch = EPOCH.get();
        long now = System.currentTimeMillis();
        RouteState state = ROUTE_STATES.get(hostGroup(host));
        if (state == null || state.epoch != currentEpoch || state.until <= now) return list;
        String pref = state.preferred;
        list.sort((a, b) -> {
            boolean aPref = !pref.isEmpty() && pref.equals(a.id);
            boolean bPref = !pref.isEmpty() && pref.equals(b.id);
            if (aPref != bPref) return aPref ? -1 : 1;
            boolean aPen = state.penalized.contains(a.id);
            boolean bPen = state.penalized.contains(b.id);
            if (aPen != bPen) return aPen ? 1 : -1;
            if (a.relay != b.relay) return a.relay ? 1 : -1;
            return 0;
        });
        return list;
    }

    private static void noteSuccess(String host, String routeId) {
        long currentEpoch = EPOCH.get();
        String group = hostGroup(host);
        RouteState state = ROUTE_STATES.compute(group, (k, existing) ->
            existing == null || existing.epoch != currentEpoch ? new RouteState(currentEpoch) : existing);
        state.preferred = routeId;
        state.penalized.remove(routeId);
        state.until = System.currentTimeMillis() + ROUTE_MEMORY_MS;
    }

    private static void noteFailure(String host, String routeId) {
        long currentEpoch = EPOCH.get();
        String group = hostGroup(host);
        RouteState state = ROUTE_STATES.compute(group, (k, existing) ->
            existing == null || existing.epoch != currentEpoch ? new RouteState(currentEpoch) : existing);
        state.penalized.add(routeId);
        if (routeId.equals(state.preferred)) state.preferred = "";
        state.until = System.currentTimeMillis() + ROUTE_MEMORY_MS;
    }

    static HttpURLConnection openDownload(URL url, int attempt) throws IOException {
        List<Route> candidates = routes(url, false);
        if (candidates.isEmpty()) return (HttpURLConnection) url.openConnection();
        Route chosen = candidates.get(Math.floorMod(attempt, candidates.size()));
        return chosen.opener.open(url);
    }

    static String get(String url, Map<String, String> headers) throws Exception {
        return get(url, headers, "application/json", u -> "https".equalsIgnoreCase(u.getProtocol()), true);
    }

    static String getRaw(String url, Map<String, String> headers, boolean sendAcceptJson) throws Exception {
        return get(url, headers, sendAcceptJson ? "application/json" : null, u -> "https".equalsIgnoreCase(u.getProtocol()), false);
    }

    static String get(String url, Map<String, String> headers, String defaultAccept, Predicate<URL> redirectAllowed, boolean allowRelay) throws Exception {
        byte[] data = execute("GET", url, null, headers, defaultAccept, 8_000_000, redirectAllowed, allowRelay);
        return new String(data, StandardCharsets.UTF_8);
    }

    static String post(String url, String body, Map<String, String> headers) throws Exception {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        byte[] data = execute("POST", url, payload, headers, "application/json", 8_000_000, u -> "https".equalsIgnoreCase(u.getProtocol()), false);
        return new String(data, StandardCharsets.UTF_8);
    }

    static byte[] bytes(String url, Map<String, String> headers, int maxBytes, Predicate<URL> redirectAllowed) throws Exception {
        return execute("GET", url, null, headers, "image/*", maxBytes, redirectAllowed, false);
    }

    private static byte[] execute(String method, String rawUrl, byte[] body, Map<String, String> headers,
            String defaultAccept, int maxBytes, Predicate<URL> redirectAllowed, boolean allowRelay) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        URL initial = new URL(rawUrl);
        if (redirectAllowed != null && !redirectAllowed.test(initial)) {
            throw new IOException("Unsupported request URL");
        }
        String relayTarget = allowRelay && "GET".equals(method) ? relayUrl(rawUrl) : null;
        List<Route> candidates = routes(initial, relayTarget != null);
        Exception primaryError = null;
        int total = candidates.size();
        for (int idx = 0; idx < total; idx++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Route route = candidates.get(idx);
            String effectiveUrl = route.relay ? relayTarget : rawUrl;
            if (effectiveUrl == null) continue;
            Map<String, String> reqHeaders = route.relay ? Collections.emptyMap() : (headers == null ? Collections.emptyMap() : headers);
            int connectTimeout = total > 1 && idx < total - 1 ? 4_500 : 6_500;
            int readTimeout = route.relay ? 12_000 : 8_500;
            try {
                byte[] raw = fetchOnce(method, new URL(effectiveUrl), body, reqHeaders,
                    route.relay ? "application/json" : defaultAccept,
                    maxBytes, route.relay ? u -> "https".equalsIgnoreCase(u.getProtocol()) : redirectAllowed,
                    route.opener, connectTimeout, readTimeout, false);
                if (route.relay) {
                    JSONObject wrapper = new JSONObject(new String(raw, StandardCharsets.UTF_8));
                    if (!wrapper.has("data") || wrapper.isNull("data")) {
                        throw new IOException(wrapper.optString("error", "数据服务未返回内容"));
                    }
                    raw = wrapper.get("data").toString().getBytes(StandardCharsets.UTF_8);
                }
                noteSuccess(initial.getHost(), route.id);
                return raw;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (InterruptedIOException e) {
                if (!(e instanceof SocketTimeoutException)) throw e;
                noteFailure(initial.getHost(), route.id);
                if (primaryError == null || !route.relay) primaryError = e;
            } catch (EOFException | SocketException stale) {
                // Retry once on the same route with Connection: close in case a pooled socket died.
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                try {
                    byte[] raw = fetchOnce(method, new URL(effectiveUrl), body, reqHeaders,
                        route.relay ? "application/json" : defaultAccept,
                        maxBytes, route.relay ? u -> "https".equalsIgnoreCase(u.getProtocol()) : redirectAllowed,
                        route.opener, connectTimeout, readTimeout, true);
                    if (route.relay) {
                        JSONObject wrapper = new JSONObject(new String(raw, StandardCharsets.UTF_8));
                        if (!wrapper.has("data") || wrapper.isNull("data")) {
                            throw new IOException(wrapper.optString("error", "数据服务未返回内容"));
                        }
                        raw = wrapper.get("data").toString().getBytes(StandardCharsets.UTF_8);
                    }
                    noteSuccess(initial.getHost(), route.id);
                    return raw;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                } catch (Exception retryFailed) {
                    noteFailure(initial.getHost(), route.id);
                    if (primaryError == null || !route.relay) primaryError = retryFailed;
                }
            } catch (HttpStatusException statusError) {
                noteFailure(initial.getHost(), route.id);
                if (primaryError == null || !route.relay) primaryError = statusError;
                if (!statusError.retryableRoute()) break;
            } catch (Exception e) {
                noteFailure(initial.getHost(), route.id);
                if (primaryError == null || !route.relay) primaryError = e;
            }
        }
        if (primaryError != null) throw primaryError;
        throw new IOException("连接失败");
    }

    static final class HttpStatusException extends IOException {
        final int code;
        HttpStatusException(int code, String prefix) {
            super(prefix + code);
            this.code = code;
        }
        boolean retryableRoute() {
            return code == 403 || code == 408 || code == 429 || code >= 500;
        }
    }

    private static byte[] fetchOnce(String initialMethod, URL initialUrl, byte[] initialBody,
            Map<String, String> headers, String defaultAccept, int maxBytes,
            Predicate<URL> redirectAllowed, Opener opener, int connectTimeout, int readTimeout,
            boolean closeSocket) throws Exception {
        URL url = initialUrl;
        String method = initialMethod;
        byte[] body = initialBody;
        String originHost = initialUrl.getHost();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (redirectAllowed != null && !redirectAllowed.test(url)) {
                throw new IOException("Redirect target blocked");
            }
            HttpURLConnection connection = opener.open(url);
            boolean complete = false;
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setRequestMethod(method);
                if (closeSocket) connection.setRequestProperty("Connection", "close");
                if (defaultAccept != null && !hasHeader(headers, "Accept")) {
                    connection.setRequestProperty("Accept", defaultAccept);
                }
                if (!hasHeader(headers, "Accept-Encoding")) {
                    connection.setRequestProperty("Accept-Encoding", "gzip");
                }
                if (!hasHeader(headers, "User-Agent")) {
                    connection.setRequestProperty("User-Agent", "MyScore-Personal/" + BuildConfig.VERSION_NAME + " Android");
                }
                boolean sameHost = originHost != null && originHost.equalsIgnoreCase(url.getHost());
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    if (!sameHost && "Authorization".equalsIgnoreCase(key)) continue;
                    connection.setRequestProperty(key, entry.getValue());
                }
                if (body != null && ("POST".equals(method) || "PUT".equals(method))) {
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body);
                    }
                }
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("重定向缺少目标地址");
                    try (InputStream ignored = connection.getInputStream()) {} catch (IOException ignored) {}
                    url = URI.create(url.toString()).resolve(location.trim()).toURL();
                    if (code == 303 || ((code == 301 || code == 302) && "POST".equals(method))) {
                        method = "GET";
                        body = null;
                    }
                    complete = true;
                    continue;
                }
                if (code != 200) {
                    String prefix = "image/*".equals(defaultAccept) ? "Image HTTP " : "来源返回 HTTP ";
                    throw new HttpStatusException(code, prefix);
                }
                InputStream rawStream = connection.getInputStream();
                String encoding = connection.getContentEncoding();
                boolean gzip = encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip");
                try (InputStream in = gzip ? new GZIPInputStream(rawStream) : rawStream;
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        if (System.nanoTime() > deadline) throw new SocketTimeoutException("来源响应超时");
                        if (out.size() + n > maxBytes) throw new IOException("来源响应过大");
                        out.write(buffer, 0, n);
                    }
                    byte[] result = out.toByteArray();
                    complete = true;
                    return result;
                }
            } finally {
                if (!complete || closeSocket) connection.disconnect();
            }
        }
        throw new IOException("重定向次数过多");
    }

    private static boolean hasHeader(Map<String, String> headers, String target) {
        if (headers == null || headers.isEmpty()) return false;
        for (String key : headers.keySet()) if (target.equalsIgnoreCase(key)) return true;
        return false;
    }
}
