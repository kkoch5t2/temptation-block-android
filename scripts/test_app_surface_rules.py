#!/usr/bin/env python3
"""Shared Google package regression tests without any device/policy mutations."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
# Recorded + dialog: none of the three conversation markers is in its root.
import xml.etree.ElementTree as ET
fixture=ET.parse(root/"scripts/fixtures/gemini_attachment_surface.xml").getroot()
owner="com.google.android.googlequicksearchbox"
source=(root/"app/src/main/java/jp/personal/entertainmentguard/AppSurfaceRules.java").read_text()
import re
markers={owner+suffix for suffix in re.findall(r'GOOGLE\+"([^"]+)"',source.split("GEMINI_VIEW_IDS={",1)[1].split("};",1)[0])}
nodes=list(fixture.iter("node"))
assert not any(n.get("resource-id") in {owner+":id/"+suffix for suffix in
    ("assistant_robin_main_activity","assistant_robin_chat_fragment_container","assistant_robin_conversation_container")} for n in nodes)
assert any(n.get("package")==owner and n.get("resource-id") in markers for n in nodes), "attachment dialog must resolve as Gemini"
assert not any(n.get("resource-id") in markers for n in ET.fromstring('<node package="'+owner+'" resource-id="'+owner+':id/search_box"/>').iter("node"))
for surface in ET.parse(root/"scripts/fixtures/gemini_camera_surfaces.xml").getroot():
    gemini = any(n.get("package")==owner and n.get("resource-id") in markers
                 for n in surface.iter("node"))
    expected = surface.get("name") not in {"google-search", "foreign-owner"}
    assert gemini == expected, surface.get("name")
print("PASS camera, permission, preview, Live and Search/owner separation")
print("PASS recorded Gemini attachment dialog and Google search isolation")
with tempfile.TemporaryDirectory(prefix="guard-surfaces-") as directory:
    tmp=Path(directory)
    test=tmp/"AppSurfaceRulesTest.java"
    test.write_text("""
import jp.personal.entertainmentguard.AppSurfaceRules;
public class AppSurfaceRulesTest {
  static int count;
  static void check(String pkg,String activity,boolean view,String expected){
    String actual=AppSurfaceRules.restrictionPackage(pkg,activity,view);
    if(!java.util.Objects.equals(actual,expected))throw new AssertionError(actual+" != "+expected);
    count++;
  }
  public static void main(String[] args){
    String g=AppSurfaceRules.GOOGLE,b=AppSurfaceRules.GEMINI;
    check(g,AppSurfaceRules.GEMINI_ACTIVITY,false,b);
    check(g,null,true,b);
    // Attachment dialog events have only a generic Android class. The heartbeat
    // has no activity name at all, so both paths must use the dialog's native ID.
    check(g,"android.app.Dialog",true,b);
    check(g,null,true,b);
    check(g,"com.google.android.googlequicksearchbox.SearchActivity",false,g);
    check(g,"android.widget.FrameLayout",true,b);
    check(g,"com.google.android.googlequicksearchbox.SearchActivity",false,g);
    check(g,null,false,null);
    check(g,AppSurfaceRules.GEMINI_ACTIVITY,false,b);
    check(g,null,false,null); // Missing evidence must never become Search.
    check(g,"com.google.android.apps.search.assistant.surfaces.voice.robin.camera.CameraActivity",false,b);
    check(g,"com.google.android.apps.search.assistant.surfaces.voice.robin.live.LiveActivity",false,b);
    check(g,"com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity",false,b);
    check(g,"com.google.android.apps.search.assistant.surfaces.voice.robinevil.CameraActivity",false,null);
    check(g,"android.app.Dialog",true,b); // Camera/dialog native root, heartbeat and events.
    check(g,null,true,b); // Camera heartbeat must use the same Gemini policy.
    check(g,"com.google.android.googlequicksearchbox.SearchActivity",false,g);
    check(g,"com.google.android.apps.search.lens.LensActivity",false,null);
    check("com.google.android.GoogleCamera",null,true,"com.google.android.GoogleCamera");
    check("com.android.chrome",null,true,"com.android.chrome");

    check("com.android.chrome",AppSurfaceRules.GEMINI_ACTIVITY,true,"com.android.chrome");
    check("com.example.other",null,true,"com.example.other");
    check(g,"com.example.GeminiActivity",false,null);
    check(b,null,false,b); // Explicit Gemini restriction remains possible.
    check(null,null,false,null);
    check(g,"android.widget.FrameLayout",false,null);
    check(g,"android.app.Dialog",false,null);
    if(!g.equals(AppSurfaceRules.restrictionPackage(g,null,false,true)))throw new AssertionError("positive Search native tree");
    if(!b.equals(AppSurfaceRules.restrictionPackage(g,null,true,true)))throw new AssertionError("Gemini native evidence wins");
    if(AppSurfaceRules.restrictionPackage(g,null,false,false)!=null)throw new AssertionError("unknown is not Search");

    String chrome="com.android.chrome";
    if(!AppSurfaceRules.remoteDesktopSurface(chrome,"https://remotedesktop.google.com/access",false,null))throw new AssertionError("remote URL");
    if(!AppSurfaceRules.remoteDesktopSurface(chrome,null,true,"リモート アクセス - Chrome リモート デスクトップ"))throw new AssertionError("remote TWA");
    if(!AppSurfaceRules.remoteDesktopSurface(chrome,null,true,"DESKTOP - Chrome Remote Desktop"))throw new AssertionError("session TWA");
    for(String url:new String[]{"https://google.com/search?q=remotedesktop.google.com","https://youtube.com","https://remotedesktop.google.com.evil.test/","https://remotedesktop.google.com@evil.test/","http://remotedesktop.google.com/","https://example.com/#remotedesktop.google.com"}){
      if(AppSurfaceRules.remoteDesktopSurface(chrome,url,true,"Chrome Remote Desktop"))throw new AssertionError("URL escaped: "+url);
    }
    if(AppSurfaceRules.remoteDesktopSurface(chrome,null,false,"Chrome Remote Desktop"))throw new AssertionError("normal Chrome title");
    if(AppSurfaceRules.remoteDesktopSurface(chrome,null,true,"Chrome Remote Desktop 検索 - Google"))throw new AssertionError("search title");
    if(AppSurfaceRules.remoteDesktopSurface(chrome,null,true,null))throw new AssertionError("unknown TWA");
    if(AppSurfaceRules.remoteDesktopSurface("com.example.other",null,true,"Chrome Remote Desktop"))throw new AssertionError("other package");
    if(!AppSurfaceRules.remoteDesktopBrand("goog-lr-6","Chrome リモート デスクトップ"))throw new AssertionError("client brand");
    if(!AppSurfaceRules.remoteDesktopBrand("goog-lr-120","Chrome Remote Desktop"))throw new AssertionError("English client brand");
    if(AppSurfaceRules.remoteDesktopBrand("heading","Chrome Remote Desktop"))throw new AssertionError("page text alone");
    if(AppSurfaceRules.remoteDesktopBrand("goog-lr-6","Chrome Remote Desktop の検索結果"))throw new AssertionError("search result");
    if(AppSurfaceRules.remoteDesktopBrand("goog-lr-evil","Chrome Remote Desktop"))throw new AssertionError("invalid tooltip id");
    if(!AppSurfaceRules.remoteDesktopConnectionControl("接続をキャンセルしてホストの一覧に戻ります"))throw new AssertionError("Japanese client control");
    if(!AppSurfaceRules.remoteDesktopConnectionControl("Cancel connecting and return to the host list"))throw new AssertionError("English client control");
    if(AppSurfaceRules.remoteDesktopConnectionControl("接続をキャンセル"))throw new AssertionError("generic cancel");
    if(AppSurfaceRules.remoteDesktopConnectionControl("接続をキャンセルしてホストの一覧に戻ります の検索結果"))throw new AssertionError("query text");
    if(AppSurfaceRules.remoteDesktopConnectionControl(null))throw new AssertionError("missing control");
    System.out.println("PASS Remote Desktop connection controls (5 cases)");
    System.out.println("PASS Remote Desktop client brand (5 cases)");
    AppSurfaceRules.RemoteDesktopSession session=new AppSurfaceRules.RemoteDesktopSession();
    if(!session.matches(10,chrome,null,true,"Chrome Remote Desktop"))throw new AssertionError("bind list");
    if(!session.matches(10,chrome,null,true,"DESKTOP-TEST"))throw new AssertionError("connection title change");
    if(!session.matches(10,chrome,null,true,null))throw new AssertionError("same window loading");
    if(session.matches(11,chrome,null,true,"DESKTOP-TEST"))throw new AssertionError("other TWA");
    if(session.matches(12,chrome,null,false,"Chrome Remote Desktop"))throw new AssertionError("normal Chrome");
    if(!session.matches(10,chrome,null,true,"DESKTOP-TEST"))throw new AssertionError("return to same session");
    if(session.matches(10,chrome,"https://google.com",true,"Chrome Remote Desktop"))throw new AssertionError("leave trusted origin");
    if(session.matches(10,chrome,null,true,"DESKTOP-TEST"))throw new AssertionError("revoked after origin change");
    if(!session.matches(10,chrome,null,true,"Chrome Remote Desktop"))throw new AssertionError("rebind");
    if(session.matches(10,chrome,null,false,"Chrome Remote Desktop"))throw new AssertionError("container gone");
    if(session.matches(10,chrome,null,true,"DESKTOP-TEST"))throw new AssertionError("revoked on normal Chrome");
    if(new AppSurfaceRules.RemoteDesktopSession().matches(10,chrome,null,true,"DESKTOP-TEST"))throw new AssertionError("restart does not reuse IDs");
    if(session.matches(-1,chrome,null,true,"Chrome Remote Desktop"))throw new AssertionError("invalid window");
    AppSurfaceRules.RemoteDesktopSession cold=new AppSurfaceRules.RemoteDesktopSession();
    if(!cold.matches(20,chrome,null,true,null,10000))throw new AssertionError("cold load waits");
    if(!cold.matches(20,chrome,null,true,null,14999))throw new AssertionError("loading before deadline");
    if(cold.matches(20,chrome,null,true,null,15000))throw new AssertionError("unknown load times out");
    if(cold.matches(20,chrome,null,true,null,16000))throw new AssertionError("loading does not restart");
    if(cold.matches(21,chrome,null,true,"Other web app",10000))throw new AssertionError("other TWA never waits");
    if(cold.matches(22,chrome,null,false,null,10000))throw new AssertionError("ordinary browser never waits");
    if(!cold.matches(20,chrome,null,true,"Chrome Remote Desktop",16000))throw new AssertionError("resolved load binds");
    if(!cold.matches(20,chrome,null,true,"DESKTOP",20000))throw new AssertionError("resolved session persists");
    System.out.println("PASS Remote Desktop cold load (8 cases)");
    System.out.println("PASS Remote Desktop session transitions (13 cases)");
    System.out.println("PASS Remote Desktop surface isolation (13 cases)");
    System.out.println("PASS "+count+" surface routing cases");
  }
}
""")
    subprocess.run(["javac","-d",str(tmp),str(root/"app/src/main/java/jp/personal/entertainmentguard/AppSurfaceRules.java"),str(test)],check=True)
    subprocess.run(["java","-cp",str(tmp),"AppSurfaceRulesTest"],check=True)
