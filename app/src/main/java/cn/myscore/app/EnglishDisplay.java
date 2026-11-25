package cn.myscore.app;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EnglishDisplay {
    private static final Map<String, String> LABELS = labels();
    private static final Pattern QUARTER = Pattern.compile("第\\s*(\\d+)\\s*节");
    private static final Pattern LAP = Pattern.compile("第\\s*(\\d+)\\s*圈");
    private static final Pattern MAP = Pattern.compile("第\\s*(\\d+)\\s*图");
    private static final Pattern STAGE = Pattern.compile("第\\s*([123])\\s*阶段");
    private static final Pattern DATE = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern MONTH = Pattern.compile("(\\d{4})年(\\d{1,2})月");
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private EnglishDisplay() {}

    static String sport(String value) { return text(value); }

    static CharSequence text(CharSequence value) {
        if (value == null) return "";
        String original = value.toString();
        String translated = text(original);
        return original.equals(translated) ? value : translated;
    }

    static String text(String value) {
        if (value == null || value.equals("null")) return "";
        if (!hasHan(value)) return value;
        String exact = LABELS.get(value);
        if (exact != null) return exact;
        String team = MiguTeams.english(value);
        if (team != null) return team;
        String converted = dates(tournamentTerms(value));
        converted = QUARTER.matcher(converted).replaceAll("Q$1");
        converted = LAP.matcher(converted).replaceAll("Lap $1");
        converted = MAP.matcher(converted).replaceAll("Map $1");
        converted = STAGE.matcher(converted).replaceAll("Stage $1");
        StringBuilder result = new StringBuilder();
        for (int start = 0; start < converted.length();) {
            if (Character.UnicodeScript.of(converted.codePointAt(start)) != Character.UnicodeScript.HAN) {
                int length = Character.charCount(converted.codePointAt(start));
                result.append(converted, start, start + length);
                start += length;
                continue;
            }
            int end = start;
            while (end < converted.length() && Character.UnicodeScript.of(converted.codePointAt(end)) == Character.UnicodeScript.HAN) {
                end += Character.charCount(converted.codePointAt(end));
            }
            String term = converted.substring(start, end);
            String label = LABELS.get(term);
            if (label == null) label = MiguTeams.english(term);
            if (label == null) label = term;
            if (result.length() > 0 && Character.isLetterOrDigit(result.charAt(result.length() - 1)) && !label.isEmpty()) result.append(' ');
            result.append(label);
            if (end < converted.length() && Character.isLetterOrDigit(converted.charAt(end)) && !label.isEmpty()) result.append(' ');
            start = end;
        }
        return result.toString().replace("，", ", ").replace("。", ".").replace("（", " (").replace("）", ")");
    }

    static boolean hasHan(String value) {
        for (int index = 0; index < value.length();) {
            int code = value.codePointAt(index);
            if (code >= 0x2E80 && Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN) return true;
            index += Character.charCount(code);
        }
        return false;
    }

    private static String dates(String value) {
        Matcher date = DATE.matcher(value);
        StringBuffer result = new StringBuffer();
        while (date.find()) {
            int month = Integer.parseInt(date.group(2));
            String shown = month < 1 || month > 12 ? date.group() : MONTHS[month - 1] + " " + date.group(3)
                + (date.group(1) == null ? "" : ", " + date.group(1));
            date.appendReplacement(result, Matcher.quoteReplacement(shown));
        }
        date.appendTail(result);
        Matcher month = MONTH.matcher(result.toString());
        StringBuffer withMonths = new StringBuffer();
        while (month.find()) {
            int number = Integer.parseInt(month.group(2));
            month.appendReplacement(withMonths, Matcher.quoteReplacement(number < 1 || number > 12 ? month.group() : MONTHS[number - 1] + " " + month.group(1)));
        }
        return month.appendTail(withMonths).toString();
    }

    static String tournament(String source) {
        return text(tournamentTerms(source));
    }

    static String watchTitle(WatchLinks.Link link) {
        return switch (link.kind) {
            case ZONE -> link.title.contains("英超") ? "Watch Premier League" : "Watch NBA";
            case ROOM -> "Open live stream";
            case LIVE -> "Watch live";
            case REPLAY -> "Watch replay";
            case HIGHLIGHTS -> "Watch highlights";
            case PENDING -> link.title.contains("集锦") ? "Highlights pending" : "Replay pending";
        };
    }

    private static String tournamentTerms(String source) {
        String converted = source.replace("拉斯维加斯大奖赛", "Las Vegas Grand Prix").replace("北京时间", "Beijing time").replace("新加坡", "Singapore").replace("科隆", "Cologne").replace("北京", "Beijing")
            .replace("布加勒斯特", "Bucharest").replace("波尔图", "Porto").replace("里约", "Rio")
            .replace("成都", "Chengdu").replace("达拉斯", "Dallas").replace("卡托维兹", "Katowice")
            .replace("克拉科夫", "Krakow").replace("上海", "Shanghai").replace("伦敦", "London")
            .replace("奥斯汀", "Austin").replace("布达佩斯", "Budapest").replace("哥本哈根", "Copenhagen")
            .replace("拉斯维加斯", "Las Vegas")
            .replace("第一阶段", "Stage 1").replace("第二阶段", "Stage 2").replace("第三阶段", "Stage 3")
            .replace("大师赛", " Masters").replace("公开赛", " Open").replace("世界锦标赛", "World Championship")
            .replace("电竞世俱杯", "Esports World Cup").replace("对抗赛", " Rivals").replace("春季赛", " Spring")
            .replace("秋季赛", " Fall").replace("裂变天地", " Playground");
        if (!converted.equals(source)) converted = converted.replaceAll(" {2,}", " ").strip();
        return converted;
    }

    private static Map<String, String> labels() {
        Map<String, String> result = new HashMap<>();
        for (String line : WORDS.split("\\R")) {
            int split = line.indexOf('=');
            if (split > 0) result.put(line.substring(0, split), line.substring(split + 1));
        }
        return result;
    }

    private static final String WORDS = """
        全部=All
        足球=Football
        英超=Premier League
        欧冠=Champions League
        国家队=National teams
        足总杯=FA Cup
        卡拉宝杯=Carabao Cup
        赛程=Schedule
        赛事=Competition
        阶段=Stage
        级别=Tier
        赛制=Format
        系列赛=Series
        转播=Broadcast
        奖金=Prize pool
        选手=Player
        球员=Player
        球队=Team
        战队=Team
        说明=Notes
        状态=Status
        名次=Rank
        位置=Position
        场地=Venue
        地点=Location
        中立场地=Neutral site
        场地类型=Surface
        草地=Grass
        观众=Attendance
        开始时间=Start time
        裁判=Referee
        主裁判=Head referee
        助理裁判=Assistant referee
        场次=Session
        大奖赛=Grand Prix
        赛道=Circuit
        本场车手与车队=Drivers and teams
        车手=Driver
        车队=Constructor
        车号=No.
        圈数=Laps
        积分=Points
        成绩 / 用时=Result / time
        场次成绩=Session results
        比赛周末=Race weekend
        练习赛=Practice
        排位赛=Qualifying
        正赛=Race
        冲刺赛=Sprint
        冲刺排位=Sprint qualifying
        场次待确认=Session TBD
        场次待定=Session TBD
        时间待定=Time TBD
        待定=TBD
        对阵待定=Matchup TBD
        主队待定=Home team TBD
        客队待定=Away team TBD
        未开始=Scheduled
        待开赛=Scheduled
        即将开始=Upcoming
        进行中=Live
        已结束=Final
        已完赛=Final
        完赛=Final
        延期=Postponed
        已延期=Postponed
        暂停=Suspended
        取消=Cancelled
        已取消=Cancelled
        中止=Abandoned
        状态待确认=Status unknown
        开球=Kickoff
        中场=Halftime
        中场休息=Halftime
        节间休息=End of period
        常规时间结束=End of regulation
        点球大战=Penalty shootout
        点球=Pens
        加时=OT
        欧洲区=Europe
        欧国联=UEFA Nations League
        世界杯=World Cup
        世预赛=World Cup qualifying
        欧洲杯=UEFA Euro
        欧洲杯预选赛=Euro qualifying
        国际友谊赛=International friendly
        挪威=Norway
        英格兰=England
        西班牙=Spain
        星期一=Monday
        星期二=Tuesday
        星期三=Wednesday
        星期四=Thursday
        星期五=Friday
        星期六=Saturday
        星期日=Sunday
        周一=Mon
        周二=Tue
        周三=Wed
        周四=Thu
        周五=Fri
        周六=Sat
        周日=Sun
        今天=Today
        昨天=Yesterday
        明天=Tomorrow
        北京时间=Beijing time
        晋级=Advance
        淘汰=Eliminated
        小组赛=Group stage
        淘汰赛=Playoffs
        瑞士轮=Swiss stage
        胜者组=Upper bracket
        败者组=Lower bracket
        最终席位=Outcome
        首轮=Opening round
        第一轮=Round 1
        第二轮=Round 2
        第三轮=Round 3
        第四轮=Round 4
        第五轮=Round 5
        预赛=Preliminary round
        半决赛=Semifinals
        决赛=Final
        总决赛=Grand final
        季军赛=Third place
        1/4决赛=Quarterfinals
        四分之一决赛=Quarterfinals
        首回合=First leg
        次回合=Second leg
        重赛=Replay
        冠军=Champion
        种子=Seed
        小组第一=Group winner
        小组第二=Group runner-up
        小组第三=Third in group
        源暂未提供完整签表=No complete bracket published
        来源暂未提供完整签表。=The source has not published a complete bracket.
        地图=Map
        地图比分=Map scores
        分节比分=Scoring by period
        全场=Overall
        首开方=Started as
        T 方=T
        CT 方=CT
        攻守半场=Halves
        上半场=First half
        下半场=Second half
        总分=Total
        获胜=won
        是=Yes
        控球率 %=Possession %
        控球率 / 时间=Possession
        控球时间=Time of possession
        射门=Shots
        射正=On target
        犯规=Fouls
        黄牌=Yellow cards
        红牌=Red cards
        越位=Offsides
        角球=Corners
        扑救=Saves
        成功传球=Accurate passes
        传球=Passing
        传中=Crosses
        成功传中=Accurate crosses
        长传=Long balls
        成功长传=Accurate long balls
        封堵射门=Blocked shots
        成功抢断=Successful tackles
        抢断=Steals
        拦截=Interceptions
        解围=Clearances
        成功解围=Effective clearances
        篮板=REB
        助攻=AST
        盖帽=BLK
        失误=TO
        总失误=Turnovers
        投篮命中率 %=FG %
        三分命中率 %=3PT %
        罚球命中率 %=FT %
        内线得分=Points in the paint
        快攻得分=Fast break points
        最大领先=Largest lead
        领先交替=Lead changes
        总推进码数=Total yards
        净传球码数=Net passing yards
        冲球码数=Rushing yards
        首攻=First downs
        三档成功率=Third-down efficiency
        四档成功率=Fourth-down efficiency
        进球=Goal
        乌龙球=Own goal
        换人=Substitution
        换上=On
        换下=Off
        已换下=Subbed off
        替补登场=Subbed on
        首发=Starting
        替补=Bench
        未出场=DNP
        站位=Formation spot
        阵型=Formation
        评分=Rating
        成功盘带=Successful dribbles
        国籍=Nationality
        年龄=Age
        号码=No.
        门将=Goalkeeper
        中卫=CB
        左中卫=LCB
        右中卫=RCB
        左后卫=LB
        右后卫=RB
        左翼卫=LWB
        右翼卫=RWB
        中前卫=CM
        左中前卫=LCM
        右中前卫=RCM
        中场球员=Midfielder
        左前卫=LM
        右前卫=RM
        前腰=AM
        左前腰=LAM
        右前腰=RAM
        后腰=DM
        左后腰=LDM
        右后腰=RDM
        前锋=Forward
        中锋=CF
        左中锋=LCF
        右中锋=RCF
        左边锋=LW
        右边锋=RW
        后卫=Defender
        边锋=Winger
        影锋=Second striker
        清道夫=Sweeper
        冲球=Rushing
        接球=Receiving
        防守=Defense
        踢球=Kicking
        弃踢=Punting
        掉球=Fumbles
        开球回攻=Kick returns
        弃踢回攻=Punt returns
        时间=MIN
        得分=PTS
        投篮=FG
        三分=3PT
        罚球=FT
        前场板=OREB
        后场板=DREB
        正负值=+/−
        进攻篮板=Offensive rebounds
        防守篮板=Defensive rebounds
        抄截=Interceptions
        伤停=Injuries
        缺阵=Out
        出战存疑=Doubtful
        受伤=Injured
        停赛=Suspended
        每日观察=Day-to-day
        球员资料来源=Player data source
        英超官方 FPL（照片按姓名与出生日期匹配）=Official Premier League FPL (photos matched by name and date of birth)
        地图比分仅展示已结束小局；选手统计按来源更新。=Map scores show completed maps only. Player stats follow the source.
        战队赛程=Team schedule
        一线赛程=Top-tier fixtures
        球队赛程=Team schedule
        榜单=edition
        获取=retrieved
        已选择=Selected
        已选中=Selected
        来源未返回赛事列表=The source did not return a fixture list
        赛事列表达到来源上限，覆盖可能不完整=The source reached its fixture limit; coverage may be incomplete
        未知赛事=Unknown competition
        比赛标识无效=Invalid match ID
        暂不支持该来源详情=Match details are unavailable for this source
        F1 场次身份不匹配=F1 session ID mismatch
        来源未找到该 F1 场次=The source could not find this F1 session
        CS2 详情读取失败=Could not load CS2 match details
        CS2 比赛身份不匹配=CS2 match ID mismatch
        比赛详情身份不匹配=Match detail ID mismatch
        5EPlay 战队赛程格式变化=The 5EPlay team schedule format has changed
        5EPlay 战队赛程返回失败=Could not load the 5EPlay team schedule
        5EPlay 一线赛程格式变化=The 5EPlay fixture format has changed
        5EPlay 一线赛程读取失败=Could not load top-tier fixtures from 5EPlay
        5EPlay 当前赛程读取失败=Could not load current fixtures from 5EPlay
        5EPlay 一线赛程缺少开赛时间=5EPlay did not provide kickoff times
        5EPlay 暂未返回该队赛程，请稍后刷新重试=5EPlay did not return this team's schedule. Try refreshing later.
        CS2 一线赛事仅提供最近约 1 年=Top-tier CS2 fixtures are available for roughly the past year only
        CS2 赛程读取失败=Could not load CS2 fixtures
        球队标识无效=Invalid team ID
        F1 没有球队赛程=F1 does not have team schedules
        来源返回了其他球队的赛程=The source returned a schedule for a different team
        Spirit 战队赛程超过分页上限=The Spirit schedule exceeded the source's page limit
        Spirit 比赛详情暂不可用=Spirit match details are temporarily unavailable
        Spirit 比赛详情标识不匹配=Spirit match detail ID mismatch
        位车手的统计暂未获取=drivers are missing stats
        空缺不代表=Missing values do not mean
        咪咕视频=Migu Video
        咪咕=Migu
        斗鱼=Douyu
        哔哩哔哩=Bilibili
        B站=Bilibili
        专区=League hub
        直播间=Live room
        直播=Live
        回放=Replay
        集锦=Highlights
        待发布=Pending
        观看英超直播=Watch Premier League
        观看 NBA 直播=Watch NBA
        直播、回放与赛程=Live games, replays and schedule
        打开直播间=Open live stream
        赛事直播=Live stream
        集锦待发布=Highlights pending
        赛后集锦稍后更新=Highlights will be posted later
        录像待发布=Replay pending
        稍后更新=Available later
        观看本场集锦=Watch highlights
        观看全场录像=Watch full replay
        观看本场回放=Watch replay
        观看本场直播=Watch live
        下载信息已失效，请重新检查更新=This download has expired. Check for updates again.
        存储空间不足，请清理后重新下载=Not enough storage. Free some space and try again.
        无法保存安装包，请检查存储空间后重试=Could not save the installer. Check available storage and try again.
        更新记录=Release notes
        本机=Local
        本机日历=Local calendar
        系统日历=System calendar
        小米日历=Xiaomi Calendar
        """;
}
