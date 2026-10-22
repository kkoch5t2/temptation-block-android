package jp.personal.entertainmentguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared safe-area layout, navigation, appearance and searchable dialogs. */
public abstract class GuardActivity extends Activity {
  protected GuardTheme ui;
  private final android.os.Handler countdownHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private final Map<TextView,Boolean> strictCountdownLabels=new java.util.LinkedHashMap<>();
  private final List<Runnable> strictBarUpdates=new ArrayList<>();
  private final Runnable countdownTick=new Runnable(){
    @Override public void run(){
      if(!strictCountdownLabels.isEmpty()){
        if(!GroupRules.strict(getSharedPreferences("guard",MODE_PRIVATE))){
          strictCountdownLabels.clear();strictBarUpdates.clear();onStrictModeEnded();
        }else{
          String status=strictStatus();
          for(Map.Entry<TextView,Boolean> entry:strictCountdownLabels.entrySet()){
            String value=entry.getValue()?DurationFormat.remaining(GroupRules.strictRemainingMs(
              getSharedPreferences("guard",MODE_PRIVATE))):status;
            TextView label=entry.getKey();
            if(!value.contentEquals(label.getText()))label.setText(value);
          }
          for(Runnable update:strictBarUpdates)update.run();
        }
      }
      countdownHandler.postDelayed(this,1000);
    }
  };
  protected String strictStatus(){
    long remain=GroupRules.strictRemainingMs(getSharedPreferences("guard",MODE_PRIVATE));
    return "🔒 厳格モード中"+(remain<0?" · 期限確認待ち":
      " · 解除まで "+DurationFormat.remaining(remain));
  }
  protected void bindStrictCountdown(TextView label){
    bindStrictCountdown(label,false);
  }
  protected void bindStrictCountdown(TextView label,boolean durationOnly){
    long remain=GroupRules.strictRemainingMs(getSharedPreferences("guard",MODE_PRIVATE));
    boolean only=durationOnly&&remain>=0;
    label.setText(only?DurationFormat.remaining(remain):strictStatus());
    strictCountdownLabels.put(label,only);
  }
  protected void addStatusIcon(TextView label,boolean active,boolean blocked){
    label.setCompoundDrawablesRelativeWithIntrinsicBounds(
      !active?R.drawable.ic_status_pause:blocked?R.drawable.ic_status_block:R.drawable.ic_status_clock,0,0,0);
    label.setCompoundDrawablePadding(dp(6));
    label.setCompoundDrawableTintList(ColorStateList.valueOf(Color.parseColor(ui.dark?"#9E9E9E":"#707070")));
  }
  protected android.widget.ProgressBar remainingBar(LinearLayout parent,long remaining,long total){
    android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    bar.setProgressTintList(null);bar.setProgressBackgroundTintList(null);
    android.graphics.drawable.ClipDrawable fill=new android.graphics.drawable.ClipDrawable(
      rounded(Color.parseColor(ui.dark?"#469BFF":"#2674DC"),5),Gravity.LEFT,
      android.graphics.drawable.ClipDrawable.HORIZONTAL);
    android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(
      new android.graphics.drawable.Drawable[]{rounded(Color.parseColor("#080D14"),5),fill});
    layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);
    bar.setProgressDrawable(layers);bar.setMax(10000);
    updateRemainingBar(bar,remaining,total);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(10));
    params.setMargins(0,dp(12),0,dp(8));parent.addView(bar,params);return bar;
  }
  private void updateRemainingBar(android.widget.ProgressBar bar,long remaining,long total){
    int percent=DurationFormat.remainingPercent(remaining,total);
    int level=bar.getMax()==100?percent:
      (int)Math.round(DurationFormat.remainingFraction(remaining,total)*bar.getMax());
    if(bar.getProgress()!=level)bar.setProgress(level);
    String description="残り時間 "+percent+"%";
    if(!description.contentEquals(bar.getContentDescription()==null?"":bar.getContentDescription()))
      bar.setContentDescription(description);
  }
  protected void bindStrictRemainingBar(LinearLayout parent,long total){
    long remaining=GroupRules.strictRemainingMs(getSharedPreferences("guard",MODE_PRIVATE));
    android.widget.ProgressBar bar=remainingBar(parent,remaining,total);
    bar.setMax(100);
    TextView percentage=new TextView(this);percentage.setTextSize(12);percentage.setTextColor(ui.muted);
    percentage.setPadding(0,0,0,dp(16));parent.addView(percentage);
    Runnable update=()->{
      long left=GroupRules.strictRemainingMs(getSharedPreferences("guard",MODE_PRIVATE));
      if(left<0){bar.setVisibility(View.GONE);percentage.setVisibility(View.GONE);return;}
      bar.setVisibility(View.VISIBLE);percentage.setVisibility(View.VISIBLE);
      updateRemainingBar(bar,left,total);
      String label="設定した期間の残り "+DurationFormat.remainingPercent(left,total)+"%";
      if(!label.contentEquals(percentage.getText()))percentage.setText(label);
    };
    update.run();strictBarUpdates.add(update);
  }
  protected void onStrictModeEnded(){}

  private ScrollView scroller;
  private LinearLayout bottomNavigation;
  private String screenKey;
  private final Map<String,Integer> scrollPositions=new HashMap<>();
  private float gestureX,gestureY;
  private long gestureTime;
  private boolean edgeGesture;
  private android.window.OnBackInvokedCallback systemBack;
  protected int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
  @Override public void onCreate(Bundle state){
    ui=new GuardTheme(this);
    setTheme(ui.dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
    super.onCreate(state);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    if(state!=null){
      String key=state.getString("scroll_key");
      if(key!=null)scrollPositions.put(key,state.getInt("scroll_y"));
    }
    if(Build.VERSION.SDK_INT>=33){
      systemBack=()->onBackPressed();
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBack);
    }
  }
  @Override protected void onResume(){
    super.onResume();
    if(ui!=null&&ui.dark!=new GuardTheme(this).dark)recreate();
    countdownHandler.removeCallbacks(countdownTick);countdownHandler.post(countdownTick);
  }
  @Override protected void onPause(){
    countdownHandler.removeCallbacks(countdownTick);super.onPause();
  }
  @Override protected void onSaveInstanceState(Bundle state){
    super.onSaveInstanceState(state);
    state.putString("scroll_key",screenKey);
    state.putInt("scroll_y",scroller==null?0:scroller.getScrollY());
  }
  @Override protected void onDestroy(){
    countdownHandler.removeCallbacks(countdownTick);strictCountdownLabels.clear();strictBarUpdates.clear();
    if(Build.VERSION.SDK_INT>=33&&systemBack!=null)
      getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);
    super.onDestroy();
  }
  protected GradientDrawable rounded(int color,int radius){
    GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));
    return shape;
  }
  protected LinearLayout.LayoutParams actionParams(){
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
    params.setMargins(0,dp(4),0,dp(4));return params;
  }
  protected void styleButton(Button b,boolean emphasized){
    b.setAllCaps(false);b.setTextSize(14);b.setTextColor(ui.accent);
    b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
    b.setPadding(dp(12),dp(10),dp(12),dp(10));
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(ui.border),
      rounded(emphasized?ui.soft:Color.TRANSPARENT,12),null));
  }
  protected LinearLayout createScreen(String key,String title,boolean back,String tab){
    if(scroller!=null&&screenKey!=null)scrollPositions.put(screenKey,scroller.getScrollY());
    screenKey=key;hideKeyboard();strictCountdownLabels.clear();strictBarUpdates.clear();
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(ui.background);
    if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
    else getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(ui.surface);
    int flags=getWindow().getDecorView().getSystemUiVisibility();
    flags=ui.dark?flags&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):
      flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
    root.setOnApplyWindowInsetsListener((v,insets)->{
      int l,t,r,b;
      if(Build.VERSION.SDK_INT>=30){
        Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
        l=bars.left;t=bars.top;r=bars.right;b=Math.max(bars.bottom,keyboard.bottom);
        if(bottomNavigation!=null)bottomNavigation.setVisibility(keyboard.bottom>bars.bottom?View.GONE:View.VISIBLE);
      }else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();
        r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}
      v.setPadding(l,t,r,b);return insets;
    });
    LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setPadding(dp(8),dp(8),dp(8),dp(8));toolbar.setBackgroundColor(ui.surface);
    toolbar.setMinimumHeight(dp(64));
    if(back){
      android.widget.ImageButton button=new android.widget.ImageButton(this);
      button.setImageResource(R.drawable.ic_arrow_back);
      button.setImageTintList(ColorStateList.valueOf(ui.text));
      button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      button.setPadding(dp(12),dp(12),dp(12),dp(12));
      button.setContentDescription("戻る");
      button.setBackground(new RippleDrawable(ColorStateList.valueOf(ui.border),
        rounded(Color.TRANSPARENT,12),null));
      button.setOnClickListener(v->onBackPressed());
      toolbar.addView(button,new LinearLayout.LayoutParams(dp(48),dp(48)));
    }
    TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(19);
    heading.setTextColor(ui.text);heading.setTypeface(null,Typeface.BOLD);
    heading.setPadding(dp(back?16:8),0,dp(8),0);
    toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
    root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
    scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setClipToPadding(false);
    FrameLayout frame=new FrameLayout(this);
    LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(16),dp(20),dp(16),dp(28));
    content.setFocusableInTouchMode(true);content.requestFocus();
    int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(760));
    FrameLayout.LayoutParams contentParams=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
    frame.addView(content,contentParams);
    frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
      int available=Math.min(r-l,dp(760));
      if(available>0&&content.getLayoutParams().width!=available){
        content.getLayoutParams().width=available;content.requestLayout();
      }
    });
    scroller.addView(frame,new ScrollView.LayoutParams(-1,-2));
    root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout nav=new LinearLayout(this);bottomNavigation=nav;nav.setPadding(dp(8),dp(8),dp(8),dp(8));
    nav.setBackgroundColor(ui.surface);
    String[] ids={"home","groups","newapps","strict","settings"},labels={"🏠\nホーム","📂\nグループ","🛡️\n新規アプリ","🔒\n厳格","🛠️\n設定"};
    for(int i=0;i<ids.length;i++){
      final String destination=ids[i];
      Button button=new Button(this);button.setText(labels[i]);button.setContentDescription(labels[i]);
      styleButton(button,destination.equals(tab));button.setSelected(destination.equals(tab));
      button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);
      button.setLineSpacing(dp(5),1f);
      button.setPadding(dp(2),dp(6),dp(2),dp(6));button.setMinHeight(dp(56));button.setMinimumHeight(dp(56));
      if(destination.equals(tab))button.setTypeface(null,Typeface.BOLD);
      button.setOnClickListener(v->navigateTab(destination));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);
      nav.addView(button,lp);
    }
    root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    setContentView(root);root.requestApplyInsets();
    final ScrollView current=scroller;
    final int position=scrollPositions.getOrDefault(key,0);
    // Restore before the first frame, not in a posted runnable after it becomes visible.
    current.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
      @Override public boolean onPreDraw(){
        current.getViewTreeObserver().removeOnPreDrawListener(this);
        current.scrollTo(0,position);
        return true;
      }
    });
    return content;
  }
  protected void resetScrollFor(String key){
    scrollPositions.remove(key);
    // A redraw of the currently visible tab must not re-save its old scroll offset.
    if(key.equals(screenKey))screenKey=null;
  }
  protected void navigate(String destination){navigate(destination,false);}
  private void navigateTab(String destination){navigate(destination,true);}
  private void navigate(String destination,boolean instant){
    if("groups".equals(destination)){
      if(this instanceof GroupActivity){((GroupActivity)this).showGroupList();return;}
      Intent intent=new Intent(this,GroupActivity.class);
      if(instant)intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
      startActivity(intent);
      if(instant)overridePendingTransition(0,0);
    }else{
      if(this instanceof MainActivity){((MainActivity)this).showPage(destination);return;}
      Intent intent=new Intent(this,MainActivity.class).putExtra("page",destination)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
      if(instant)intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
      startActivity(intent);
      if(instant)overridePendingTransition(0,0);
      finish();
      if(instant)overridePendingTransition(0,0);
    }
  }
  /** System edge-back plus a left swipe from the right edge for button-navigation users. */
  @Override public boolean dispatchTouchEvent(MotionEvent event){
    if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
      gestureX=event.getX();gestureY=event.getY();gestureTime=event.getEventTime();
      edgeGesture=gestureX>getResources().getDisplayMetrics().widthPixels-dp(48);
    }else if(event.getActionMasked()==MotionEvent.ACTION_UP&&edgeGesture){
      edgeGesture=false;
      if(gestureX-event.getX()>dp(100)&&Math.abs(event.getY()-gestureY)<dp(56)
          &&event.getEventTime()-gestureTime<700){
        MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);cancel.recycle();onBackPressed();return true;
      }
    }else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)edgeGesture=false;
    return super.dispatchTouchEvent(event);
  }
  protected void hideKeyboard(){
    android.view.inputmethod.InputMethodManager manager=(android.view.inputmethod.InputMethodManager)
      getSystemService(Context.INPUT_METHOD_SERVICE);
    if(manager!=null)manager.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);
    if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)
      getWindow().getInsetsController().hide(WindowInsets.Type.ime());
  }
  @Override public void onWindowFocusChanged(boolean focused){
    super.onWindowFocusChanged(focused);
    if(focused&&!(getCurrentFocus() instanceof EditText))hideKeyboard();
  }
  protected String groupEmoji(String id,String name){
    String saved=getSharedPreferences("guard_ui",MODE_PRIVATE).getString("emoji."+id,null);
    if(saved!=null)return saved;
    String lower=name.toLowerCase(Locale.ROOT);
    if(name.contains("禁止")||name.contains("絶対"))return "🚫";
    if(lower.contains("ec")||name.contains("買い物")||name.contains("ショッピング"))return "🛒";
    if(lower.contains("sns")||name.contains("交流"))return "💬";
    if(name.contains("検索"))return "🔎";
    if(name.contains("ブラウザ"))return "🌐";
    if(name.contains("漫画")||name.contains("読書"))return "📚";
    if(name.contains("音楽")||name.contains("ラジオ"))return "🎵";
    if(name.contains("動画")||name.contains("アニメ")||name.contains("サイト")&&name.contains("娯楽"))return "🎬";
    if(name.contains("娯楽")||name.contains("ゲーム"))return "🎮";
    if(name.contains("アプリ"))return "📱";
    return "📂";
  }
  protected String groupTitle(String id){
    String name=GroupRules.name(getSharedPreferences("guard",MODE_PRIVATE),id);
    return groupEmoji(id,name)+" "+name;
  }
  protected void chooseGroupEmoji(String id,Runnable refresh){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(16),dp(8),dp(16),dp(12));
    EditText custom=new EditText(this);custom.setSingleLine(true);custom.setTextSize(22);
    custom.setHint("好きな絵文字・記号を入力");
    custom.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
    String name=GroupRules.name(getSharedPreferences("guard",MODE_PRIVATE),id);
    custom.setText(groupEmoji(id,name));
    panel.addView(custom,new LinearLayout.LayoutParams(-1,-2));
    TextView error=new TextView(this);error.setTextColor(ui.danger);error.setTextSize(13);
    error.setVisibility(View.GONE);panel.addView(error);
    TextView help=new TextView(this);help.setText("下の絵文字はタップすると設定できます");
    help.setTextColor(ui.muted);help.setTextSize(13);help.setPadding(0,dp(10),0,dp(12));panel.addView(help);
    android.widget.GridLayout grid=new android.widget.GridLayout(this);grid.setColumnCount(5);
    String[] emojis={"🎮","🎬","🎵","📺","📚","🛒","💬","🔎","🌐","📱",
      "🚫","🔒","⏱","🎯","🧠","💼","🏠","🏃","⚽","⚾",
      "☕","🍀","⭐","🔥","❤️","🐱","🐶","🌙","☀️","📂"};
    String[] names={"ゲーム","映画・動画","音楽","テレビ","読書","買い物","会話","検索","ウェブ","アプリ",
      "禁止","ロック","時間","目標","勉強","仕事","家","運動","サッカー","野球",
      "休憩","幸運","星","炎","ハート","猫","犬","夜","昼","フォルダー"};
    ScrollView scroll=new ScrollView(this);scroll.addView(panel);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("グループの絵文字")
      .setView(scroll).setNegativeButton("閉じる",null)
      .setNeutralButton("自動に戻す",(d,w)->{
        getSharedPreferences("guard_ui",MODE_PRIVATE).edit().remove("emoji."+id).apply();refresh.run();
      }).setPositiveButton("保存",null).create();
    for(int i=0;i<emojis.length;i++){
      final String emoji=emojis[i];
      Button button=new Button(this);button.setText(emoji);styleButton(button,emoji.equals(custom.getText().toString()));
      button.setTextSize(26);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(2),dp(4),dp(2),dp(4));
      button.setContentDescription(emoji+" "+names[i]);
      button.setSelected(emoji.equals(custom.getText().toString()));
      button.setOnClickListener(v->{
        getSharedPreferences("guard_ui",MODE_PRIVATE).edit().putString("emoji."+id,emoji).apply();
        dialog.dismiss();refresh.run();
      });
      android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams();
      lp.columnSpec=android.widget.GridLayout.spec(i%5,1f);lp.rowSpec=android.widget.GridLayout.spec(i/5);
      lp.width=0;lp.height=dp(54);lp.setMargins(dp(3),dp(3),dp(3),dp(3));grid.addView(button,lp);
    }
    panel.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    dialog.setOnDismissListener(d->hideKeyboard());
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();
    panel.setFocusableInTouchMode(true);panel.requestFocus();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      String value=custom.getText().toString().trim();
      if(value.isEmpty()||value.codePointCount(0,value.length())>12||
          value.codePoints().anyMatch(Character::isISOControl)){
        error.setText("絵文字・記号を1〜12文字で入力してください");error.setVisibility(View.VISIBLE);return;
      }
      getSharedPreferences("guard_ui",MODE_PRIVATE).edit().putString("emoji."+id,value).apply();
      dialog.dismiss();refresh.run();
    });
  }
  protected void chooseTheme(){
    String[] values={"system","light","dark"},labels={"端末の設定に合わせる","ライト","ダーク"};
    String current=getSharedPreferences("guard_ui",MODE_PRIVATE).getString("theme","system");
    int selected="dark".equals(current)?2:"light".equals(current)?1:0;
    new AlertDialog.Builder(this).setTitle("表示テーマ")
      .setSingleChoiceItems(labels,selected,(dialog,index)->{
        getSharedPreferences("guard_ui",MODE_PRIVATE).edit().putString("theme",values[index]).apply();
        dialog.dismiss();recreate();
      }).setNegativeButton("閉じる",null).show();
  }
  protected String themeLabel(){
    String mode=getSharedPreferences("guard_ui",MODE_PRIVATE).getString("theme","system");
    return "dark".equals(mode)?"ダーク":"light".equals(mode)?"ライト":"端末の設定に合わせる";
  }
  protected EditText searchBox(LinearLayout parent,String hint){
    EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(15);
    input.setTextColor(ui.text);input.setHintTextColor(ui.muted);input.setHint(hint);
    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setBackground(rounded(ui.soft,12));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(12));
    parent.addView(input,lp);return input;
  }
  protected void searchableMembers(String title,List<String> values,Runnable edit){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(20),dp(4),dp(20),dp(8));
    EditText query=searchBox(panel,"名前・サイト名で検索");
    TextView counter=new TextView(this);counter.setTextSize(13);counter.setTextColor(ui.muted);
    counter.setPadding(0,0,0,dp(10));panel.addView(counter);
    ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>(values)){
      @Override public View getView(int position,View convert,ViewGroup parent){
        TextView row=(TextView)super.getView(position,convert,parent);
        row.setTextColor(ui.text);row.setTextSize(15);row.setMinHeight(dp(48));return row;
      }
    };
    ListView list=new ListView(this);list.setAdapter(adapter);list.setDividerHeight(dp(1));
    TextView empty=new TextView(this);empty.setText(values.isEmpty()?"個別登録はありません":"一致する項目はありません");
    empty.setTextColor(ui.muted);empty.setPadding(dp(8),dp(24),dp(8),dp(24));panel.addView(empty);
    list.setEmptyView(empty);panel.addView(list,new LinearLayout.LayoutParams(-1,dp(300)));
    counter.setText(values.size()+"件");
    query.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){
        String needle=s.toString().toLowerCase(Locale.ROOT);adapter.setNotifyOnChange(false);adapter.clear();
        for(String value:values)if(value.toLowerCase(Locale.ROOT).contains(needle))adapter.add(value);
        adapter.notifyDataSetChanged();counter.setText(adapter.getCount()+" / "+values.size()+"件");
      }
      public void afterTextChanged(Editable e){}
    });
    AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(title).setView(panel).setNegativeButton("閉じる",null);
    if(edit!=null)dialog.setPositiveButton("編集",(d,w)->edit.run());
    AlertDialog shown=dialog.create();shown.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    shown.setOnDismissListener(d->hideKeyboard());shown.show();panel.setFocusableInTouchMode(true);panel.requestFocus();
  }
  protected void searchableApps(String title,List<String> names,boolean[] chosen,Runnable save){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(18),dp(4),dp(18),dp(8));EditText query=searchBox(panel,"アプリ名で検索");
    TextView count=new TextView(this);count.setTextColor(ui.muted);count.setPadding(0,0,0,dp(8));panel.addView(count);
    List<Integer> shown=new ArrayList<>();for(int i=0;i<names.size();i++)shown.add(i);
    Runnable updateCount=()->{int n=0;for(boolean value:chosen)if(value)n++;count.setText(n+"件を選択中");};
    updateCount.run();
    BaseAdapter adapter=new BaseAdapter(){
      public int getCount(){return shown.size();}
      public Object getItem(int p){return names.get(shown.get(p));}
      public long getItemId(int p){return shown.get(p);}
      public View getView(int position,View convert,ViewGroup parent){
        int index=shown.get(position);CheckBox row=new CheckBox(GuardActivity.this);
        String value=names.get(index);int suffix=value.indexOf(" [");
        row.setText(suffix<0?value:value.substring(0,suffix));row.setContentDescription(value);
        row.setTextSize(15);row.setTextColor(ui.text);row.setMinHeight(dp(52));
        row.setButtonTintList(ColorStateList.valueOf(ui.accent));row.setChecked(chosen[index]);
        row.setOnCheckedChangeListener((button,checked)->{chosen[index]=checked;updateCount.run();});
        return row;
      }
    };
    ListView list=new ListView(this);list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,dp(340)));
    query.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){
        shown.clear();String q=s.toString().toLowerCase(Locale.ROOT);
        for(int i=0;i<names.size();i++)if(names.get(i).toLowerCase(Locale.ROOT).contains(q))shown.add(i);
        adapter.notifyDataSetChanged();
      }
      public void afterTextChanged(Editable e){}
    });
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(panel)
      .setPositiveButton("保存",(d,w)->save.run()).setNegativeButton("キャンセル",null).create();
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    dialog.setOnDismissListener(d->hideKeyboard());dialog.show();panel.setFocusableInTouchMode(true);panel.requestFocus();
  }
}
