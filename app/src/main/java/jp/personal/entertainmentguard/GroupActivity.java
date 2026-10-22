package jp.personal.entertainmentguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/** Manage mixed app/domain groups and their independent daily time allowance. */
public class GroupActivity extends GuardActivity {
  private SharedPreferences prefs;
  private LinearLayout layout;
  private String selectedGroup;
  private String groupQuery="",groupFilter="all";
  private final Map<String,LinearLayout> groupCards=new java.util.LinkedHashMap<>();
  private TextView groupCounter;
  private final Map<String,Button> filterButtons=new java.util.LinkedHashMap<>();
  private boolean locked(){return prefs.getBoolean("settings_locked",false);}
  private int d(int dp){return (int)(dp*getResources().getDisplayMetrics().density);}
  private android.graphics.drawable.GradientDrawable cardShape(){
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(ui.surface);bg.setCornerRadius(d(18));return bg;
  }
  private TextView text(LinearLayout target,String label,int size,int color){
    TextView t=new TextView(this);
    t.setText(label);t.setTextSize(size);t.setTextColor(ui.readable(color));
    t.setPadding(d(3),d(8),d(3),d(8));
    target.addView(t);
    return t;
  }
  private void action(LinearLayout target,String label,Runnable onClick){
    Button b=new Button(this);b.setText(label);
    styleButton(b,true);
    b.setOnClickListener(v->{
      if(locked()){notice("設定が固定されているため変更できません。");return;}
      onClick.run();
    });
    target.addView(b,actionParams());
  }
  private void notice(String msg){
    new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK",null).show();
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    prefs=getSharedPreferences("guard",MODE_PRIVATE);
    GroupRules.ensure(prefs);
    selectedGroup=state!=null?state.getString("selected_group"):getIntent().getStringExtra("group_id");
    if(state!=null){groupQuery=state.getString("group_query","");groupFilter=state.getString("group_filter","all");}
    render();
    if(state==null&&getIntent().getBooleanExtra("create_group",false)&&!locked())editGroup(null);
    if(state==null&&getIntent().getStringExtra("edit_domains")!=null&&!locked()){
      selectedGroup=getIntent().getStringExtra("edit_domains");render();manageDomains(selectedGroup);
    }
    if(state==null&&getIntent().getBooleanExtra("strict_settings",false)){
      if(GroupRules.strict(prefs))notice(strictStatus()+"\n\n期間中は制限を強くする変更のみ可能です。");
      else if(!locked())confirmStrictMode();
    }
  }
  @Override protected void onResume(){super.onResume();render();}
  @Override protected void onStrictModeEnded(){render();}
  @Override protected void onSaveInstanceState(Bundle state){
    super.onSaveInstanceState(state);state.putString("selected_group",selectedGroup);
    state.putString("group_query",groupQuery);state.putString("group_filter",groupFilter);
  }
  @Override public void onBackPressed(){
    if(selectedGroup!=null){selectedGroup=null;render();}else super.onBackPressed();
  }
  void showGroupList(){
    resetScrollFor("groups:list");selectedGroup=null;render();
  }
  private void openGroup(String id){selectedGroup=id;render();}
  private void memberButton(LinearLayout row,String id,String kind,int count){
    Button b=new Button(this);b.setAllCaps(false);b.setTextSize(13);
    b.setText((kind.equals("app")?"📱 アプリ":"🌐 サイト")+"\n"+count+"件");
    styleButton(b,true);
    b.setMinHeight(d(64));b.setOnClickListener(v->showMembers(id,kind));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
    lp.setMargins(d(4),0,d(4),0);row.addView(b,lp);
  }
  private void showMembers(String id,String kind){
    List<String> members=new ArrayList<>();
    String prefix="group."+kind+".";
    for(Map.Entry<String,?> e:new TreeMap<>(prefs.getAll()).entrySet()){
      if(!e.getKey().startsWith(prefix)||!id.equals(e.getValue()))continue;
      String value=e.getKey().substring(prefix.length());
      if(kind.equals("app")){
        try{
          CharSequence name=getPackageManager().getApplicationLabel(
            getPackageManager().getApplicationInfo(value,0));
          value=name.toString();
        }catch(android.content.pm.PackageManager.NameNotFoundException ignored){
          value+="（未インストール）";
        }
      }
      members.add(value);
    }
    String type=kind.equals("app")?"アプリ":"サイト";
    searchableMembers(GroupRules.name(prefs,id)+"："+type,members,locked()?null:()->{
      if(locked())return;
      if(kind.equals("app"))chooseApps(id);
      else if(kind.equals("domain"))manageDomains(id);
    });
  }
  private String today(){
    return new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());
  }
  private void applyGroupFilters(){
    String query=groupQuery.trim().toLowerCase(Locale.ROOT);int visible=0;
    for(Map.Entry<String,LinearLayout> entry:groupCards.entrySet()){
      String id=entry.getKey();boolean active=GroupRules.enabled(prefs,id);
      boolean match=GroupRules.name(prefs,id).toLowerCase(Locale.ROOT).contains(query)
        &&("all".equals(groupFilter)||"active".equals(groupFilter)&&active||"paused".equals(groupFilter)&&!active);
      entry.getValue().setVisibility(match?android.view.View.VISIBLE:android.view.View.GONE);
      if(match)visible++;
    }
    if(groupCounter!=null)groupCounter.setText(visible==0?"一致するグループはありません":visible+" グループ");
    for(Map.Entry<String,Button> entry:filterButtons.entrySet()){
      styleButton(entry.getValue(),entry.getKey().equals(groupFilter));
      entry.getValue().setSelected(entry.getKey().equals(groupFilter));
    }
  }
  private android.widget.Switch groupStateSwitch(String id,boolean active){
    android.widget.Switch control=new android.widget.Switch(this);
    boolean allowed=!locked()&&!(active&&GroupRules.strict(prefs));
    control.setText(active?"有効":"無効");control.setTextSize(14);
    control.setTextColor(allowed?ui.text:ui.muted);
    control.setChecked(active);control.setEnabled(allowed);
    control.setMinHeight(d(48));control.setSwitchPadding(d(10));control.setPadding(d(12),0,0,0);
    control.setThumbTintList(new android.content.res.ColorStateList(
      new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
      new int[]{ui.success,ui.muted}));
    control.setTrackTintList(new android.content.res.ColorStateList(
      new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
      new int[]{(ui.success&0x00ffffff)|0x55000000,ui.border}));
    String reason=locked()?"設定固定中":!allowed?"厳格モード中は無効化できません":"";
    control.setContentDescription("グループ "+(active?"有効":"無効")+(reason.isEmpty()?"":"、"+reason));
    if(!reason.isEmpty())control.setTooltipText(reason);
    final boolean[] restoring={false};
    control.setOnCheckedChangeListener((button,checked)->{
      if(restoring[0]||checked==active)return;
      restoring[0]=true;control.setChecked(active);restoring[0]=false;
      if(locked()){notice("設定が固定されているため変更できません。");return;}
      toggleGroup(id,checked);
    });
    return control;
  }
  private void render(){
    if(selectedGroup!=null&&!GroupRules.ids(prefs).contains(selectedGroup))selectedGroup=null;
    layout=createScreen("groups:"+(selectedGroup==null?"list":selectedGroup),
      selectedGroup==null?"グループ":groupTitle(selectedGroup),true,"groups");
    groupCards.clear();filterButtons.clear();groupCounter=null;
    if(locked())text(layout,"🔒 設定固定中 · 登録内容は確認できます",13,ui.muted);
    else if(GroupRules.strict(prefs)){
      bindStrictCountdown(text(layout,"",13,ui.danger));
    }
    if(selectedGroup==null){
      text(layout,"グループごとの制限を確認・編集",14,ui.muted);
      EditText query=searchBox(layout,"グループ名で検索");query.setText(groupQuery);
      LinearLayout filters=new LinearLayout(this);
      String[] ids={"all","active","paused"},labels={"すべて","有効","無効"};
      for(int i=0;i<ids.length;i++){
        final String filter=ids[i];Button b=new Button(this);b.setText(labels[i]);
        styleButton(b,filter.equals(groupFilter));b.setOnClickListener(v->{groupFilter=filter;applyGroupFilters();});
        LinearLayout.LayoutParams filterParams=new LinearLayout.LayoutParams(0,-2,1);
        filterParams.setMargins(d(3),0,d(3),0);filters.addView(b,filterParams);filterButtons.put(filter,b);
      }
      layout.addView(filters);
      LinearLayout listHeader=new LinearLayout(this);
      listHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams headerParams=new LinearLayout.LayoutParams(-1,-2);
      headerParams.setMargins(0,d(12),0,d(4));layout.addView(listHeader,headerParams);
      groupCounter=text(listHeader,"",13,ui.muted);
      groupCounter.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
      if(!locked()){
        Button create=new Button(this);create.setText("＋ 新しいグループ");styleButton(create,true);
        create.setOnClickListener(v->editGroup(null));
        LinearLayout.LayoutParams createParams=new LinearLayout.LayoutParams(-2,-2);
        createParams.setMargins(d(12),d(4),0,d(4));listHeader.addView(create,createParams);
      }
      query.addTextChangedListener(new android.text.TextWatcher(){
        public void beforeTextChanged(CharSequence value,int start,int count,int after){}
        public void onTextChanged(CharSequence value,int start,int before,int count){
          groupQuery=value.toString();applyGroupFilters();
        }
        public void afterTextChanged(android.text.Editable value){}
      });
    }
    for(String id:GroupRules.ids(prefs)){
      if(selectedGroup!=null&&!selectedGroup.equals(id))continue;
      LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(d(18),d(14),d(18),d(18));card.setBackground(cardShape());card.setElevation(d(1));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
      lp.setMargins(0,d(6),0,d(8));layout.addView(card,lp);
      String name=GroupRules.name(prefs,id);boolean deny=GroupRules.mode(prefs,id)==GroupRules.BLOCK;
      boolean active=GroupRules.enabled(prefs,id);long total=GroupRules.todayUsed(prefs,id,today());
      long budget=GroupRules.minutes(prefs,id)*60000L;
      LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
      card.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
      TextView nameText=text(titleRow,selectedGroup==null?groupTitle(id):"🛡️ 制限の状況",19,ui.text);
      nameText.setTypeface(null,android.graphics.Typeface.BOLD);
      nameText.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
      titleRow.addView(groupStateSwitch(id,active),new LinearLayout.LayoutParams(-2,-2));
      TextView mode=text(card,!active?"無効・制限なし":deny?"常時禁止":
        "1日合計 "+GroupRules.minutes(prefs,id)+"分",14,!active?ui.muted:deny?ui.danger:ui.success);
      addStatusIcon(mode,active,deny);
      if(active&&!deny){
        TextView remaining=text(card,"残り "+UsageLimit.formatRemaining(budget,total),23,ui.text);
        remaining.setTypeface(null,android.graphics.Typeface.BOLD);
        long left=Math.max(0,budget-Math.max(0,total));
        remainingBar(card,left,budget);
        text(card,"残り "+DurationFormat.remainingPercent(left,budget)+"%",12,ui.muted);
      }
      int[] counts=countMembers(id);
      if(selectedGroup==null){
        groupCards.put(id,card);
        text(card,"アプリ "+counts[0]+"件 · サイト "+counts[1]+"件",12,ui.muted);
        text(card,"詳細を見る ›",14,ui.accent);
        card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->openGroup(id));
        card.setForeground(new android.graphics.drawable.RippleDrawable(
          android.content.res.ColorStateList.valueOf(ui.border),null,rounded(android.graphics.Color.WHITE,18)));
      }else{
        TextView membersTitle=text(card,"📋 登録内容",16,ui.text);
        membersTitle.setPadding(d(3),d(20),d(3),d(10));
        LinearLayout members=new LinearLayout(this);LinearLayout.LayoutParams memberParams=new LinearLayout.LayoutParams(-1,-2);
        memberParams.setMargins(0,d(4),0,d(8));card.addView(members,memberParams);
        memberButton(members,id,"app",counts[0]);memberButton(members,id,"domain",counts[1]);
        if(GroupRules.DEFAULT.equals(id))
          text(card,"個別登録の件数です。標準の娯楽対象も含まれます。",12,ui.muted);
        if(!locked()){
          TextView editTitle=text(card,"✏️ 編集",16,ui.text);
          editTitle.setPadding(d(3),d(20),d(3),d(10));
          action(card,"グループ名",()->renameGroup(id));
          action(card,"制限方法 · "+(deny?"常時禁止":"時間制限"),()->choosePolicy(id,GroupRules.name(prefs,id)));
          if(!deny)action(card,"利用時間 · "+GroupRules.minutes(prefs,id)+"分",()->
            chooseMinutes(id,GroupRules.name(prefs,id)));
          if(!GroupRules.DEFAULT.equals(id)&&!GroupRules.strict(prefs)){
            Button remove=new Button(this);remove.setText("グループを削除");styleButton(remove,false);
            remove.setTextColor(ui.danger);remove.setOnClickListener(v->deleteGroup(id));
            LinearLayout.LayoutParams removeParams=actionParams();removeParams.topMargin=d(16);
            card.addView(remove,removeParams);
          }
        }
        Button emoji=new Button(this);emoji.setText("グループの絵文字");styleButton(emoji,false);
        emoji.setOnClickListener(v->chooseGroupEmoji(id,this::render));card.addView(emoji,actionParams());
      }
    }
    if(selectedGroup==null)applyGroupFilters();
  }
  private void showFieldError(TextView error,String message){
    error.setText(message);error.setVisibility(android.view.View.VISIBLE);
  }
  private void renameGroup(String id){
    if(locked())return;
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(d(20),d(8),d(20),d(8));
    EditText input=new EditText(this);input.setSingleLine(true);input.setText(GroupRules.name(prefs,id));
    input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
    panel.addView(input,new LinearLayout.LayoutParams(-1,-2));
    TextView error=text(panel,"",13,ui.danger);error.setVisibility(android.view.View.GONE);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("グループ名").setView(panel)
      .setPositiveButton("保存",null).setNegativeButton("キャンセル",null).create();
    dialog.setOnDismissListener(d->hideKeyboard());dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(locked())return;String value=input.getText().toString().trim();
      if(value.isEmpty()||value.length()>40){showFieldError(error,"1〜40文字で入力してください");return;}
      if(GroupRules.configure(prefs,id,value,GroupRules.mode(prefs,id),GroupRules.minutes(prefs,id))){
        dialog.dismiss();render();
      }else showFieldError(error,"この変更はできません");
    });
  }
  private void confirmStrictMode(){
    if(GroupRules.strict(prefs)||locked())return;
    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(d(20),d(10),d(20),d(14));
    text(panel,"厳格モードを続ける期間を設定してください。期限になると自動解除されます。再起動後は期限を確認できるまで解除が保留される場合があります。",
      15,Color.rgb(70,80,95));
    EditText count=new EditText(this);
    count.setHint("例：7");
    count.setText("7");
    count.setSelectAllOnFocus(true);
    count.setInputType(InputType.TYPE_CLASS_NUMBER);
    count.setSingleLine(true);
    panel.addView(count,new LinearLayout.LayoutParams(-1,-2));
    text(panel,"1〜999の数字を入力し、下の単位ボタンを押します。",
      13,Color.rgb(90,98,112));
    Button hours=policyButton(panel,"⏳ 時間で設定",Color.rgb(29,113,101));
    Button days=policyButton(panel,"📅 日数で設定",Color.rgb(41,86,151));
    Button years=policyButton(panel,"🗓️ 年数で設定",Color.rgb(112,69,144));
    text(panel,"期限までは制限の延長・解除・グループ無効化などができません。",
      13,Color.rgb(120,76,60));
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle("厳格モードの解除までの期間")
      .setView(panel)
      .setNegativeButton("キャンセル",null)
      .create();
    hours.setOnClickListener(v->chooseStrictTime(count,Calendar.HOUR_OF_DAY,"時間",dialog));
    days.setOnClickListener(v->chooseStrictTime(count,Calendar.DAY_OF_YEAR,"日",dialog));
    years.setOnClickListener(v->chooseStrictTime(count,Calendar.YEAR,"年",dialog));
    dialog.show();
  }
  private void chooseStrictTime(EditText entry,int field,String unit,AlertDialog dialog){
    int n;
    try{n=Integer.parseInt(entry.getText().toString().trim());}
    catch(NumberFormatException e){notice("期間は1〜999の数字を入力してください。");return;}
    if(n<1||n>999){notice("期間は1〜999の数字を入力してください。");return;}
    Calendar cal=Calendar.getInstance();
    cal.add(field,n);
    long until=cal.getTimeInMillis();
    // Fix the interval when the user chooses a unit; subsequent wall-clock edits
    // during confirmation cannot shorten the lock.
    long duration=until-System.currentTimeMillis();
    if(duration<=0){notice("期間を設定し直してください。");return;}
    String date=new SimpleDateFormat("yyyy年M月d日 HH:mm",Locale.JAPAN).format(cal.getTime());
    dialog.dismiss();
    new AlertDialog.Builder(this)
      .setTitle("厳格モードを"+n+unit+"有効にしますか？")
      .setMessage("解除予定："+date+"\n\nこの時刻までは時間の延長・完全禁止の解除・"+
        "グループの無効化や削除・禁止対象の削除などができなくなります。"+
        "期限を過ぎると自動的に厳格モードが解除され、制限設定はそのまま残ります。\n\n"+
        "Androidのアプリデータ消去・削除までは防げません。")
      .setNegativeButton("戻る",null)
      .setPositiveButton("次へ",(d,w)->
        new AlertDialog.Builder(this)
          .setTitle("最終確認：厳格モード")
          .setMessage(n+unit+"の間はアプリ内で厳格モードを解除できません。"+
            "解除予定は"+date+"です。")
          .setNegativeButton("キャンセル",null)
          .setPositiveButton("厳格モードをONにする",(d2,w2)->{
            if(!locked()&&!GroupRules.strict(prefs) &&
                GroupRules.enableStrictForDuration(prefs,duration,until,n+unit))navigate("strict");
            else notice("厳格モードを有効にできませんでした。端末の状態を確認し、もう一度試してください。");
          }).show()
      ).show();
  }
  private void toggleGroup(String id,boolean active){
    if(locked()){notice("設定が固定されているため変更できません。");return;}
    if(GroupRules.strict(prefs) && !active){
      notice("厳格モード中はグループを無効にできません。");return;
    }
    if(!active){
      new AlertDialog.Builder(this)
        .setTitle("グループを無効にしますか？")
        .setMessage("「"+GroupRules.name(prefs,id)+"」を無効にすると、登録済みのアプリ・サイトは制限されなくなります。")
        .setNegativeButton("キャンセル",null)
        .setPositiveButton("制限を無効にする",(dialog,which)->{
          if(locked()){notice("設定が固定されているため変更できません。");return;}
          if(GroupRules.setEnabled(prefs,id,false))render();
          else notice("この変更は禁止されています。");
        }).show();
      return;
    }
    if(GroupRules.setEnabled(prefs,id,true))render();
    else notice("この変更は禁止されています。");
  }
  private int[] countMembers(String id){
    int[] r={0,0};
    for(Map.Entry<String,?> e:prefs.getAll().entrySet()){
      if(e.getKey().startsWith("group.app.") && id.equals(e.getValue()))r[0]++;
      if(e.getKey().startsWith("group.domain.") && id.equals(e.getValue()))r[1]++;
    }
    return r;
  }
  private void actionSummary(LinearLayout card,String id){
    StringBuilder out=new StringBuilder();
    for(Map.Entry<String,?> e:new TreeMap<>(prefs.getAll()).entrySet()){
      if(id.equals(e.getValue())&&(e.getKey().startsWith("group.app.")||e.getKey().startsWith("group.domain."))){
        out.append(e.getKey().substring(e.getKey().indexOf('.',6)+1)).append("\n");
      }
    }
    if(out.length()>0)text(card,out.toString(),13,Color.GRAY);
  }
  private void editGroup(String id){
    if(locked())return;
    if(id!=null){renameGroup(id);return;}
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(d(20),d(8),d(20),d(8));
    EditText name=new EditText(this);name.setSingleLine(true);name.setHint("例：漫画・動画");
    name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
    panel.addView(name,new LinearLayout.LayoutParams(-1,-2));
    TextView error=text(panel,"",13,ui.danger);error.setVisibility(android.view.View.GONE);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("新しいグループ")
      .setView(panel).setNegativeButton("キャンセル",null).setPositiveButton("次へ",null).create();
    dialog.setOnDismissListener(d->hideKeyboard());dialog.show();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(locked())return;String value=name.getText().toString().trim();
      if(value.isEmpty()){showFieldError(error,"グループ名を入力してください");return;}
      dialog.dismiss();choosePolicy(null,value);
    });
  }
  /** Regular, full-width buttons instead of radio-style single-choice rows. */
  private Button policyButton(LinearLayout panel,String title,int tint){
    Button b=new Button(this);
    b.setAllCaps(false);
    b.setText(title);
    b.setTextSize(18);
    b.setTextColor(Color.WHITE);
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tint));
    b.setMinHeight(d(64));
    b.setPadding(d(12),d(10),d(12),d(10));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
    params.setMargins(0,d(10),0,d(3));
    panel.addView(b,params);
    return b;
  }
  private void choosePolicy(String id,String label){
    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(d(20),d(8),d(20),d(12));
    text(panel,"このグループに入れたアプリとサイトの制限方法を選択してください。",
      15,Color.rgb(76,85,98));
    Button timed=policyButton(panel,"⏳ 時間を決めて制限する",
      Color.rgb(29,113,101));
    text(panel,"1日合計で使える時間を、次の画面で選びます。",
      14,Color.rgb(84,93,106));
    Button deny=policyButton(panel,"🚫 いつでも利用禁止にする",
      Color.rgb(155,60,55));
    text(panel,"このグループ内のアプリ・サイトを常にブロックします。",
      14,Color.rgb(84,93,106));
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle("「"+label+"」の制限方法")
      .setView(panel)
      .setNegativeButton("キャンセル",null)
      .create();
    timed.setOnClickListener(v->{
      dialog.dismiss();
      if(!locked())chooseMinutes(id,label);
    });
    deny.setOnClickListener(v->{
      dialog.dismiss();
      if(!locked())saveGroup(id,label,GroupRules.BLOCK,10);
    });
    if(id!=null&&GroupRules.strict(prefs)&&GroupRules.mode(prefs,id)==GroupRules.BLOCK){
      timed.setEnabled(false);timed.setAlpha(0.45f);
      text(panel,"🔒 厳格モード中は時間制限に戻せません",13,ui.muted);
    }
    dialog.show();
  }
  private void chooseMinutes(String id,String label){
    int[] all={1,3,5,10,15,20,30,45,60,90,120,180,240};
    List<Integer> choices=new ArrayList<>();
    boolean strict=GroupRules.strict(prefs)&&id!=null;
    for(int value:all)if(!strict||value<=GroupRules.minutes(prefs,id))choices.add(value);
    String[] options=new String[choices.size()];
    for(int i=0;i<choices.size();i++)options[i]="1日合計 "+choices.get(i)+" 分";
    new AlertDialog.Builder(this).setTitle(strict?"利用時間（短縮のみ）":"利用時間")
      .setItems(options,(dlg,which)->saveGroup(id,label,GroupRules.DAILY,choices.get(which)))
      .setNegativeButton("キャンセル",null).show();
  }
  private void saveGroup(String id,String name,int mode,int minutes){
    if(locked())return;
    boolean saved;
    if(id==null){
      String created=GroupRules.create(prefs,name,mode,minutes);saved=created!=null;
      if(saved)selectedGroup=created;
    }else{
      saved=GroupRules.configure(prefs,id,name,mode,minutes);
    }
    if(saved)render();
    else notice("厳格モード中は制限を緩める変更はできません。");
  }
  private void chooseApps(String id){
    Intent query=new Intent(Intent.ACTION_MAIN);
    query.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> installed=getPackageManager().queryIntentActivities(query,0);
    Map<String,String> choices=new TreeMap<>();
    for(ResolveInfo info:installed){
      if(info.activityInfo==null)continue;
      String pkg=info.activityInfo.packageName;
      if(pkg.equals(getPackageName())||pkg.equals("com.android.settings")
          ||pkg.equals("com.miui.home")||pkg.equals("com.google.android.dialer")
          ||pkg.equals("com.android.phone")||pkg.equals("com.google.android.apps.messaging"))
        continue;
      CharSequence l=info.loadLabel(getPackageManager());
      choices.put((l==null?pkg:l.toString())+" ["+pkg+"]",pkg);
    }
    List<String> labels=new ArrayList<>(choices.keySet());
    List<String> packages=new ArrayList<>();
    boolean[] selected=new boolean[labels.size()],original=new boolean[labels.size()];
    for(int i=0;i<labels.size();i++){
      String pkg=choices.get(labels.get(i));packages.add(pkg);
      boolean enabled=prefs.contains("app:"+pkg)?prefs.getBoolean("app:"+pkg,false)
        :AppRules.knownCategory(pkg)!=null;
      selected[i]=enabled && id.equals(GroupRules.appGroup(prefs,pkg));
      original[i]=selected[i];
    }
    searchableApps(GroupRules.name(prefs,id)+"：アプリを選択",labels,selected,()->{
      if(locked())return;
      int rejected=0;
      for(int i=0;i<selected.length;i++){
        if(selected[i]==original[i])continue;
        String pkg=packages.get(i);boolean ok;
        if(selected[i])ok=GroupRules.assignApp(prefs,id,pkg);
        else if(GroupRules.DEFAULT.equals(id))ok=GroupRules.disableApp(prefs,pkg);
        else ok=GroupRules.removeAppFromGroup(prefs,id,pkg);
        if(!ok)rejected++;
      }
      render();
      if(rejected>0)notice("厳格モードにより、制限を緩める"+rejected+"件の変更は拒否しました。");
    });
  }
  private void manageDomains(String id){
    if(locked())return;
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(d(20),d(8),d(20),d(8));
    text(panel,"URL・ドメインを1行1件で入力。既存のサイトはこのグループへ移動します。",14,ui.muted);
    EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_TEXT|
      InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    input.setHint("example.com\nhttps://example.org");input.setMinLines(3);
    panel.addView(input,new LinearLayout.LayoutParams(-1,-2));
    TextView error=text(panel,"",13,ui.danger);error.setVisibility(android.view.View.GONE);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(GroupRules.name(prefs,id)+"：サイト編集")
      .setView(panel).setPositiveButton("追加・移動",null)
      .setNeutralButton("登録を外す",(d,w)->removeDomainDialog(id)).setNegativeButton("キャンセル",null).create();
    dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(locked())return;String raw=input.getText().toString();
      if(raw.length()>40000){showFieldError(error,"一度に入力できる量を超えています");return;}
      java.util.Set<String> hosts=new java.util.LinkedHashSet<>();int skipped=0;
      for(String value:raw.split("[,;\\s]+")){
        if(value.isEmpty())continue;String host=Classifier.host(value);
        if(host==null)skipped++;else hosts.add(host);
      }
      if(hosts.isEmpty()){showFieldError(error,"有効なURL・ドメインを入力してください");return;}
      int added=0,rejected=0;
      for(String host:hosts){if(GroupRules.assignDomain(prefs,id,host))added++;else rejected++;}
      dialog.dismiss();render();
      notice(added+"件を登録・移動しました。"+(skipped>0?" 読み取り失敗："+skipped+"件":"")+
        (rejected>0?" 厳格モードによる拒否："+rejected+"件":""));
    });
  }
  private void removeDomainDialog(String id){
    List<String> names=new ArrayList<>();
    for(Map.Entry<String,?> e:new TreeMap<>(prefs.getAll()).entrySet()){
      if(e.getKey().startsWith("group.domain.")&&id.equals(e.getValue()))
        names.add(e.getKey().substring("group.domain.".length()));
    }
    if(names.isEmpty()){notice("このグループに個別登録されたサイトはありません。");return;}
    new AlertDialog.Builder(this).setTitle("グループから移動するサイト")
      .setItems(names.toArray(new String[0]),(dlg,which)->{
        String host=names.get(which);
        if(locked())return;
        new AlertDialog.Builder(this).setTitle("登録を外しますか？")
          .setMessage(host+"\n\n"+(GroupRules.DEFAULT.equals(id)?
            "個別のサイト登録を削除します。標準対象の制限は残ります。":
            "基本グループへ移動します。基本グループの制限が適用されます。"))
          .setNegativeButton("キャンセル",null).setPositiveButton("登録を外す",(d,w)->{
            if(locked())return;
            boolean ok=GroupRules.DEFAULT.equals(id)?GroupRules.removeDomain(prefs,host):
              GroupRules.removeDomainFromGroup(prefs,id,host);
            if(ok)render();else notice("厳格モード中はサイトの制限を解除できません。");
          }).show();
      }).setNegativeButton("キャンセル",null).show();
  }
  private void deleteGroup(String id){
    new AlertDialog.Builder(this).setTitle("「"+GroupRules.name(prefs,id)+"」を削除")
      .setMessage("所属していたサイト・アプリは基本グループに戻ります。利用履歴は削除されません。")
      .setPositiveButton("削除",(dlg,which)->{
        if(locked())return;
        if(GroupRules.delete(prefs,id))render();
        else notice("厳格モード中はグループを削除できません。");
      }).setNegativeButton("キャンセル",null).show();
  }
}
