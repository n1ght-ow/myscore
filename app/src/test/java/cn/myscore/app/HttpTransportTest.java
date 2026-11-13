package cn.myscore.app;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;

public class HttpTransportTest {
    @Test public void mapsExactUpstreamFeedsToFirstPartyRelayAndRejectsOthers() {
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=board&league=nba&date=2026-11-13",
            Http.relayUrl("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=20261113&limit=1000")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=board&league=f1&date=2026-01-01",
            Http.relayUrl("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard?dates=20261106-20261114&limit=1000")
        );
        assertNull(Http.relayUrl("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard?dates=20261229-20270102&limit=1000"));
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=summary&league=ucl&event=704321",
            Http.relayUrl("https://site.api.espn.com/apis/site/v2/sports/soccer/uefa.champions/summary?event=704321")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=schedule&league=epl&id=359&future=true",
            Http.relayUrl("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/teams/359/schedule?fixture=true")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=roster&league=nba&id=13",
            Http.relayUrl("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/13/roster")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=f1Core&event=600041&session=600045",
            Http.relayUrl("https://sports.core.api.espn.com/v2/sports/racing/leagues/f1/events/600041/competitions/600045?lang=en&region=us")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=f1Stats&event=600041&session=600045&id=4665",
            Http.relayUrl("https://sports.core.api.espn.com/v2/sports/racing/leagues/f1/events/600041/competitions/600045/competitors/4665/statistics?lang=en&region=us")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=fiveTier&page=2",
            Http.relayUrl("https://app.5eplay.com/api/tournament/session_list?game_type=1&game_status=0&grades=1%2C2%2C7&limit=100&page=2")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=fiveTier&current=true&page=1",
            Http.relayUrl("https://app.5eplay.com/api/tournament/session_list?game_type=1&game_status=1&grades=&limit=100&page=1")
        );
        assertNull(Http.relayUrl("https://app.5eplay.com/api/tournament/session_list?game_type=1&game_status=0&grades=1%2C2%2C7&limit=100&page=9"));
        assertNull(Http.relayUrl("https://app.5eplay.com/api/tournament/session_list?game_type=1&game_status=1&grades=&limit=20&page=1"));
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=fiveTeam&id=csgo_tm_7020&status=future&page=1",
            Http.relayUrl("https://esports-data.5eplaycdn.com/v1/api/csgo/team/matches_v1/csgo_tm_7020?page=1&limit=30&status=future")
        );
        assertEquals(
            Http.RELAY_BASE + "/api/v1/source?kind=fiveDetail&id=csgo_mc_2399751",
            Http.relayUrl("https://esports-data.5eplaycdn.com/v1/api/csgo/matches/csgo_mc_2399751/data")
        );
        assertNull(Http.relayUrl("https://fantasy.premierleague.com/api/bootstrap-static/"));
        assertNull(Http.relayUrl("https://api.github.com/repos/n1ght-ow/myscore/releases/latest"));
    }

    @Test public void classifiesDomesticAndOverseasHostsCorrectly() {
        for (String domestic : new String[]{
            "lpl.qq.com", "game.gtimg.cn", "open.tjstats.com",
            "app.5eplay.com", "esports-data.5eplaycdn.com",
            "api.bilibili.com", "i0.hdslb.com",
            "app-sc.miguvideo.com", "jadeite.migu.cn"
        }) {
            assertTrue(domestic, Http.domesticHost(domestic));
        }
        for (String overseas : new String[]{
            "site.api.espn.com", "a.espncdn.com", "github.com",
            "api.github.com", "fantasy.premierleague.com", ""
        }) {
            assertFalse(overseas, Http.domesticHost(overseas));
        }
    }

    @Test public void followsBoundedRedirectsAndDecompressesGzipPayloads() throws Exception {
        AtomicReference<String> seenAcceptEncoding = new AtomicReference<>("");
        AtomicBoolean sawCustomHeaderAfterRedirect = new AtomicBoolean(false);
        AtomicInteger dropOnce = new AtomicInteger();
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = server.getLocalPort();
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket client = server.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                        String requestLine = reader.readLine();
                        if (requestLine == null) continue;
                        String path = requestLine.split(" ")[1];
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            String lower = line.toLowerCase(Locale.ROOT);
                            if (lower.startsWith("accept-encoding:")) seenAcceptEncoding.set(line.substring(16).trim());
                            if (lower.startsWith("x-custom:") && line.substring(9).trim().equals("MyScoreTest")) {
                                sawCustomHeaderAfterRedirect.set(true);
                            }
                        }
                        OutputStream out = client.getOutputStream();
                        if ("/start".equals(path)) {
                            out.write(("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:" + port + "/final\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        } else if ("/final".equals(path)) {
                            if (dropOnce.getAndIncrement() == 0) {
                                client.close();
                                continue;
                            }
                            ByteArrayOutputStream raw = new ByteArrayOutputStream();
                            try (GZIPOutputStream gz = new GZIPOutputStream(raw)) {
                                gz.write("{\"ok\":true,\"msg\":\"多路直连测试\"}".getBytes(StandardCharsets.UTF_8));
                            }
                            byte[] body = raw.toByteArray();
                            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Encoding: gzip\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                            out.write(body);
                        } else if ("/loop".equals(path)) {
                            out.write(("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:" + port + "/loop\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        } else if ("/untrusted".equals(path)) {
                            out.write("HTTP/1.1 302 Found\r\nLocation: https://evil.invalid/payload\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                        }
                        out.flush();
                    } catch (IOException ignored) {
                        break;
                    }
                }
            }, "test-loopback-http");
            acceptor.setDaemon(true);
            acceptor.start();

            String decoded = Http.get(
                "http://127.0.0.1:" + port + "/start",
                Map.of("X-Custom", "MyScoreTest"),
                "application/json",
                u -> "http".equals(u.getProtocol()) && "127.0.0.1".equals(u.getHost()),
                false
            );
            assertEquals("{\"ok\":true,\"msg\":\"多路直连测试\"}", decoded);
            assertTrue(String.valueOf(seenAcceptEncoding.get()).contains("gzip"));
            assertTrue(sawCustomHeaderAfterRedirect.get());
            assertEquals("Stale socket drop should recover cleanly on retry", 2, dropOnce.get());

            try {
                Http.get(
                    "http://127.0.0.1:" + port + "/loop",
                    Map.of(),
                    "application/json",
                    u -> "http".equals(u.getProtocol()) && "127.0.0.1".equals(u.getHost()),
                    false
                );
                fail("Redirect loop must fail");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("重定向"));
            }

            try {
                Http.get(
                    "http://127.0.0.1:" + port + "/untrusted",
                    Map.of(),
                    "application/json",
                    u -> "http".equals(u.getProtocol()) && "127.0.0.1".equals(u.getHost()),
                    false
                );
                fail("Blocked redirect host must fail");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Redirect"));
            }
        }
    }

    @Test public void bumpsEpochAndNotifiesActiveListenersOnly() throws Exception {
        AtomicInteger count = new AtomicInteger();
        long before = Http.epoch();
        AutoCloseable reg = Http.onNetworkChanged(count::incrementAndGet);
        Http.bumpEpoch();
        assertEquals(before + 1, Http.epoch());
        assertEquals(1, count.get());
        reg.close();
        Http.bumpEpoch();
        assertEquals(1, count.get());
    }
}
