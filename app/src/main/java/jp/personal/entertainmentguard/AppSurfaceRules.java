package jp.personal.entertainmentguard;

/** Resolve shared browser/Google surfaces to the app the user actually selected. */
public final class AppSurfaceRules {
  private AppSurfaceRules(){}
  public static final String REMOTE_DESKTOP="com.google.chromeremotedesktop";
  /** Hidden-toolbar Remote Desktop is a Chrome web app, not a normal browser tab.
   * Never match arbitrary document text or remember an exemption across windows. */
  public static boolean remoteDesktopSurface(String pkg,String url,boolean chromeWebApp,String documentTitle){
    if(!"com.android.chrome".equals(pkg))return false;
    if(url!=null){
      try{
        java.net.URI uri=new java.net.URI(url.contains("://")?url:"https://"+url);
        return "https".equalsIgnoreCase(uri.getScheme())
          && "remotedesktop.google.com".equalsIgnoreCase(uri.getHost())
          && uri.getUserInfo()==null && (uri.getPort()==-1||uri.getPort()==443);
      }catch(Exception ignored){return false;}
    }
    if(!chromeWebApp||documentTitle==null)return false;
    String title=documentTitle.trim();
    for(String name:new String[]{"Chrome Remote Desktop","Chrome リモート デスクトップ"}){
      if(title.equals(name)||title.endsWith(" - "+name))return true;
    }
    return false;
  }
  /** Google's Remote Desktop client retains its own brand tooltip while the
   * document title changes to a host name. Match that element, never text alone. */
  public static boolean remoteDesktopBrand(String elementId,String label){
    if(elementId==null||!elementId.matches("goog-lr-[0-9]+"))return false;
    return "Chrome Remote Desktop".equals(label)||"Chrome リモート デスクトップ".equals(label);
  }
  public static boolean remoteDesktopConnectionControl(String label){
    return "接続をキャンセルしてホストの一覧に戻ります".equals(label)
      ||"Cancel connecting and return to the host list".equals(label);
  }
  /** A TWA keeps one native window while changing from its list to a host/session.
   * External origins regain Chrome's URL bar; those URLs revoke the binding. */
  public static final class RemoteDesktopSession {
    private final java.util.Set<Integer> windows=new java.util.HashSet<>();
    private final java.util.Map<Integer,Long> loadingSince=new java.util.HashMap<>();
    public boolean matches(int windowId,String pkg,String url,boolean webApp,String title){
      return matches(windowId,pkg,url,webApp,title,System.nanoTime()/1000000L);
    }
    public boolean matches(int windowId,String pkg,String url,boolean webApp,String title,long now){
      if(!"com.android.chrome".equals(pkg))return false;
      if(url!=null){
        boolean remote=remoteDesktopSurface(pkg,url,false,null);
        if(!remote)windows.remove(windowId);
        return remote;
      }
      if(!webApp){windows.remove(windowId);return false;}
      if(windowId<0)return false;
      if(remoteDesktopSurface(pkg,null,true,title))windows.add(windowId);
      if(windows.contains(windowId))return true;
      // Chrome creates the native TWA window before its document metadata.
      // Wait once for that window, only while the title is still empty.
      if(title==null||title.trim().isEmpty()){
        long since=loadingSince.computeIfAbsent(windowId,id->now);
        return now>=since && now-since<5000;
      }
      return false;
    }
  }
  public static final String GOOGLE="com.google.android.googlequicksearchbox";
  public static final String GEMINI="com.google.android.apps.bard";
  private static final String GEMINI_ACTIVITY_PREFIX=
    "com.google.android.apps.search.assistant.surfaces.voice.robin.";
  public static final String GEMINI_ACTIVITY=
    "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity";
  public static final String[] GEMINI_VIEW_IDS={
    GOOGLE+":id/assistant_robin_main_activity",
    GOOGLE+":id/assistant_robin_chat_fragment_container",
    GOOGLE+":id/assistant_robin_conversation_container",
    // The + menu is a separate dialog root; the conversation IDs are hidden.
    GOOGLE+":id/assistant_robin_add_attachment_bottomsheet",
    GOOGLE+":id/assistant_robin_add_attachment_scroll_view",
    GOOGLE+":id/assistant_robin_add_attachment_bottomsheet_content",
    // Native Gemini camera, preview and Live roots from the Google app resources.
    // They replace the chat tree, so chat-only detection misattributes them to Search.
    GOOGLE+":id/assistant_robin_camera_compose_view",
    GOOGLE+":id/assistant_robin_capture_camera_preview_container",
    GOOGLE+":id/assistant_robin_capture_camera_preview",
    GOOGLE+":id/assistant_robin_capture_viewfinder",
    GOOGLE+":id/assistant_robin_capture_top_container",
    GOOGLE+":id/assistant_robin_capture_bottom_container",
    GOOGLE+":id/assistant_robin_capture_ask_permission_view",
    GOOGLE+":id/assistant_robin_inline_camera_content",
    GOOGLE+":id/assistant_robin_imagepreview_container",
    GOOGLE+":id/assistant_robin_imagepreview_overlay_layout",
    GOOGLE+":id/assistant_robin_chat_live_container",
    GOOGLE+":id/assistant_robin_live_camera_container",
    GOOGLE+":id/assistant_robin_live_camera_view"
  };
  public static final String[] GOOGLE_SEARCH_VIEW_IDS={
    GOOGLE+":id/search_box",
    GOOGLE+":id/search_plate",
    GOOGLE+":id/search_plate_inner",
    GOOGLE+":id/search_box_text"
  };
  public static String restrictionPackage(String pkg,String activity,boolean geminiView){
    return restrictionPackage(pkg,activity,geminiView,false);
  }
  public static String restrictionPackage(String pkg,String activity,boolean geminiView,boolean searchView){
    if(!GOOGLE.equals(pkg))return pkg;
    if(geminiView||(activity!=null&&activity.startsWith(GEMINI_ACTIVITY_PREFIX)))return GEMINI;
    if(searchView||"com.google.android.googlequicksearchbox.SearchActivity".equals(activity)
        ||"com.google.android.apps.gsa.queryentry.QueryEntryActivity".equals(activity))return GOOGLE;
    // An unrecognized shared surface is not proof of Google Search use.
    return null;
  }
}
