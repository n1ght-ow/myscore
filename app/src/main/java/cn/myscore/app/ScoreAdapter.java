package cn.myscore.app;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Recycles score cards instead of rebuilding the whole screen. Only the visible rows are bound,
 * and a row keeps its existing view tree when a source refresh changes a score or a status.
 */
final class ScoreAdapter extends BaseAdapter {
    interface Host {
        void openMatch(Match match);
        void toggleSubscription(Match match);
        void retryScores();
        void showSourceStatus();
        boolean isSubscribed(String id);
    }
    private final Ui ui;
    private final Host host;
    private List<ScoreRow> rows=new ArrayList<>();
    private ScoreList list;

    ScoreAdapter(Ui ui,Host host){this.ui=ui;this.host=host;}
    void attach(ScoreList list){this.list=list;}

    /**
     * Rebinds visible rows when the row identity and order are unchanged, so a score tick only
     * changes text. Returns true when the list had to be relaid out.
     */
    boolean updateRows(List<ScoreRow> next){
        boolean same=next.size()==rows.size();
        if(same)for(int i=0;i<next.size();i++)if(!next.get(i).key().equals(rows.get(i).key())){same=false;break;}
        rows=next;
        if(!same){notifyDataSetChanged();return true;}
        if(list==null)return false;
        int first=list.getFirstVisiblePosition();
        for(int i=0;i<list.getChildCount();i++){int position=first+i;if(position>=0&&position<rows.size())getView(position,list.getChildAt(i),list);}
        return false;
    }
    @Override public int getCount(){return rows.size();}
    @Override public ScoreRow getItem(int position){return rows.get(position);}
    @Override public long getItemId(int position){return position;}
    @Override public boolean hasStableIds(){return false;}
    @Override public int getViewTypeCount(){return 6;}
    @Override public int getItemViewType(int position){return rows.get(position).type;}

    @Override public View getView(int position,View convertView,ViewGroup parent) {
        ScoreRow row=rows.get(position);
        switch(row.type) {
            case ScoreRow.LOADING: {
                TextView text=(TextView)convertView;
                if(text==null){text=ui.text("",11,ui.muted,false);text.setPadding(ui.dp(4),ui.dp(9),0,ui.dp(2));}
                text.setText(row.text);return text;
            }
            case ScoreRow.SECTION: {
                TextView text=(TextView)convertView;
                if(text==null){text=ui.text("",14,ui.text,true);text.setPadding(ui.dp(4),ui.dp(18),0,ui.dp(10));}
                text.setText(row.text);return text;
            }
            case ScoreRow.FAILURE: {
                LinearLayout box=(LinearLayout)convertView;
                if(box==null){
                    box=ui.column();box.setPadding(0,0,0,ui.dp(16));
                    TextView banner=ui.text("",14,ui.error,true);banner.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));
                    ui.clickable(banner,ui.surface,12);banner.setFocusable(true);banner.setOnClickListener(v->host.showSourceStatus());
                    box.addView(banner);
                }
                ((TextView)box.getChildAt(0)).setText(row.text);
                return box;
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

    private TextView teamScore(String text){TextView score=ui.text(text,20,ui.text,true);score.setGravity(Gravity.END);score.setMinWidth(ui.dp(32));return score;}
    private String footnote(Match m){return m.timeConfirmed?m.time():"时间待定";}
    private String stateText(Match m){return Rules.stateLabel(m.state);}
    private void subscription(Ui.Icon icon,Match m,FrameLayout sub) {
        boolean isSubscribed=host.isSubscribed(m.id);
        boolean canSubscribe=m.scheduled()&&m.timeConfirmed&&m.start>System.currentTimeMillis();
        icon.set(isSubscribed?"calendar-check":"calendar-add",isSubscribed||canSubscribe?ui.primary:ui.muted);
        sub.setContentDescription(isSubscribed?"取消订阅 "+m.displayTitle()+"，提前 5 分钟提醒":canSubscribe?"订阅 "+m.displayTitle()+"，提前 5 分钟提醒":m.displayTitle()+" 已开始或已结束，暂不可订阅");
        sub.setOnClickListener(v->host.toggleSubscription(m));
    }

    private final class MatchHolder {
        final LinearLayout root,card,metaRow;
        final TextView league,state,home,homeScore,away,awayScore,foot,stale;
        final Logos.Crest homeCrest,awayCrest;
        final FrameLayout sub;
        final Ui.Icon subIcon;
        Match match;

        MatchHolder() {
            root=ui.column();root.setPadding(0,0,0,ui.dp(6));
            card=ui.column();card.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
            ui.clickable(card,ui.surface,12);card.setFocusable(true);
            card.setOnClickListener(v->{if(match!=null)host.openMatch(match);});
            metaRow=ui.row();
            league=ui.text("",12,ui.muted,true);metaRow.addView(league,new LinearLayout.LayoutParams(0,-2,1));
            state=ui.text("",11,ui.text,true);state.setGravity(Gravity.END);metaRow.addView(state,new LinearLayout.LayoutParams(-2,-2));
            card.addView(metaRow);ui.gap(card,9);

            LinearLayout homeRow=ui.row();
            homeCrest=Logos.crest(ui,25,false);homeRow.addView(homeCrest.box);
            home=ui.text("",15,ui.text,true);
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMarginStart(ui.dp(10));homeRow.addView(home,hp);
            homeScore=teamScore("—");
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMarginStart(ui.dp(12));homeRow.addView(homeScore,sp);
            card.addView(homeRow);ui.gap(card,5);

            LinearLayout awayRow=ui.row();
            awayCrest=Logos.crest(ui,25,false);awayRow.addView(awayCrest.box);
            away=ui.text("",15,ui.text,true);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.setMarginStart(ui.dp(10));awayRow.addView(away,ap);
            awayScore=teamScore("—");
            LinearLayout.LayoutParams asp=new LinearLayout.LayoutParams(-2,-2);asp.setMarginStart(ui.dp(12));awayRow.addView(awayScore,asp);
            card.addView(awayRow);ui.gap(card,8);

            LinearLayout footRow=ui.row();
            foot=ui.text("",12,ui.muted,false);footRow.addView(foot,new LinearLayout.LayoutParams(0,-2,1));
            sub=new FrameLayout(ui.c);
            subIcon=new Ui.Icon(ui.c,"calendar-add",ui.primary);
            sub.addView(subIcon,new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));
            ui.clickable(sub,Color.TRANSPARENT,24);sub.setFocusable(true);
            LinearLayout.LayoutParams subParams=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));subParams.setMarginStart(ui.dp(8));footRow.addView(sub,subParams);
            card.addView(footRow);
            stale=ui.text("上次记录为进行中，当前状态待刷新",12,ui.error,false);
            stale.setPadding(0,ui.dp(6),0,0);stale.setVisibility(View.GONE);card.addView(stale);
            root.addView(card);
        }

        void bind(Match m) {
            match=m;
            Competition comp=Competition.find(m.league);
            league.setText(comp==null?m.group:comp.name);
            boolean showState=m.live()||!m.status.equals(stateText(m));
            state.setVisibility(showState?View.VISIBLE:View.GONE);
            if(showState)state.setText(m.status);
            home.setText(m.home);
            away.setText(m.away);
            homeScore.setText(m.hasScore()?m.homeScore:"—");
            awayScore.setText(m.hasScore()?m.awayScore:"—");
            foot.setText(footnote(m));
            boolean staleNow=m.live()&&System.currentTimeMillis()-m.updated>120_000;
            stale.setVisibility(staleNow?View.VISIBLE:View.GONE);
            Logos.bind(homeCrest,m.homeLogo,m.home);
            Logos.bind(awayCrest,m.awayLogo,m.away);
            subscription(subIcon,m,sub);
        }
    }

    private final class F1Holder {
        final LinearLayout root,card;
        final TextView league,state,title,first,second,foot,stale;
        final FrameLayout sub;
        final Ui.Icon subIcon;
        Match match;

        F1Holder() {
            root=ui.column();root.setPadding(0,0,0,ui.dp(6));
            card=ui.column();card.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
            ui.clickable(card,ui.surface,12);card.setFocusable(true);
            card.setOnClickListener(v->{if(match!=null)host.openMatch(match);});
            LinearLayout metaRow=ui.row();
            league=ui.text("",12,ui.muted,true);metaRow.addView(league,new LinearLayout.LayoutParams(0,-2,1));
            state=ui.text("",11,ui.text,true);state.setGravity(Gravity.END);metaRow.addView(state,new LinearLayout.LayoutParams(-2,-2));
            card.addView(metaRow);ui.gap(card,9);
            title=ui.text("",17,ui.text,true);card.addView(title);
            first=ui.text("",12,ui.muted,false);first.setPadding(0,ui.dp(5),0,0);card.addView(first);
            second=ui.text("",12,ui.muted,false);second.setPadding(0,ui.dp(5),0,0);card.addView(second);
            ui.gap(card,8);
            LinearLayout footRow=ui.row();
            foot=ui.text("",12,ui.muted,false);footRow.addView(foot,new LinearLayout.LayoutParams(0,-2,1));
            sub=new FrameLayout(ui.c);
            subIcon=new Ui.Icon(ui.c,"calendar-add",ui.primary);
            sub.addView(subIcon,new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));
            ui.clickable(sub,Color.TRANSPARENT,24);sub.setFocusable(true);
            LinearLayout.LayoutParams subParams=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));subParams.setMarginStart(ui.dp(8));footRow.addView(sub,subParams);
            card.addView(footRow);
            stale=ui.text("上次记录为进行中，当前状态待刷新",12,ui.error,false);
            stale.setPadding(0,ui.dp(6),0,0);stale.setVisibility(View.GONE);card.addView(stale);
            root.addView(card);
        }

        void bind(Match m) {
            match=m;
            Competition comp=Competition.find(m.league);
            league.setText(comp==null?m.group:comp.name);
            boolean showState=m.live()||!m.status.equals(stateText(m));
            state.setVisibility(showState?View.VISIBLE:View.GONE);
            if(showState)state.setText(m.status);
            title.setText(m.displayTitle());
            first.setText(m.results.size()>0?m.results.get(0):"");first.setVisibility(m.results.size()>0?View.VISIBLE:View.GONE);
            second.setText(m.results.size()>1?m.results.get(1):"");second.setVisibility(m.results.size()>1?View.VISIBLE:View.GONE);
            foot.setText(footnote(m));
            boolean staleNow=m.live()&&System.currentTimeMillis()-m.updated>120_000;
            stale.setVisibility(staleNow?View.VISIBLE:View.GONE);
            subscription(subIcon,m,sub);
        }
    }

    private final class EmptyHolder {
        final LinearLayout root;
        final TextView title,message;
        EmptyHolder() {
            root=ui.column();root.setPadding(ui.dp(4),ui.dp(32),ui.dp(4),0);
            title=ui.text("",20,ui.text,true);root.addView(title);ui.gap(root,10);
            message=ui.text("",14,ui.muted,false);root.addView(message);ui.gap(root,20);
            TextView retry=ui.button("重新获取",false,host::retryScores);root.addView(retry,new LinearLayout.LayoutParams(-2,-2));
        }
    }
}
