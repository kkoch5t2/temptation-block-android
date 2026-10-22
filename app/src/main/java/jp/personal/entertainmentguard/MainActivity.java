package jp.personal.entertainmentguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ResolveInfo;
import android.widget.EditText;
import android.text.InputType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Map;
import java.util.TreeMap;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class MainActivity extends GuardActivity {
  private LinearLayout layout;
  private SharedPreferences prefs;
  private String page="home";
  private int d(float dp){return (int)(getResources().getDisplayMetrics().density*dp);}
  /** App-level settings lock only; not Android Device Owner or uninstall protection. */
  private boolean settingsLocked(){return prefs!=null && prefs.getBoolean("settings_locked",false);}
  private void showLockedNotice(){
    new AlertDialog.Builder(this)
      .setTitle("設定は固定されています")
      .setMessage("このアプリ内では、制限対象や時間設定を変更できません。Android自体の設定、データ消去、アンインストールを禁止する機能ではありません。")
      .setPositiveButton("OK",null).show();
  }
  private void confirmPermanentLock(){
    if(settingsLocked()){showLockedNotice();return;}
    new AlertDialog.Builder(this)
      .setTitle("現在の設定を固定しますか？")
      .setMessage("固定後は、このアプリ内でブロック対象・時間制限・サイト登録を変更したり、解除したりできません。設定を確認してから続けてください。\n\nAndroidのアンインストール・データ削除・ユーザー補助の無効化を防ぐものではありません。")
      .setNegativeButton("キャンセル",null)
      .setPositiveButton("確認画面へ",(dialog,which)->{
        if(settingsLocked()){showLockedNotice();return;}
        new AlertDialog.Builder(this)
          .setTitle("最終確認：設定を固定")
          .setMessage("アプリ内に固定を解除するボタンは用意しません。制限を変えられなくなることを理解した上で固定しますか？")
          .setNegativeButton("やめる",null)
          .setPositiveButton("設定を固定する",(d2,w2)->{
            if(settingsLocked()){showLockedNotice();return;}
            prefs.edit().putBoolean("settings_locked",true).commit();
            render();
          }).show();
      }).show();
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    prefs=getSharedPreferences("guard",MODE_PRIVATE);
    GroupRules.ensure(prefs);
    page=b!=null?b.getString("page","home"):getIntent().getStringExtra("page");
    if(!"settings".equals(page)&&!"strict".equals(page)&&!"newapps".equals(page))page="home";
    render();
  }
  @Override protected void onResume(){super.onResume();render();}
  @Override protected void onStrictModeEnded(){render();}
  @Override protected void onSaveInstanceState(Bundle state){
    super.onSaveInstanceState(state);state.putString("page",page);
  }
  @Override protected void onNewIntent(Intent intent){
    super.onNewIntent(intent);setIntent(intent);showPage(intent.getStringExtra("page"));
  }
  void showPage(String value){
    String target="settings".equals(value)||"strict".equals(value)||"newapps".equals(value)?value:"home";
    resetScrollFor("main:"+target);
    page=target;render();
  }
  @Override public void onBackPressed(){
    if(!"home".equals(page))showPage("home");else super.onBackPressed();
  }
  private TextView addText(String s,int sp,int color){
    TextView t=new TextView(this);
    t.setText(s);t.setTextSize(sp);t.setTextColor(ui.readable(color));
    t.setPadding(d(0),d(10),d(0),d(10));
    layout.addView(t);
    return t;
  }
  private Button addButton(String label,Runnable action){
    Button b=new Button(this);b.setText(label);
    b.setOnClickListener(v->action.run());
    layout.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    return b;
  }
  private boolean serviceEnabled(){
    String enabled=Settings.Secure.getString(getContentResolver(),
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return enabled!=null&&enabled.contains(getPackageName()+".GuardianService");
  }
  private void chooseApps(){
    if(settingsLocked()){showLockedNotice();return;}
    Intent query=new Intent(Intent.ACTION_MAIN);
    query.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> candidates=getPackageManager().queryIntentActivities(query,0);
    Map<String,String> options=new TreeMap<>();
    for(ResolveInfo info:candidates){
      if(info.activityInfo==null)continue;
      String pkg=info.activityInfo.packageName;
      if(pkg.equals(getPackageName())||pkg.equals("com.android.settings")
        ||pkg.equals("com.miui.home")||pkg.equals("com.google.android.dialer"))continue;
      CharSequence title=info.loadLabel(getPackageManager());
      String name=(title==null?pkg:title.toString());
      options.put(name+" ["+pkg+"]",pkg);
    }
    List<String> titles=new ArrayList<>(options.keySet());
    List<String> packages=new ArrayList<>();
    boolean[] chosen=new boolean[titles.size()];
    for(int i=0;i<titles.size();i++){
      String pkg=options.get(titles.get(i));
      packages.add(pkg);
      chosen[i]=RestrictionTargets.app(prefs,pkg);
    }
    new AlertDialog.Builder(this).setTitle("ブロックするアプリを選択")
      .setMultiChoiceItems(titles.toArray(new String[0]),chosen,
        (dialog,which,selected)->chosen[which]=selected)
      .setPositiveButton("保存",(dialog,which)->{
        if(settingsLocked()){showLockedNotice();return;}
        SharedPreferences.Editor edit=prefs.edit();
        for(int i=0;i<packages.size();i++)
          edit.putBoolean("app:"+packages.get(i),chosen[i]);
        edit.apply();
        render();
      })
      .setNegativeButton("キャンセル",null).show();
  }
  private void addDomainDialog(){
    if(settingsLocked()){showLockedNotice();return;}
    EditText field=new EditText(this);
    field.setSingleLine(true);
    field.setHint("example.com");
    int pad=d(18);
    field.setPadding(pad,pad,pad,pad);
    new AlertDialog.Builder(this)
      .setTitle("遮断するサイトを追加")
      .setView(field)
      .setPositiveButton("追加",(dialog,which)->{
        if(settingsLocked()){showLockedNotice();return;}
        String domain=Classifier.host(field.getText().toString());
        if(domain==null){
          new AlertDialog.Builder(this).setMessage("有効なサイト名を入力してください")
            .setPositiveButton("OK",null).show();
          return;
        }
        if(GroupRules.assignDomain(prefs,GroupRules.DEFAULT,domain))render();
        else new AlertDialog.Builder(this)
          .setMessage("厳格モード中は、このサイトを制限の弱いグループへ移せません。")
          .setPositiveButton("OK",null).show();
      })
      .setNegativeButton("キャンセル",null).show();
  }

  /** Accept lists copied from another blocker without touching its private app data. */
  private void importDomainsDialog(){
    if(settingsLocked()){showLockedNotice();return;}
    EditText field=new EditText(this);
    field.setHint("youtube.com\nexample.org\nhttps://example.net/path");
    field.setInputType(InputType.TYPE_CLASS_TEXT |
        InputType.TYPE_TEXT_FLAG_MULTI_LINE |
        InputType.TYPE_TEXT_VARIATION_URI);
    field.setMinLines(6);
    field.setMaxLines(12);
    int pad=d(16);
    field.setPadding(pad,pad,pad,pad);
    new AlertDialog.Builder(this).setTitle("禁止サイトをまとめて登録")
      .setMessage("AppBlockなどの一覧からURL・ドメインをコピーし、1行に1件ずつ貼り付けてください。空白・カンマ区切りにも対応します。")
      .setView(field)
      .setPositiveButton("登録する",(dialog,which)->{
        if(settingsLocked()){showLockedNotice();return;}
        String raw=field.getText().toString();
        if(raw.length()>40000){
          new AlertDialog.Builder(this).setMessage("一度に登録できる文字数を超えています")
            .setPositiveButton("OK",null).show();return;
        }
        HashSet<String> uniques=new HashSet<>();
        int invalid=0;
        for(String token:raw.split("[,;\\s]+")){
          if(token.isEmpty())continue;
          String host=Classifier.host(token);
          if(host!=null)uniques.add(host);
          else invalid++;
        }
        int added=0,rejected=0;
        for(String host:uniques){
          if(GroupRules.assignDomain(prefs,GroupRules.DEFAULT,host))added++;
          else rejected++;
        }
        render();
        new AlertDialog.Builder(this).setTitle("取り込み結果")
          .setMessage(added+"件を登録しました。"+
            (invalid>0?" 読み取れなかった項目："+invalid+"件":"")+
            (rejected>0?" 厳格モードによる拒否："+rejected+"件":""))
          .setPositiveButton("OK",null).show();
      })
      .setNegativeButton("キャンセル",null).show();
  }

  private void chooseCooldownSeconds(){
    if(CooldownSettings.locked(prefs))return;
    int[] choices=CooldownPolicy.secondOptions();
    String[] labels=new String[choices.length];
    int current=CooldownSettings.seconds(prefs),selected=0;
    for(int i=0;i<choices.length;i++){
      labels[i]=CooldownPolicy.label(choices[i]);
      if(choices[i]==current)selected=i;
    }
    new AlertDialog.Builder(this).setTitle("ブロック時の使用禁止時間")
      .setSingleChoiceItems(labels,selected,(dialog,which)->{
        boolean saved=CooldownSettings.setSeconds(prefs,choices[which]);
        dialog.dismiss();render();
        if(!saved)new AlertDialog.Builder(this)
          .setMessage("厳格モード・設定固定中は使用禁止時間を変更できません。")
          .setPositiveButton("OK",null).show();
      }).setNegativeButton("キャンセル",null).show();
  }

  private void chooseRestrictionMode(){
    if(settingsLocked()){showLockedNotice();return;}
    String[] options={
      CooldownSettings.seconds(prefs)+"秒間、画面全体で使用禁止",
      "1日の設定時間を使い切ったら"+CooldownSettings.seconds(prefs)+"秒間、画面全体で使用禁止",
      "すぐホームへ戻す（警告のみ）"
    };
    int selected=prefs.getInt("restriction_mode",1);
    if(selected<0||selected>2)selected=0;
    new AlertDialog.Builder(this)
      .setTitle("誘惑を開こうとしたときの制限")
      .setSingleChoiceItems(options,selected,(dialog,which)->{
        if(settingsLocked()){dialog.dismiss();showLockedNotice();return;}
        prefs.edit().putInt("restriction_mode",which).apply();
        dialog.dismiss();
        if(which==1)chooseDailyMinutes();
        else render();
      })
      .setNegativeButton("キャンセル",null).show();
  }
  private void chooseDailyMinutes(){
    if(settingsLocked()){showLockedNotice();return;}
    final int[] choices=UsageLimit.MINUTE_OPTIONS;
    String[] labels=new String[choices.length];
    int current=prefs.getInt("limit_minutes",10);
    int selected=0;
    for(int i=0;i<choices.length;i++){
      labels[i]="1日合計 "+choices[i]+" 分まで";
      if(choices[i]==current)selected=i;
    }
    new AlertDialog.Builder(this)
      .setTitle("1日の娯楽利用時間")
      .setSingleChoiceItems(labels,selected,(dialog,which)->{
        if(settingsLocked()){dialog.dismiss();showLockedNotice();return;}
        // Keep today's usage when changing allowance; prevent reset-based bypass.
        prefs.edit().putInt("limit_minutes",choices[which]).apply();
        dialog.dismiss();
        render();
      })
      .setNegativeButton("キャンセル",null).show();
  }
  private android.graphics.drawable.GradientDrawable shape(int color){
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(color);bg.setCornerRadius(d(18));return bg;
  }
  private LinearLayout card(int color){
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(d(18),d(16),d(18),d(16));
    c.setBackground(shape(color));c.setElevation(d(2));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
    p.setMargins(0,0,0,d(12));layout.addView(c,p);return c;
  }
  private TextView label(LinearLayout parent,String value,int size,int color,boolean bold){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(ui.readable(color));
    if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
    parent.addView(t);return t;
  }
  private void action(LinearLayout parent,String value,Runnable task){
    Button b=new Button(this);b.setText(value);styleButton(b,true);
    b.setOnClickListener(v->task.run());
    parent.addView(b,actionParams());
  }
  private void render(){
    layout=createScreen("main:"+page,"settings".equals(page)?"設定":
      "strict".equals(page)?"厳格モード":"newapps".equals(page)?"新しく入れたアプリ":getString(R.string.app_name),
      !"home".equals(page),page);
    if("settings".equals(page)){renderSettings();return;}
    if("strict".equals(page)){renderStrict();return;}
    if("newapps".equals(page)){renderNewApps();return;}
    int navy=ui.text,muted=ui.muted;
    boolean enabled=serviceEnabled(),locked=settingsLocked();
    LinearLayout hero=card(Color.rgb(26,47,76));
    label(hero,"🎯 今日の集中を守る",23,Color.WHITE,true);
    TextView status=label(hero,enabled?"🟢 監視中":"🔴 監視が停止しています",
      17,enabled?Color.rgb(121,230,184):Color.rgb(255,187,133),true);
    status.setPadding(0,d(14),0,d(8));
    TextView restrictionStatus=label(hero,locked?"🔒 設定固定中":"グループ別に制限を設定",
      14,Color.rgb(199,218,237),false);
    if(!enabled)action(hero,"監視の設定を開く",()->
      startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    LinearLayout summary=card(ui.surface);
    int active=0;for(String id:GroupRules.ids(prefs))if(GroupRules.enabled(prefs,id))active++;
    label(summary,"📊 制限のサマリ",18,ui.text,true);
    TextView groups=label(summary,"有効なグループ  "+active+" / "+GroupRules.ids(prefs).size(),16,ui.text,true);
    groups.setPadding(0,d(12),0,d(8));
    action(summary,"グループ・残り時間を確認",()->navigate("groups"));

    boolean strict=GroupRules.strict(prefs);
    LinearLayout strictCard=card(ui.surface);
    label(strictCard,"🔒 厳格モード",18,ui.text,true);
    TextView strictState=label(strictCard,strict?"オン · 制限の緩和を防止中":"オフ",16,
      strict?ui.success:ui.muted,true);
    strictState.setPadding(0,d(10),0,d(6));
    if(strict)bindStrictCountdown(label(strictCard,"",14,ui.muted,false));
    if(strict)label(strictCard,"制限対象の小窓再生（PiP）は禁止",14,ui.muted,false);
    action(strictCard,"厳格モードを確認",()->showPage("strict"));

    boolean newAppsBlocked=NewAppRules.enabled(prefs);
    LinearLayout appsCard=card(ui.surface);
    label(appsCard,"🛡️ 新規アプリ",18,ui.text,true);
    TextView appsState=label(appsCard,newAppsBlocked?"新しく入れたアプリは使用禁止":"使用制限はオフ",16,
      newAppsBlocked?ui.success:ui.muted,true);
    appsState.setPadding(0,d(10),0,d(6));
    label(appsCard,"インストール・削除はできます。",14,ui.muted,false);
    action(appsCard,"新規アプリの制限を確認",()->showPage("newapps"));
  }

  private void renderStrict(){
    boolean active=GroupRules.strict(prefs);
    LinearLayout status=card(ui.surface);
    label(status,active?"🔒 厳格モードはオン":"🔓 厳格モードはオフ",20,
      active?ui.success:ui.text,true);
    TextView description=label(status,active?"決めた期間、制限を緩める変更を防いでいます。":
      "期間を決めて、制限設定を守るための機能です。",14,ui.muted,false);
    description.setPadding(0,d(12),0,d(16));description.setLineSpacing(d(4),1);
    TextView pip=label(status,active?"🪟 制限対象の小窓再生（PiP）は禁止中":
      "🪟 厳格モード中は制限対象のPiPを禁止",15,active?ui.danger:ui.muted,true);
    pip.setPadding(0,0,0,d(8));
    TextView pipHelp=label(status,
      "指定したアプリのPiPを検知すると再生停止を送り、小窓を閉じます。未指定アプリ、通常の全画面表示や分割画面は対象外です。",
      14,ui.muted,false);
    pipHelp.setPadding(0,0,0,d(16));pipHelp.setLineSpacing(d(3),1);
    if(active){
      long remaining=GroupRules.strictRemainingMs(prefs);
      label(status,remaining<0?"解除期限の確認":"自動解除まで",14,ui.muted,true);
      TextView countdown=label(status,"",remaining<0?16:28,ui.text,true);
      countdown.setPadding(0,d(8),0,d(18));bindStrictCountdown(countdown,true);
      long duration=prefs.getLong("strict_duration_elapsed",0);
      if(remaining>=0&&duration>0)bindStrictRemainingBar(status,duration);
      long until=GroupRules.strictUntil(prefs);
      if(until>0){
        label(status,"解除予定："+
          new SimpleDateFormat("M月d日 HH:mm",Locale.JAPAN).format(new Date(until)),
          15,ui.text,false);
      }
      TextView note=label(status,"期限になると自動でオフになります。グループの制限設定は残ります。",
        14,ui.muted,false);
      note.setPadding(0,d(12),0,0);note.setLineSpacing(d(4),1);
    }else{
      TextView note=label(status,"時間・日数・年数から期間を選び、確認してから開始します。",
        14,ui.muted,false);note.setPadding(0,0,0,d(12));
      if(settingsLocked())label(status,"🔒 設定固定中のため開始できません",14,ui.muted,false);
      else action(status,"🔒 期間を選んで開始",()->startActivity(
        new Intent(this,GroupActivity.class).putExtra("strict_settings",true)));
    }
    LinearLayout allowed=card(ui.surface);
    label(allowed,"✅ 期間中にできること",18,ui.success,true);
    TextView yes=label(allowed,"禁止する対象を追加する\n利用時間を短くする\n制限を強くする",15,ui.text,false);
    yes.setPadding(0,d(12),0,0);yes.setLineSpacing(d(8),1);
    LinearLayout restricted=card(ui.surface);
    label(restricted,"🚫 期限までできないこと",18,ui.danger,true);
    TextView no=label(restricted,"厳格モードを途中で解除する\n利用時間を延ばす・制限を解除する\nグループを無効にする・削除する\nブロック時の使用禁止時間を変更する",
      15,ui.text,false);
    no.setPadding(0,d(12),0,0);no.setLineSpacing(d(8),1);
  }
  /** Dedicated tab keeps policy explanations and controls out of the home summary. */
  private void renderNewApps(){
    boolean blocked=NewAppRules.enabled(prefs);
    LinearLayout panel=card(ui.surface);
    label(panel,"🛡️ 新しく入れたアプリの使用",19,ui.text,true);
    TextView state=label(panel,blocked?"使用をブロック中":"新しく入れたアプリを使用できます",
      16,blocked?ui.success:ui.muted,true);
    state.setPadding(0,d(10),0,d(8));
    TextView description=label(panel,
      "アプリのインストール・削除はできます。オンにすると、新しく入れたアプリを開いても使用できず、ホームに戻ります。",
      14,ui.muted,false);
    description.setLineSpacing(d(3),1);description.setPadding(0,0,0,d(8));
    TextView details=label(panel,
      "今入っているアプリの通常更新は対象外です。削除して入れ直したアプリはブロックします。",
      14,ui.muted,false);
    details.setLineSpacing(d(3),1);details.setPadding(0,0,0,d(12));
    if(blocked&&(GroupRules.strict(prefs)||settingsLocked()))
      label(panel,settingsLocked()?"🔒 設定固定中のため解除できません":
        "🔒 厳格モード中は解除できません",13,ui.muted,false);
    else action(panel,blocked?"新しいアプリの使用を許可する":"新しいアプリの使用を禁止する",this::toggleNewApps);
  }
  private void toggleNewApps(){
    boolean active=NewAppRules.enabled(prefs);
    new AlertDialog.Builder(this)
      .setTitle(active?"新規アプリの自動ブロックをオフにしますか？":"新規アプリを自動ブロックしますか？")
      .setMessage(active?"新しく入れたアプリも使えるようになります。":
        "今入っているアプリを基準に、これから入れるアプリの使用を自動で禁止します。"+
        "厳格モード中はオフにできません。既存のグループ設定・利用時間は保持します。")
      .setNegativeButton("キャンセル",null)
      .setPositiveButton(active?"オフにする":"オンにする",(dialog,which)->{
        boolean ok=active?NewAppRules.disable(prefs):NewAppGuard.enable(this,prefs);
        render();
        if(!ok)new AlertDialog.Builder(this).setMessage(
          active?"厳格モード・設定固定中は解除できません。":"アプリ一覧を確認できなかったため有効化できませんでした。")
          .setPositiveButton("OK",null).show();
      }).show();
  }
  private void renderSettings(){
    LinearLayout appearance=card(ui.surface);
    label(appearance,"🎨 表示",19,ui.text,true);
    TextView explanation=label(appearance,"明るさや配色を選べます",13,ui.muted,false);
    explanation.setPadding(0,d(8),0,d(12));
    action(appearance,"表示テーマ  ·  "+themeLabel(),this::chooseTheme);
    LinearLayout cooldown=card(ui.surface);
    label(cooldown,"⏱ ブロック時の使用禁止時間",19,ui.text,true);
    TextView cooldownHelp=label(cooldown,
      "ブロック画面を表示する秒数です。「なし」は待機せずにブロックします。対象のアプリ・サイトは使えません。",
      13,ui.muted,false);
    cooldownHelp.setPadding(0,d(8),0,d(12));
    Button duration=new Button(this);
    duration.setText("使用禁止時間  ·  "+CooldownPolicy.label(CooldownSettings.seconds(prefs)));
    styleButton(duration,true);
    duration.setEnabled(!CooldownSettings.locked(prefs));
    duration.setOnClickListener(v->chooseCooldownSeconds());
    cooldown.addView(duration,actionParams());
    if(GroupRules.strict(prefs)){
      label(cooldown,"🔒 厳格モード中は変更できません",13,ui.muted,false);
      bindStrictCountdown(label(cooldown,"",13,ui.muted,false));
    }else if(settingsLocked())
      label(cooldown,"🔒 設定固定中は変更できません",13,ui.muted,false);
    LinearLayout rules=card(ui.surface);
    label(rules,"🛡️ 制限の管理",19,ui.text,true);
    action(rules,"グループ・時間・禁止対象",()->navigate("groups"));
    if(GroupRules.strict(prefs))bindStrictCountdown(label(rules,"",14,ui.danger,false));
    action(rules,"厳格モードを確認",()->showPage("strict"));
    if(!settingsLocked()){
      action(rules,"サイトを追加",()->startActivity(new Intent(this,GroupActivity.class).putExtra("edit_domains",GroupRules.DEFAULT)));
      action(rules,"サイトをまとめて登録",this::importDomainsDialog);
    }
    if(settingsLocked())label(rules,"🔒 制限設定は固定されています",13,ui.muted,false);
    LinearLayout monitoring=card(ui.surface);
    label(monitoring,"👀 監視",19,ui.text,true);
    TextView state=label(monitoring,serviceEnabled()?"🟢 監視中":"🔴 監視停止中",15,
      serviceEnabled()?ui.success:ui.danger,true);state.setPadding(0,d(12),0,d(10));
    TextView blocked=label(monitoring,"遮断回数（累計）："+prefs.getInt("blocked_total",0)+"回",14,ui.muted,false);
    blocked.setPadding(0,0,0,d(12));
    action(monitoring,"ユーザー補助の設定",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    action(monitoring,"状態を更新",this::render);
    LinearLayout help=card(ui.surface);label(help,"📖 使い方",19,ui.text,true);
    TextView detail=label(help,"• 下のナビからホーム・グループ・厳格・設定へ移動\n"+
      "• 戻るボタン、Androidの戻るジェスチャーに対応\n"+
      "• 右端から左へスワイプしても戻れます\n"+
      "• 登録一覧は検索できます",14,ui.muted,false);
    detail.setPadding(0,d(12),0,0);detail.setLineSpacing(d(5),1);
    addText("検索語や閲覧履歴は保存しません。表示テーマは制限設定とは別に保存します。",13,ui.muted);
  }
}
