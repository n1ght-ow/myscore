package cn.myscore.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Tablet score rail: one dense, selectable row per fixture. Selecting a row fills the reading
 * pane; the same row opened a second time (or via the reading pane) still reaches the full match
 * centre. Cards never fabricate a score or a state.
 */
final class PadScoreAdapter extends BaseAdapter {
    interface Host {
        void selectMatch(Match match);
        void openMatch(Match match);
        void openTeam(Match match,boolean home);
        void toggleSubscription(Match match);
        void openWatch(Match match);
        WatchLinks.Link watchLink(Match match);
        boolean isSubscribed(String id);
        void showSourceStatus();
    }
    private final Ui ui;
    private final Host host;
    private List<ScoreRow> rows=new ArrayList<>();
    private String selectedId="";
    private ListView list;

    PadScoreAdapter(Ui ui,Host host){this.ui=ui;this.host=host;}
    void attach(ListView list){this.list=list;}

    void updateRows(List<ScoreRow> next){
        boolean same=next.size()==rows.size();
        if(same)for(int i=0;i<next.size();i++)if(!next.get(i).key().equals(rows.get(i).key())){same=false;break;}
        rows=next;
        if(!same||(list!=null&&list.isLayoutRequested())){notifyDataSetChanged();return;}
        if(list==null)return;
        int first=list.getFirstVisiblePosition();
        for(int i=0;i<list.getChildCount();i++){int position=first+i;if(position>=0&&position<rows.size())getView(position,list.getChildAt(i),list);}
    }
    void setSelected(String id){
        if(id==null)id="";
        if(id.equals(selectedId))return;
        selectedId=id;
        if(list==null){notifyDataSetChanged();return;}
        int first=list.getFirstVisiblePosition();
        for(int i=0;i<list.getChildCount();i++){int position=first+i;if(position>=0&&position<rows.size())getView(position,list.getChildAt(i),list);}
    }

    @Override public int getCount(){return rows.size();}
    @Override public ScoreRow getItem(int position){return rows.get(position);}
    @Override public long getItemId(int position){return position;}
    @Override public boolean hasStableIds(){return false;}
    @Override public int getViewTypeCount(){return 6;}
    @Override public int getItemViewType(int position){return rows.get(position).type;}

    @Override public View getView(int position,View convertView,ViewGroup parent){
        ScoreRow row=rows.get(position);
        switch(row.type){
            case ScoreRow.LOADING: {
                TextView text=(TextView)convertView;
                if(text==null){text=ui.text("",13,ui.muted,false);text.setPadding(ui.dp(6),ui.dp(24),0,ui.dp(12));}
                text.setText(row.text);return text;
            }
            case ScoreRow.SECTION: {
                TextView text=(TextView)convertView;
                if(text==null){text=ui.text("",13,ui.text,true);text.setPadding(ui.dp(4),ui.dp(22),0,ui.dp(8));text.setLetterSpacing(-0.01f);}
                text.setText(row.text);text.setTextColor(row.text.startsWith("进行中")?ui.primary:ui.text);return text;
            }
            case ScoreRow.FAILURE: {
                TextView banner=(TextView)convertView;
                if(banner==null){
                    banner=ui.text("",13,ui.error,true);banner.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));
                    ui.clickable(banner,ui.surface,Ui.CARD_RADIUS);banner.setMinHeight(ui.dp(48));banner.setFocusable(true);
                    banner.setOnClickListener(v->host.showSourceStatus());
                }
                banner.setText(row.text);return banner;
            }
            case ScoreRow.EMPTY: {
                Object tag=convertView==null?null:convertView.getTag();
                EmptyHolder holder=tag instanceof EmptyHolder?(EmptyHolder)tag:new EmptyHolder();
                holder.root.setTag(holder);
                holder.title.setText(row.title);holder.message.setText(row.message);return holder.root;
            }
            case ScoreRow.F1: {
                Object tag=convertView==null?null:convertView.getTag();
                F1Holder holder=tag instanceof F1Holder?(F1Holder)tag:new F1Holder();
                holder.root.setTag(holder);
                holder.bind(row.match);return holder.root;
            }
            default: {
                Object tag=convertView==null?null:convertView.getTag();
                MatchHolder holder=tag instanceof MatchHolder?(MatchHolder)tag:new MatchHolder();
                holder.root.setTag(holder);
                holder.bind(row.match);return holder.root;
            }
        }
    }

    private final class EmptyHolder {
        final LinearLayout root;final TextView title,message;
        EmptyHolder(){
            root=ui.column();root.setPadding(0,ui.dp(18),0,ui.dp(18));
            LinearLayout card=ui.column();card.setBackground(ui.panel());card.setPadding(ui.dp(20),ui.dp(22),ui.dp(20),ui.dp(22));
            title=ui.text("",17,ui.text,true);message=ui.text("",13,ui.muted,false);message.setLineSpacing(ui.dp(4),1);
            card.addView(title);ui.gap(card,8);card.addView(message);root.addView(card);
        }
    }

    private final class F1Holder {
        final LinearLayout root,card;final FrameLayout selectedMark;final TextView time,title,state,provenance;
        Match match;
        F1Holder(){
            root=ui.column();root.setPadding(0,0,0,ui.dp(8));
            card=ui.row();card.setPadding(ui.dp(14),ui.dp(12),ui.dp(10),ui.dp(12));
            ui.clickable(card,ui.surface,16);card.setFocusable(true);
            LinearLayout clock=ui.column();time=ui.text("",15,ui.text,true);state=ui.text("",10,ui.muted,false);
            clock.addView(time);clock.addView(state);card.addView(clock,new LinearLayout.LayoutParams(ui.dp(62),-2));
            LinearLayout words=ui.column();title=ui.text("",15,ui.text,true);provenance=ui.text("",10,ui.muted,false);
            words.addView(title);ui.gap(words,4);words.addView(provenance);card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            selectedMark=new FrameLayout(ui.c);selectedMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            selectedMark.addView(new Ui.Icon(ui.c,"check",ui.text),new FrameLayout.LayoutParams(ui.dp(13),ui.dp(13),Gravity.CENTER));
            selectedMark.setVisibility(View.INVISIBLE);card.addView(selectedMark,new LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)));
            card.setOnClickListener(v->{if(match==null)return;if(selectedId.equals(match.id))host.openMatch(match);else host.selectMatch(match);});
            root.addView(card);
        }
        void bind(Match value){
            match=value;
            boolean active=value.id.equals(selectedId);
            time.setText(value.timeConfirmed?value.time():"待定");
            state.setText(Rules.stateLabel(value.state));state.setTextColor(value.live()?ui.primary:ui.muted);
            title.setText(value.displayTitle());
            provenance.setText(value.source+" · "+updated(value));
            selected(card,active);
            selectedMark.setVisibility(active?View.VISIBLE:View.INVISIBLE);
            root.setContentDescription(value.displayTitle()+"，"+state.getText()+"，"+provenance.getText()+(active?"，已选中":""));
        }
    }

    private final class MatchHolder {
        final LinearLayout root,card,meta,footer;final FrameLayout selectedMark;
        final TextView time,state,league,home,away,homeScore,awayScore,provenance;
        final Logos.Crest homeCrest,awayCrest;
        final FrameLayout homeSlot,awaySlot,calendar;
        final Ui.WatchPill watch;
        final Ui.Icon calendarIcon;
        Match match;

        MatchHolder(){
            root=ui.column();root.setPadding(0,0,0,ui.dp(8));
            card=ui.column();card.setPadding(ui.dp(14),ui.dp(10),ui.dp(10),ui.dp(8));
            ui.clickable(card,ui.surface,16);card.setFocusable(true);
            card.setOnClickListener(v->{if(match==null)return;if(selectedId.equals(match.id))host.openMatch(match);else host.selectMatch(match);});

            meta=ui.row();
            LinearLayout clock=ui.column();time=ui.text("",15,ui.text,true);state=ui.text("",10,ui.muted,false);
            clock.addView(time);clock.addView(state);meta.addView(clock,new LinearLayout.LayoutParams(ui.dp(62),-2));
            selectedMark=new FrameLayout(ui.c);selectedMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            selectedMark.addView(new Ui.Icon(ui.c,"check",ui.text),new FrameLayout.LayoutParams(ui.dp(13),ui.dp(13),Gravity.CENTER));
            selectedMark.setVisibility(View.INVISIBLE);
            meta.addView(selectedMark,new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18)));
            league=ui.text("",11,ui.muted,true);meta.addView(league,new LinearLayout.LayoutParams(0,-2,1));
            calendar=new FrameLayout(ui.c);
            calendarIcon=new Ui.Icon(ui.c,"calendar-add",ui.muted);
            calendar.addView(calendarIcon,new FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER));
            ui.clickable(calendar,Color.TRANSPARENT,24);calendar.setFocusable(true);
            card.addView(meta);

            LinearLayout homeRow=ui.row();
            homeCrest=Logos.crest(ui,24,false);homeSlot=new FrameLayout(ui.c);
            homeSlot.addView(homeCrest.box,new FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER));
            ui.clickable(homeSlot,Color.TRANSPARENT,24);
            homeRow.addView(homeSlot,new LinearLayout.LayoutParams(ui.dp(36),ui.dp(34)));
            home=ui.text("",15,ui.text,true);homeRow.addView(home,new LinearLayout.LayoutParams(0,-2,1));
            homeScore=ui.text("—",21,ui.text,true);homeScore.setGravity(Gravity.END);homeScore.setMinWidth(ui.dp(34));
            homeRow.addView(homeScore,new LinearLayout.LayoutParams(-2,-2));
            card.addView(homeRow);

            LinearLayout awayRow=ui.row();
            awayCrest=Logos.crest(ui,24,false);awaySlot=new FrameLayout(ui.c);
            awaySlot.addView(awayCrest.box,new FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER));
            ui.clickable(awaySlot,Color.TRANSPARENT,24);
            awayRow.addView(awaySlot,new LinearLayout.LayoutParams(ui.dp(36),ui.dp(34)));
            away=ui.text("",15,ui.text,true);awayRow.addView(away,new LinearLayout.LayoutParams(0,-2,1));
            awayScore=ui.text("—",21,ui.text,true);awayScore.setGravity(Gravity.END);awayScore.setMinWidth(ui.dp(34));
            awayRow.addView(awayScore,new LinearLayout.LayoutParams(-2,-2));
            card.addView(awayRow);

            ui.gap(card,4);footer=ui.row();
            provenance=ui.text("",10,ui.muted,false);provenance.setPadding(ui.dp(2),0,ui.dp(2),0);
            footer.addView(provenance,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams calendarParams=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));calendarParams.setMarginStart(ui.dp(8));
            footer.addView(calendar,calendarParams);
            watch=ui.watchPill(48);
            LinearLayout.LayoutParams watchParams=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));watchParams.setMarginStart(ui.dp(8));
            footer.addView(watch,watchParams);watch.setVisibility(View.GONE);card.addView(footer);
            root.addView(card);
        }

        void bind(Match value){
            match=value;
            boolean active=value.id.equals(selectedId);
            Competition competition=Competition.find(value.league);
            time.setText(value.timeConfirmed?value.time():"待定");
            state.setText(value.live()&&!value.status.isBlank()?value.status:Rules.stateLabel(value.state));
            state.setTextColor(value.live()?ui.primary:value.cancelled()?ui.error:ui.muted);
            league.setText(competition==null?value.group:competition.name);
            home.setText(value.home);away.setText(value.away);
            homeScore.setText(value.hasScore()?value.homeScore:"—");awayScore.setText(value.hasScore()?value.awayScore:"—");
            homeScore.setTextColor(ui.scoreColor(value,true));awayScore.setTextColor(ui.scoreColor(value,false));
            provenance.setText(value.source+" · "+updated(value));
            selected(card,active);
            selectedMark.setVisibility(active?View.VISIBLE:View.INVISIBLE);
            bindTeam(homeSlot,value,true);bindTeam(awaySlot,value,false);
            WatchLinks.Link link=host.watchLink(value);
            watch.bind(link,value.live(),()->host.openWatch(value));
            bindCalendar(value,active);
            card.setContentDescription(value.displayTitle()+"，"+state.getText()+"，"+provenance.getText()+(active?"，已选中":""));
        }

        private void bindTeam(FrameLayout slot,Match m,boolean home){
            String id=home?m.homeId:m.awayId,name=home?m.home:m.away;
            Logos.Crest crest=home?homeCrest:awayCrest;
            Logos.bind(crest,home?m.homeLogo:m.awayLogo,name);
            boolean available=id!=null&&!id.isBlank();
            slot.setClickable(available);slot.setFocusable(available);
            slot.setContentDescription(available?"查看 "+name+" 的赛程":null);
            slot.setOnClickListener(available?v->host.openTeam(m,home):null);
        }

        private void bindCalendar(Match m,boolean selected){
            boolean subscribed=host.isSubscribed(m.id);
            boolean canSubscribe=m.scheduled()&&m.timeConfirmed&&m.start>System.currentTimeMillis();
            calendar.setSelected(subscribed);calendar.setAlpha(subscribed?1f:Ui.ACTION_ICON_ALPHA);
            calendarIcon.set(subscribed?"calendar-check":"calendar-add",subscribed?ui.primary:ui.muted);
            calendar.setContentDescription(subscribed?"取消订阅 "+m.displayTitle()+"，提前 5 分钟提醒":canSubscribe?"订阅 "+m.displayTitle()+"，提前 5 分钟提醒":m.displayTitle()+" 已开始或已结束，暂不可订阅");
            calendar.setOnClickListener(v->host.toggleSubscription(m));
        }
    }

    private void selected(LinearLayout card,boolean active){
        if(card.getTag() instanceof Boolean&&((Boolean)card.getTag())==active)return;
        card.setTag(active);
        ui.clickable(card,active?ui.tonal:ui.surface,16);
        card.setSelected(active);
    }

    private static String updated(Match m){
        return Formats.updated(m.updated);
    }
}
