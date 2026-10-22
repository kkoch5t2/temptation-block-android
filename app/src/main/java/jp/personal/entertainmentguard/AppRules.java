package jp.personal.entertainmentguard;

import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

/** Known entertainment apps + user-curated packages. Avoid matching all Android packages. */
public final class AppRules {
  private AppRules(){}
  private static final Map<String,String> KNOWN=new LinkedHashMap<>();
  static{
    for(String p:new String[]{"com.google.android.youtube",
        "com.netflix.mediaclient","com.amazon.avod.thirdpartyclient","com.disney.disneyplus",
        "com.hulu.plus","jp.happyon.android","jp.co.tver.androidtv","jp.hulu.android",
        "com.zhiliaoapp.musically","com.ss.android.ugc.trill","tv.twitch.android.app",
        "com.abema","com.niconico.nicovideo","com.google.android.videos",
        "com.miui.videoplayer","jp.hamitv.hamiand1"})
      KNOWN.put(p,"動画");
    for(String p:new String[]{"com.miui.player","com.miui.fm",
        "com.google.android.apps.youtube.music"})
      KNOWN.put(p,"音楽・ラジオ");
    for(String p:new String[]{"com.dazn","com.dazn.app","com.nbaimd.gametime.nba2011",
        "com.bamnetworks.mobile.android.gameday.atbat"})
      KNOWN.put(p,"スポーツ観戦");
    for(String p:new String[]{"com.nianticlabs.pokemongo","com.supercell.clashofclans",
        "com.supercell.clashroyale","com.roblox.client","com.mojang.minecraftpe",
        "com.epicgames.fortnite","com.miHoYo.GenshinImpact","com.miHoYo.HonkaiStarRail",
        "jp.co.cygames.umamusume","com.bandainamcoent.dblegends_ww"})
      KNOWN.put(p,"ゲーム");
    for(String p:new String[]{"jp.co.cmoa.app","jp.co.smasma.app",
        "jp.co.shueisha.mangaplus","com.kakao.piccoma",
        "jp.kakao.piccoma","com.comic_fuz",
        "com.access_company.android.sh_jumpplus"})
      KNOWN.put(p,"漫画");
    for(String p:new String[]{"com.crunchyroll.crunchyroid","com.nttdocomo.android.danimeapp"})
      KNOWN.put(p,"アニメ");
  }
  public static String knownCategory(String pkg){return KNOWN.get(pkg);}
  public static String category(String pkg,SharedPreferences prefs){
    if(pkg==null)return null;
    // Explicit user choices override built-in defaults in either direction.
    if(prefs.contains("app:"+pkg))
      return prefs.getBoolean("app:"+pkg,false)?"指定アプリ":null;
    return KNOWN.get(pkg);
  }
  public static boolean supportedBrowser(String pkg){
    return "com.android.chrome".equals(pkg)||"com.brave.browser".equals(pkg);
  }
  public static boolean unsupportedBrowser(String pkg){
    if(pkg==null)return false;
    for(String p:new String[]{
      "org.mozilla.firefox","org.mozilla.fenix","org.mozilla.focus","com.opera.browser",
      "com.opera.mini.native","com.microsoft.emmx","com.duckduckgo.mobile.android",
      "com.sec.android.app.sbrowser","com.vivaldi.browser","com.kiwibrowser.browser",
      "com.yandex.browser","com.UCMobile.intl","com.ecosia.android",
      "com.mi.globalbrowser","com.mi.browser","com.android.browser"})
      if(pkg.equals(p))return true;
    return false;
  }
}
