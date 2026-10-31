package cn.myscore.app;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Recycles schedule rows; a refresh only rewrites visible text until the section shape changes. */
final class TeamScheduleAdapter extends BaseAdapter {
    interface Host { void openMatch(Match match); void toggleSubscription(Match match); boolean isSubscribed(String id); boolean isBusy(); }
    private final Ui ui;
    private final Host host;
    private final List<TeamScheduleRow> rows=new ArrayList<>();
    private ListView list;

    TeamScheduleAdapter(Ui ui,Host host){this.ui=ui;this.host=host;}
    void attach(ListView list){this.list=list;}
    void refreshSubscriptions(){notifyDataSetChanged();}

    boolean updateRows(List<TeamScheduleRow> next) {
        boolean same=next.size()==rows.size();
        if(same)for(int i=0;i<next.size();i++)if(!next.get(i).key.equals(rows.get(i).key)){same=false;break;}
        rows.clear();rows.addAll(next);
        if(!same||(list!=null&&list.isLayoutRequested())){notifyDataSetChanged();return true;}
        if(list==null)return false;
        int first=list.getFirstVisiblePosition();
        for(int i=0;i<list.getChildCount();i++){int position=first+i;if(position>=0&&position<rows.size())getView(position,list.getChildAt(i),list);}
        return false;
    }

    @Override public int getCount(){return rows.size();}
    @Override public TeamScheduleRow getItem(int position){return rows.get(position);}
    @Override public long getItemId(int position){return position;}
    @Override public boolean hasStableIds(){return false;}
    @Override public int getViewTypeCount(){return 3;}
    @Override public int getItemViewType(int position){return rows.get(position).type;}

    @Override public View getView(int position,View convertView,ViewGroup parent) {
        TeamScheduleRow row=rows.get(position);
        if(row.type==TeamScheduleRow.SECTION) {
            TextView text=(TextView)convertView;
            if(text==null) {
                text=ui.text("",14,ui.text,true);
                text.setPadding(ui.dp(4),ui.dp(22),0,ui.dp(12));
            }
            text.setText(row.title);
            text.setTextColor(row.title.startsWith("进行中")?ui.primary:ui.text);
            return text;
        }
        if(row.type==TeamScheduleRow.NOTE) {
            LinearLayout box=(LinearLayout)convertView;
            if(box==null) {
                box=ui.column();
                box.setBackground(ui.panel());
                box.setPadding(ui.dp(18),ui.dp(20),ui.dp(18),ui.dp(20));
                TextView title=ui.text("",18,ui.text,true);
                TextView message=ui.text("",14,ui.muted,false);
                message.setLineSpacing(ui.dp(5),1);
                message.setPadding(0,ui.dp(8),0,0);
                box.addView(title);box.addView(message);
                box.setTag(new NoteHolder(title,message));
            }
            NoteHolder holder=(NoteHolder)box.getTag();
            holder.title.setText(row.title);
            holder.message.setText(row.message);
            holder.message.setVisibility(row.message==null||row.message.isEmpty()?View.GONE:View.VISIBLE);
            return box;
        }
        Object tag=convertView==null?null:convertView.getTag();
        MatchHolder holder=tag instanceof MatchHolder?(MatchHolder)tag:new MatchHolder();
        holder.root.setTag(holder);
        holder.bind(row);
        return holder.root;
    }

    private void text(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}

    private static final class NoteHolder {
        final TextView title,message;
        NoteHolder(TextView title,TextView message){this.title=title;this.message=message;}
    }

    private final class MatchHolder {
        final LinearLayout root,card;
        final TextView date,state,opponent,detail,result;
        final Ui.ScoreText score;
        final Ui.MatchTime foot;
        final LinearLayout footer;
        final FrameLayout action;
        final Ui.Icon calendar;
        final Logos.Crest crest;
        TeamScheduleRow row;

        MatchHolder() {
            root=ui.column();root.setPadding(0,0,0,ui.dp(8));
            card=ui.column();card.setBackground(ui.panel());card.setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(10));
            ui.clickable(card,ui.surface,Ui.CARD_RADIUS);card.setFocusable(true);
            card.setOnClickListener(v->{if(row!=null&&row.match!=null)host.openMatch(row.match);});
            card.setMinimumHeight(ui.dp(72));
            LinearLayout top=ui.row();
            date=ui.text("",12,ui.muted,false);top.addView(date,new LinearLayout.LayoutParams(0,-2,1));
            state=ui.text("",12,ui.muted,true);state.setGravity(Gravity.END);state.setPadding(ui.dp(8),0,0,0);
            top.addView(state,new LinearLayout.LayoutParams(-2,-2));
            card.addView(top);ui.gap(card,10);
            LinearLayout main=ui.row();
            crest=Logos.crest(ui,24,false);main.addView(crest.box,new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24)));
            LinearLayout middle=ui.column();
            opponent=ui.text("",15,ui.text,true);middle.addView(opponent);
            detail=ui.text("",11,ui.muted,false);detail.setPadding(0,ui.dp(4),0,0);middle.addView(detail);
            LinearLayout.LayoutParams middleParams=new LinearLayout.LayoutParams(0,-2,1);
            middleParams.setMarginStart(ui.dp(10));main.addView(middle,middleParams);
            LinearLayout numbers=ui.column();numbers.setGravity(Gravity.CENTER);
            score=ui.score("","",false,20);numbers.addView(score,new LinearLayout.LayoutParams(-1,-2));
            result=ui.text("",11,ui.primary,true);result.setGravity(Gravity.CENTER);result.setPadding(0,ui.dp(2),0,0);numbers.addView(result);
            LinearLayout.LayoutParams numbersParams=new LinearLayout.LayoutParams(Math.max(ui.dp(96),ui.scoreColumnWidth(20)),-2);
            numbersParams.setMarginStart(ui.dp(10));main.addView(numbers,numbersParams);
            card.addView(main);
            ui.gap(card,12);
            footer=ui.column();
            LinearLayout controls=ui.row();
            foot=ui.new MatchTime();
            controls.addView(foot,new LinearLayout.LayoutParams(0,-2,1));
            action=new FrameLayout(ui.c);
            calendar=new Ui.Icon(ui.c,"calendar-add",ui.muted);
            action.addView(calendar,new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));
            action.setFocusable(true);ui.clickable(action,Color.TRANSPARENT,24);
            action.setOnClickListener(v->{if(row!=null)host.toggleSubscription(row.match);});
            LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(Math.max(ui.dp(96),ui.scoreColumnWidth(20)),ui.dp(48));
            actionParams.setMarginStart(ui.dp(8));controls.addView(action,actionParams);footer.addView(controls);card.addView(footer);
            root.addView(card,new LinearLayout.LayoutParams(-1,-2));
        }

        void bind(TeamScheduleRow value) {
            row=value;
            text(date,value.date);
            text(state,value.state);
            state.setTextColor(value.match.live()?ui.primary:value.match.cancelled()||value.match.state.equals("delayed")?ui.error:ui.muted);
            text(opponent,value.opponent);
            text(detail,value.side.isEmpty()?value.detail:value.side+" · "+value.detail);
            boolean scored=!value.score.isEmpty();
            String own=value.match.displayScore(value.home),other=value.match.displayScore(!value.home);
            score.setScore(own,other,scored);score.setResultColors(ui.scoreColor(value.match,value.home),ui.scoreColor(value.match,!value.home));
            boolean subscribed=host.isSubscribed(value.match.id);
            boolean eligible=value.match.scheduled()&&value.match.timeConfirmed&&value.match.start>System.currentTimeMillis();
            action.setVisibility(eligible?View.VISIBLE:View.GONE);
            action.setEnabled(!host.isBusy());action.setAlpha(host.isBusy()?0.5f:subscribed?1f:Ui.ACTION_ICON_ALPHA);
            foot.bind(value.time,value.match.source);
            calendar.set(subscribed?"calendar-check":"calendar-add",subscribed?ui.primary:ui.muted);
            action.setContentDescription((subscribed?"取消订阅":"订阅")+"对阵"+value.opponent+"的比赛");
            score.setVisibility(scored?View.VISIBLE:View.GONE);
            text(result,value.result);
            result.setVisibility(value.result.isEmpty()?View.GONE:View.VISIBLE);
            result.setTextColor("胜".equals(value.result)?ui.primary:"平".equals(value.result)?ui.muted:ui.error);
            Logos.bind(crest,value.home?value.match.awayLogo:value.match.homeLogo,value.opponent);
            card.setContentDescription(value.date+" "+value.time+"，"+(value.side.isEmpty()?"":value.side+"，")+"对阵"+value.opponent+"，"+value.state+(scored?"，比分 "+value.score:""));
        }
    }
}
