package jp.personal.entertainmentguard;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Classifies actual destination hosts/paths only; never reads search queries or page contents. */
public final class Classifier {
  private Classifier() {}
  public static final class Hit {
    public final String host, category;
    public final boolean strong, search;
    public Hit(String host,String category,boolean strong,boolean search) {
      this.host=host; this.category=category; this.strong=strong; this.search=search;
    }
    public boolean flagged(){return category!=null;}
  }
  private static final Map<String,String> BLOCK=new LinkedHashMap<>();
  private static final Map<String,String> WARN=new LinkedHashMap<>();
  static {
    for(String domain:new String[]{
      "shonenjumpplus.com","comic-fuz.com","comic-days.com","piccoma.com",
      "mechacomic.jp","cmoa.jp","ebookjapan.yahoo.co.jp","manga-one.com",
      "manga-park.com","mangaplus.shueisha.co.jp","webtoons.com"
    }) BLOCK.put(domain,"漫画");
    for(String domain:new String[]{
      "animestore.docomo.ne.jp","anime.dmkt-sp.jp","crunchyroll.com",
      "animefesta.jp","d-anime.com","animehodai.jp"
    }) BLOCK.put(domain,"アニメ");
    for(String domain:new String[]{"poki.com","crazygames.com","miniclip.com",
      "games.yahoo.co.jp","game-game.com","now.gg"})BLOCK.put(domain,"ゲーム");
    for(String domain:new String[]{"youtube.com","youtu.be","youtube-nocookie.com",
      "netflix.com","tver.jp","nicovideo.jp","niconico.jp","tiktok.com",
      "twitch.tv","abema.tv","disneyplus.com","primevideo.com",
      "hulu.jp","u-next.jp","bilibili.com"})BLOCK.put(domain,"動画");
    for(String domain:new String[]{"dazn.com","spozone.jp","jod.jsports.co.jp",
      "watch.nba.com","mlb.tv"})BLOCK.put(domain,"スポーツ観戦");
    for(String domain:new String[]{"sports.yahoo.co.jp","baseball.yahoo.co.jp",
      "espn.com","mlb.com","nba.com"})WARN.put(domain,"スポーツ観戦");
    for(String domain:new String[]{"4gamer.net","famitsu.com","gamewith.jp",
      "game8.jp"})WARN.put(domain,"ゲーム");
  }
  public static boolean hostIs(String host,String candidate){
    return host!=null&&(host.equals(candidate)||host.endsWith("."+candidate));
  }
  private static String match(String host,Map<String,String> map){
    for(Map.Entry<String,String> entry:map.entrySet())
      if(hostIs(host,entry.getKey()))return entry.getValue();
    return null;
  }
  public static String host(String input){
    if(input==null)return null;
    String raw=input.trim().toLowerCase(Locale.ROOT);
    if(raw.length()<4||raw.length()>2048||raw.contains(" "))return null;
    if(!raw.contains("://"))raw="https://"+raw;
    try{
      String h=new URI(raw).getHost();
      if(h==null||!h.contains("."))return null;
      return h.startsWith("www.")?h.substring(4):h;
    }catch(Exception ignored){return null;}
  }
  public static Hit inspect(String input){
    String h=host(input);
    if(h==null)return new Hit(null,null,false,false);
    String block=match(h,BLOCK);
    if(block!=null)return new Hit(h,block,true,false);
    // Streaming paths on mixed-use news portals are directly blocked.
    if(hostIs(h,"mlb.com") && input.toLowerCase(Locale.ROOT).matches(".*(/tv|/live|watch/).*"))
      return new Hit(h,"スポーツ観戦",true,false);
    String warn=match(h,WARN);
    return new Hit(h,warn,false,false);
  }
}
