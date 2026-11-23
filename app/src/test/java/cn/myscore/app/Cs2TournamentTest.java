package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Cs2TournamentTest {
    @Test public void singleEliminationKeepsFutureScoresBlankAndFinishedWinner() throws Exception {
        List<Cs2Tournament.Stage> stages = Cs2Tournament.parse(singleRoot());

        assertEquals(1, stages.size());
        assertEquals("淘汰赛", stages.get(0).label());
        Cs2Tournament.Group group = stages.get(0).groups().get(0);
        assertEquals("single", group.format());
        List<Cs2Tournament.Game> games = games(group);
        assertEquals(2, games.size());

        Cs2Tournament.Game future = game(games, "csgo_mc_1");
        assertEquals("m_r1-1", future.graphId());
        assertEquals("Nov 24 20:00", future.time());
        assertEquals("", future.first().score());
        assertEquals("", future.second().score());
        assertFalse(future.first().winner());
        assertFalse(future.second().winner());
        assertEquals("BO3", future.bestOf());
        assertEquals("", future.status());

        Cs2Tournament.Game finished = game(games, "csgo_mc_2");
        assertEquals("2", finished.first().score());
        assertEquals("0", finished.second().score());
        assertTrue(finished.first().winner());
        assertFalse(finished.second().winner());
        assertEquals("", finished.first().from());
        assertEquals("", finished.first().fromLabel());
        assertEquals("m_r1-1", finished.second().from());
        assertEquals("已结束", finished.status());
    }

    @Test public void standardSixteenTeamSwissUsesActualRecordPaths() throws Exception {
        List<Cs2Tournament.Stage> stages = Cs2Tournament.parse(swissRoot());

        assertEquals(1, stages.size());
        Cs2Tournament.Group group = stages.get(0).groups().get(0);
        assertEquals("swiss", group.format());
        assertEquals(6, group.lanes().size());
        for (int index = 0; index < 5; index++) roundLabel(group.lanes().get(index).label(), index + 1);
        assertEquals("最终席位", group.lanes().get(5).label());

        Map<String, Cs2Tournament.Bucket> buckets = new HashMap<>();
        Map<String, Cs2Tournament.Bucket> byId = new HashMap<>();
        for (Cs2Tournament.Lane lane : group.lanes()) {
            for (Cs2Tournament.Bucket bucket : lane.buckets()) {
                assertNull(buckets.put(bucket.label(), bucket));
                byId.put(bucket.id(), bucket);
            }
        }
        for (String label : List.of("0:0", "1:0", "0:1", "2:0", "1:1", "0:2", "2:1", "1:2", "2:2")) {
            assertNotNull("missing Swiss record bucket " + label, buckets.get(label));
        }
        assertNext(buckets, byId, "0:0", "1:0", "0:1");
        assertNext(buckets, byId, "1:0", "2:0", "1:1");
        assertNext(buckets, byId, "0:1", "1:1", "0:2");
        assertNext(buckets, byId, "1:1", "2:1", "1:2");
        assertNext(buckets, byId, "2:0", "3:0", "2:1");
        assertNext(buckets, byId, "0:2", "1:2", "0:3");
        assertNext(buckets, byId, "2:1", "3:1", "2:2");
        assertNext(buckets, byId, "1:2", "2:2", "1:3");
        assertNext(buckets, byId, "2:2", "3:2", "2:3");
        for (String label : List.of("3:0", "3:1", "3:2", "0:3", "1:3", "2:3")) assertTrue(buckets.get(label).next().isEmpty());
        assertEquals(16, group.teams().size());
        assertEquals(8L, group.teams().stream().filter(team -> team.status().equals("晋级")).count());
        assertEquals(8L, group.teams().stream().filter(team -> team.status().equals("淘汰")).count());
    }

    @Test public void rejectsMalformedStagePayloads() throws Exception {
        rejects(() -> Cs2Tournament.parse(new JSONObject().put("success", false)));
        rejects(() -> Cs2Tournament.parse(new JSONObject().put("success", true)));
        rejects(() -> Cs2Tournament.parse(new JSONObject().put("success", true).put("data", new JSONObject())));

        JSONObject broken = root("single", new JSONObject());
        broken.getJSONArray("data").getJSONObject(0).getJSONArray("groups").getJSONObject(0).put("graph", "{");
        rejects(() -> Cs2Tournament.parse(broken));
    }

    @Test public void unknownFormatsDoNotInventGroups() throws Exception {
        List<Cs2Tournament.Stage> stages = Cs2Tournament.parse(root("mystery", new JSONObject()));

        assertEquals(1, stages.size());
        assertTrue(stages.get(0).groups().isEmpty());
    }

    private static JSONObject singleRoot() throws Exception {
        JSONObject graph = new JSONObject();
        JSONArray rounds = new JSONArray();
        rounds.put(new JSONObject()
            .put("basic", new JSONObject().put("name", "半决赛"))
            .put("matches", new JSONArray()
                .put(match("csgo_mc_1", "m_r1-1", side("Alpha", "csgo_tm_1", ""), side("Beta", "csgo_tm_2", ""),
                    "upcoming", "0", "0", "draw", "2026-11-24 12:00:00"))
                .put(match("csgo_mc_2", "m_r1-2", sideWithFrom("Mongolz", "csgo_tm_3", "m_out", "m_out"),
                    sideWithFrom("Natus Vincere", "csgo_tm_4", "m_r1-1", ""),
                    "past", "2", "0", "t1", "2026-11-23 16:00:00"))));
        graph.put("sEB", new JSONObject().put("EB", new JSONObject().put("rounds", rounds)));
        return root("sEB", graph);
    }

    private static JSONObject swissRoot() throws Exception {
        JSONObject swiss = new JSONObject();
        int[] game = {1};
        addSwissRound(swiss, "r1", "groupAll", 8, game);
        addSwissRound(swiss, "r2", "groupHigh", 4, game);
        swiss.getJSONObject("r2").put("groupLow", swissBucket(4, game));
        addSwissRound(swiss, "r3", "groupHigh", 2, game);
        swiss.getJSONObject("r3").put("groupMid", swissBucket(4, game)).put("groupLow", swissBucket(2, game));
        addSwissRound(swiss, "r4", "groupHigh", 3, game);
        swiss.getJSONObject("r4").put("groupLow", swissBucket(3, game));
        addSwissRound(swiss, "r5", "groupAll", 3, game);

        JSONArray ranks = new JSONArray();
        for (int index = 0; index < 16; index++) {
            int wins = index < 2 ? 3 : index < 6 ? 3 : index < 8 ? 3 : index < 10 ? 2 : index < 14 ? 1 : 0;
            int losses = index < 2 ? 0 : index < 6 ? 1 : index < 8 ? 2 : index < 10 ? 3 : index < 14 ? 3 : 3;
            ranks.put(new JSONObject()
                .put("absId", "csgo_tm_rank_" + index)
                .put("nameEn", "Team " + index)
                .put("abbrEn", "T" + index)
                .put("logo", "https://example.test/t" + index + ".png")
                .put("winCnt", String.valueOf(wins))
                .put("lossCnt", String.valueOf(losses))
                .put("score_result", wins + "-" + losses)
                .put("promotion_label", index < 8 ? 1 : 4)
                .put("promotion_result", "sure"));
        }
        JSONObject graph = new JSONObject()
            .put("id", "1")
            .put("name", "Group A")
            .put("swiss", swiss)
            .put("curRankList", ranks);
        return root("swiss", graph);
    }

    private static void addSwissRound(JSONObject swiss, String round, String bucket, int count, int[] game) throws Exception {
        JSONObject value = new JSONObject().put("basic", new JSONObject().put("name", ""));
        value.put(bucket, swissBucket(count, game));
        swiss.put(round, value);
    }

    private static JSONObject swissBucket(int count, int[] game) throws Exception {
        JSONArray matches = new JSONArray();
        for (int index = 0; index < count; index++) {
            int number = game[0]++;
            matches.put(match("csgo_mc_" + number, "", side("Team A" + number, "csgo_tm_a" + number, ""),
                side("Team B" + number, "csgo_tm_b" + number, ""), "past", "2", "0", "t1", "2026-11-24 12:00:00"));
        }
        return new JSONObject().put("name", "").put("matches", matches);
    }

    private static JSONObject match(String id, String graphId, JSONObject first, JSONObject second, String status,
            String firstScore, String secondScore, String result, String time) throws Exception {
        return new JSONObject()
            .put("t1", first)
            .put("t2", second)
            .put("absId", id)
            .put("graphId", graphId)
            .put("format", "3")
            .put("planTs", time)
            .put("result", result)
            .put("status", status)
            .put("t1Score", firstScore)
            .put("t2Score", secondScore);
    }

    private static JSONObject side(String name, String id, String logo) throws Exception {
        return sideWithFrom(name, id, "");
    }

    private static JSONObject sideWithFrom(String name, String id, String from) throws Exception {
        return sideWithFrom(name, id, from, "");
    }

    private static JSONObject sideWithFrom(String name, String id, String from, String fromLabel) throws Exception {
        return new JSONObject()
            .put("TBD", false)
            .put("absId", id)
            .put("abbrEn", name)
            .put("nameEn", name)
            .put("nameZh", name)
            .put("logo", "https://example.test/" + id + ".png")
            .put("fromMatchId", from)
            .put("fromDesc", fromLabel);
    }

    private static JSONObject root(String format, JSONObject graph) throws Exception {
        JSONObject info = new JSONObject().put("name", "淘汰赛").put("desc", "单败Bo3").put("fmtType", format);
        JSONObject group = new JSONObject().put("name", "淘汰赛").put("graph", graph.toString());
        return new JSONObject()
            .put("success", true)
            .put("data", new JSONArray().put(new JSONObject()
                .put("stageInfo", info)
                .put("groups", new JSONArray().put(group))));
    }

    private static List<Cs2Tournament.Game> games(Cs2Tournament.Group group) {
        List<Cs2Tournament.Game> games = new ArrayList<>();
        for (Cs2Tournament.Lane lane : group.lanes()) {
            for (Cs2Tournament.Bucket bucket : lane.buckets()) games.addAll(bucket.games());
        }
        return games;
    }

    private static Cs2Tournament.Game game(List<Cs2Tournament.Game> games, String id) {
        for (Cs2Tournament.Game game : games) if (game.id().equals(id)) return game;
        fail("missing game " + id);
        return null;
    }

    private static void assertNext(Map<String, Cs2Tournament.Bucket> buckets, Map<String, Cs2Tournament.Bucket> byId,
            String source, String... targets) {
        List<String> expected = new ArrayList<>(List.of(targets));
        List<String> actual = new ArrayList<>();
        for (String id : buckets.get(source).next()) {
            Cs2Tournament.Bucket bucket = byId.get(id);
            assertNotNull("missing Swiss next bucket " + id, bucket);
            actual.add(bucket.label());
        }
        expected.sort(String::compareTo);
        actual.sort(String::compareTo);
        assertEquals(expected, actual);
    }

    private static void roundLabel(String label, int round) {
        assertNotNull(label);
        assertTrue(label, label.contains(String.valueOf(round)) || label.contains("一二三四五".substring(round - 1, round)));
    }

    @FunctionalInterface
    private interface Attempt {
        void run() throws Exception;
    }

    private static void rejects(Attempt attempt) {
        try {
            attempt.run();
            fail("malformed or unsupported tournament data must be rejected");
        } catch (Exception expected) {
        }
    }
}
