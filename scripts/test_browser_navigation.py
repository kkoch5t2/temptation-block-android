#!/usr/bin/env python3
"""Regression: typed URLs/queries must not become committed visits."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="guard-navigation-") as folder:
    tmp=Path(folder)
    test=tmp/"BrowserNavigationTest.java"
    test.write_text("""
import jp.personal.entertainmentguard.*;
public class BrowserNavigationTest {
  static int count;
  static void eq(Object a,Object b,String name){
    if(!java.util.Objects.equals(a,b))throw new AssertionError(name+": "+a+" != "+b);count++;
  }
  public static void main(String[] args){
    BrowserNavigationState n=new BrowserNavigationState();
    String chrome="com.android.chrome",safe="https://www.google.com/";
    eq(n.observe(chrome,1,"youtube.com",true),null,"cold typed URL");
    eq(n.observe(chrome,1,safe,false),safe,"committed safe page");
    eq(n.observe(chrome,1,"youtube.com",true),safe,"typed domain keeps actual page");
    eq(n.observe(chrome,1,"https://google.com/search?q=youtube",true),safe,"typed search URL ignored");
    eq(Classifier.inspect(n.observe(chrome,1,"youtube.com",true)).flagged(),false,"no premature block");
    eq(n.observe(chrome,1,"https://youtube.com/",false),"https://youtube.com/","confirmed visit");
    eq(Classifier.inspect(n.observe(chrome,1,"https://youtube.com/",false)).strong,true,"confirmed video site blocked");
    eq(n.observe(chrome,1,"",true),"https://youtube.com/","editing cannot evade existing denied visit");
    String search="https://google.com/search?q=youtube";
    eq(Classifier.inspect(n.observe(chrome,1,search,false)).flagged(),false,"submitted YouTube search stays available");
    for(String engine:new String[]{"google.com/search?q=", "www.google.com/search?q=", "bing.com/search?q=", "duckduckgo.com/?q=", "search.yahoo.co.jp/search?p=", "ecosia.org/search?q=", "startpage.com/sp/search?query="}){
      for(String query:new String[]{"YouTube","youtube.com","youtube%20settings","%E5%8B%95%E7%94%BB","manga","anime","mlb","npb","baseball","soccer","game","%E6%BC%AB%E7%94%BB","%E3%82%B2%E3%83%BC%E3%83%A0"}){
        Classifier.Hit hit=Classifier.inspect("https://"+engine+query);
        eq(hit.flagged(),false,"search result: "+engine+query);
        eq(hit.search,false,"no automatic search restriction");
      }
    }
    for(String video:new String[]{"https://youtube.com/","https://m.youtube.com/watch?v=abc","https://youtu.be/abc","https://www.youtube-nocookie.com/embed/abc"})
      eq(Classifier.inspect(video).strong,true,"actual video site remains restricted");
    eq(n.observe(chrome,2,"youtube.com",true),null,"different window has no copied visit");
    eq(n.observe("com.brave.browser",1,"youtube.com",true),null,"different browser isolation");
    eq(n.observe(chrome,1,null,false),null,"new tab clears old visit");
    eq(n.observe(chrome,1,"youtube.com",true),null,"typing in fresh tab not blocked");
    eq(n.observe(chrome,1,"https://google.com/search?q=weather",false),"https://google.com/search?q=weather","safe submitted query");
    eq(Classifier.inspect(n.observe(chrome,1,"youtube",true)).flagged(),false,"new text does not alter safe committed query");
    System.out.println("PASS "+count+" committed navigation cases");
  }
}
""")
    src=root/"app/src/main/java/jp/personal/entertainmentguard"
    subprocess.run(["javac","-d",str(tmp),str(src/"Classifier.java"),str(src/"BrowserNavigationState.java"),str(test)],check=True)
    subprocess.run(["java","-cp",str(tmp),"BrowserNavigationTest"],check=True)
