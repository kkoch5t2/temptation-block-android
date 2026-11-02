#!/usr/bin/env python3
"""Subscriptions must retain restriction targets without subscribing to unrelated apps."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/jp/personal/entertainmentguard"
with tempfile.TemporaryDirectory(prefix="guard-scope-") as directory:
    tmp=Path(directory)
    test=tmp/"AccessibilityScopeTest.java"
    test.write_text("""
import java.util.*;
import jp.personal.entertainmentguard.*;
public class AccessibilityScopeTest {
  static void check(boolean value,String label){
    if(!value)throw new AssertionError(label);
  }
  public static void main(String[] args){
    String own="com.example.guard",bank="com.example.wallet",blocked="com.example.video";
    Set<String> base=AccessibilityScope.packages(own,null);
    check(base.size()==3 && base.contains(own),"empty policy has a bounded subscription");
    check(base.contains("com.android.chrome") && base.contains("com.brave.browser"),"website enforcement retained");
    check(!base.contains(bank),"unselected payment app has no subscription");
    Set<String> selected=AccessibilityScope.packages(own,Arrays.asList(blocked,blocked,null,""));
    check(selected.contains(blocked),"explicit app retained");
    check(selected.size()==4,"no duplicate or wildcard subscription");
    check(!selected.contains(bank),"unrelated app still excluded");
    Set<String> shared=AccessibilityScope.packages(own,Arrays.asList(AppSurfaceRules.GEMINI));
    check(shared.contains(AppSurfaceRules.GEMINI) && shared.contains(AppSurfaceRules.GOOGLE),"shared Gemini host retained");
    check(!AccessibilityScope.packages(own,Collections.emptyList()).contains(AppSurfaceRules.GOOGLE),"removed target removes shared host");
    Set<String> newApps=AccessibilityScope.packages(own,Arrays.asList(blocked,"com.example.newapp"));
    check(newApps.contains("com.example.newapp"),"new installation blocker retained");
    check(AccessibilityScope.packages(own,Arrays.asList(bank)).contains(bank),"explicitly selected payment apps are not exempted");
    check(!base.contains("") && base.stream().noneMatch(Objects::isNull),"never express all packages through an empty entry");
    try{
      AccessibilityScope.packages("",null);
      throw new AssertionError("missing identity cannot produce an empty subscription");
    }catch(IllegalArgumentException expected){}
    System.out.println("PASS accessibility scope: bounded subscriptions, selected apps, shared hosts and new installs");
  }
}
""")
    subprocess.run(["javac","-d",str(tmp),str(src/"AppSurfaceRules.java"),str(src/"AccessibilityScope.java"),str(test)],check=True)
    subprocess.run(["java","-cp",str(tmp),"AccessibilityScopeTest"],check=True)
