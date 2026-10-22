package jp.personal.entertainmentguard;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

final class GuardTheme {
  final boolean dark;
  final int background, surface, text, muted, accent, success, danger, soft, border;
  GuardTheme(Context context) {
    String mode=context.getSharedPreferences("guard_ui",Context.MODE_PRIVATE).getString("theme","system");
    dark="dark".equals(mode)||("system".equals(mode)&&
      (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
    background=Color.parseColor(dark?"#10151E":"#F3F6FA");
    surface=Color.parseColor(dark?"#1C2431":"#FFFFFF");
    text=Color.parseColor(dark?"#EDF2F8":"#1A2F4C");
    muted=Color.parseColor(dark?"#A9B7C9":"#617086");
    accent=Color.parseColor(dark?"#A8C9FF":"#2B62A2");
    success=Color.parseColor(dark?"#7AE0BA":"#146A4E");
    danger=Color.parseColor(dark?"#FFAEA7":"#AD383B");
    soft=Color.parseColor(dark?"#29364B":"#E8EFF8");
    border=Color.parseColor(dark?"#344155":"#DDE5F0");
  }
  int readable(int original) {
    if(!dark||original==Color.WHITE||original==text||original==muted||original==accent||original==success||original==danger)return original;
    int r=Color.red(original),g=Color.green(original),b=Color.blue(original);
    if(r>g*1.3&&r>b*1.2)return danger;
    if(g>r*1.2&&g>b*1.05)return success;
    if(b>r*1.3&&b>g*1.1)return accent;
    return original==Color.GRAY||Math.abs(r-g)<18&&r>65?muted:text;
  }
}
