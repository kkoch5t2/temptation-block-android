package jp.personal.entertainmentguard;

import java.net.URI;
import java.util.Locale;

/** Match tab titles only when their identity is reasonably clear. */
public final class BlockedTabMatcher {
  private BlockedTabMatcher(){}

  public static String siteName(String hostname){
    if(hostname==null)return "";
    String h=hostname.toLowerCase(Locale.ROOT);
    if(Classifier.hostIs(h,"youtube.com") || Classifier.hostIs(h,"youtu.be"))return "youtube";
    if(Classifier.hostIs(h,"piccoma.com"))return "piccoma";
    if(Classifier.hostIs(h,"netflix.com"))return "netflix";
    if(Classifier.hostIs(h,"tiktok.com"))return "tiktok";
    if(Classifier.hostIs(h,"twitch.tv"))return "twitch";
    if(Classifier.hostIs(h,"nicovideo.jp")||Classifier.hostIs(h,"niconico.jp"))return "niconico";
    if(Classifier.hostIs(h,"crunchyroll.com"))return "crunchyroll";
    if(Classifier.hostIs(h,"poki.com"))return "poki";
    if(Classifier.hostIs(h,"crazygames.com"))return "crazygames";
    if(Classifier.hostIs(h,"dazn.com"))return "dazn";
    if(Classifier.hostIs(h,"tver.jp"))return "tver";
    if(Classifier.hostIs(h,"abema.tv"))return "abema";
    if(Classifier.hostIs(h,"disneyplus.com"))return "disney";
    if(Classifier.hostIs(h,"primevideo.com"))return "prime video";
    if(Classifier.hostIs(h,"mlb.tv"))return "mlb";
    return "";
  }

  public static boolean titleMatches(String url,String hostname,String title){
    if(title==null||title.trim().isEmpty())return false;
    String token=siteName(hostname);
    if(token.isEmpty())return false; // Avoid closing an unrelated tab.
    String lower=title.toLowerCase(Locale.ROOT).trim();
    if(token.equals("youtube") && (lower.contains(" - youtube") || lower.equals("youtube") || lower.contains("youtube.com")))return true;
    if(token.equals("piccoma") && (lower.contains("ピッコマ") || lower.contains("piccoma")))return true;
    return lower.contains(token);
  }

  public static boolean isHomePage(String raw){
    try{
      URI u=new URI(raw.contains("://")?raw:"https://"+raw);
      String p=u.getPath();
      return p==null||p.isEmpty()||p.equals("/");
    }catch(Exception ignored){return false;}
  }

  /** Give page-specific tabs priority over a generic homepage title and vice versa. */
  public static int score(String raw,String host,String title){
    if(!titleMatches(raw,host,title))return -1;
    String token=siteName(host);
    String t=title.toLowerCase(Locale.ROOT).trim();
    if(token.equals("youtube")){
      boolean home=t.equals("youtube");
      return home==(isHomePage(raw))?2:1;
    }
    return 2;
  }
}
