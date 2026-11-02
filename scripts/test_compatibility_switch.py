#!/usr/bin/env python3
"""Payment pause ownership, recovery boundaries, activity ordering and settings preservation."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="guard-payment-") as directory:
    tmp=Path(directory)
    source=tmp/"CompatibilitySwitchTest.java"
    source.write_text("""
import jp.personal.entertainmentguard.CompatibilitySwitchPolicy;
import static jp.personal.entertainmentguard.CompatibilitySwitchPolicy.Action.*;
public class CompatibilitySwitchTest {
 static int count;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
 public static void main(String[] args){
  String pay="com.example.incompatible",other="com.example.work";
  CompatibilitySwitchPolicy p=new CompatibilitySwitchPolicy(false,java.util.Arrays.asList(pay));
  check(p.decide(true,true,true,pay,true,false,1000,-1)==PAUSE,"foreground payment pauses enabled service");
  check(p.decide(true,true,true,other,true,false,1000,-1)==NONE,"other apps cannot pause");
  check(p.decide(false,true,true,pay,true,false,1000,-1)==NONE,"feature is opt-in");
  check(p.decide(true,false,true,pay,true,false,1000,-1)==NONE,"permissions required");
  check(p.decide(true,true,false,pay,true,false,1000,-1)==NONE,"locked screen cannot pause");
  check(p.decide(true,true,true,pay,false,false,1000,-1)==NONE,"manually disabled service is never enabled");
  check(p.decide(true,true,true,pay,false,true,1001,1000)==NONE,"owned pause remains during payment");
  check(p.decide(true,true,true,other,false,true,1001,1000)==RESTORE,"leaving payment restores");
  check(p.decide(true,true,true,null,false,true,1001,1000)==RESTORE,"unknown foreground restores");
  check(p.decide(true,false,true,pay,false,true,1001,1000)==RESTORE,"permission loss restores");
  check(p.decide(true,true,false,pay,false,true,1001,1000)==RESTORE,"screen lock restores");
  check(p.decide(false,true,true,pay,false,true,1001,1000)==RESTORE,"disable restores before stopping");
  check(p.decide(true,true,true,pay,false,true,1001,-1)==RESTORE,"missing pause time restores");
  check(p.decide(true,true,true,pay,false,true,999,1000)==RESTORE,"invalid monotonic time restores");
  p=new CompatibilitySwitchPolicy(false,java.util.Arrays.asList(pay));
  long start=1000,end=start+CompatibilitySwitchPolicy.MAX_PAUSE_MS;
  check(p.decide(true,true,true,pay,false,true,end-1,start)==NONE,"pause remains before boundary");
  check(p.decide(true,true,true,pay,false,true,end,start)==RESTORE,"maximum pause boundary restores");
  check(p.decide(true,true,true,pay,true,false,end+1,-1)==NONE,"timeout cannot immediately re-pause");
  p.decide(true,true,true,other,true,false,end+2,-1);
  check(p.decide(true,true,true,pay,true,false,end+3,-1)==PAUSE,"new payment visit after exit allowed");
  p=new CompatibilitySwitchPolicy(true,java.util.Arrays.asList(pay));
  check(p.decide(true,true,true,pay,true,false,1000,-1)==NONE,"watchdog recovery cannot re-pause same session");
  p.decide(true,true,true,other,true,false,1001,-1);
  check(p.decide(true,true,true,pay,true,false,1002,-1)==PAUSE,"recovered watcher can handle next visit");
  String own="com.example.guard/com.example.guard.GuardianService";
  String shortOwn="com.example.guard/.GuardianService";
  String first="com.example.reader/.ReadService",second="com.example.assist/.AssistService";
  String original=first+":"+shortOwn+":"+second;
  check(CompatibilitySwitchPolicy.contains(original,own),"full and short component identities match");
  check(CompatibilitySwitchPolicy.editServices(original,own,false).equals(first+":"+second),"remove only our service");
  String restored=CompatibilitySwitchPolicy.editServices(first+":"+second,own,true);
  check(restored.equals(first+":"+second+":"+own),"restore retains every other service");
  check(CompatibilitySwitchPolicy.editServices(restored,own,true).equals(restored),"restore is idempotent");
  check(!CompatibilitySwitchPolicy.contains(first+":"+second,own),"others are not our service");
  check(CompatibilitySwitchPolicy.editServices(null,own,false).isEmpty(),"empty settings remain empty");
  check(CompatibilitySwitchPolicy.editServices(null,own,true).equals(own),"empty settings can restore owned pause");
  check(CompatibilitySwitchPolicy.editServices(own,own,false).isEmpty(),"single own service can pause");
  check(!CompatibilitySwitchPolicy.contains(null,own),"missing settings mean disabled");
  CompatibilitySwitchPolicy.Foreground fg=new CompatibilitySwitchPolicy.Foreground();
  fg.resume(pay,"First");fg.resume(pay,"Second");fg.pause(pay,"First");
  check(pay.equals(fg.current()),"old activity pause cannot erase newer payment activity");
  fg.pause(pay,"Second");check(fg.current()==null,"actual payment pause clears foreground");
  fg.resume(pay,"First");fg.resume(other,"Work");fg.pause(pay,"First");
  check(other.equals(fg.current()),"old payment pause cannot erase work app");
  fg.clear();check(fg.current()==null,"lock and detection failure discard stale foreground");
  String guard="com.example.guard";
  check(pay.equals(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,guard,guard+".CompatibilityLaunchActivity",guard,1000,2000,pay,java.util.Collections.singleton(pay))),"visible preparation can finish the launch");
  check(pay.equals(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,null,null,guard,1000,2000,pay,java.util.Collections.singleton(pay))),"brief activity transition has bounded grace");
  check(other.equals(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,other,"Work",guard,1000,2000,pay,java.util.Collections.singleton(pay))),"leaving for another app never inherits preparation grace");
  check(guard.equals(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,guard,guard+".MainActivity",guard,1000,2000,pay,java.util.Collections.singleton(pay))),"settings cannot inherit preparation grace");
  check(CompatibilitySwitchPolicy.foregroundDuringLaunch(false,null,null,guard,1000,2000,pay,java.util.Collections.singleton(pay))==null,"lock cancels launch grace");
  check(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,null,null,guard,2000,2000,pay,java.util.Collections.singleton(pay))==null,"grace expires at deadline");
  check(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,null,null,guard,1000,5000,pay,java.util.Collections.singleton(pay))==null,"invalid future deadline cannot extend grace");
  p=new CompatibilitySwitchPolicy(false,java.util.Arrays.asList(pay));p.waitForExit();
  check(p.decide(true,true,true,pay,true,false,1000,-1)==NONE,"blocked automatic launch cannot repeatedly interrupt PayPay");
  p.decide(true,true,true,other,true,false,1001,-1);
  check(p.decide(true,true,true,pay,true,false,1002,-1)==PAUSE,"new visit can retry preparation");
  String bank="com.example.bank",wallet="com.example.wallet";
  p=new CompatibilitySwitchPolicy(false,java.util.Arrays.asList(bank,wallet));
  check(p.decide(true,true,true,bank,true,false,1000,-1)==PAUSE,"any registered app can pause");
  check(p.decide(true,true,true,wallet,false,true,1100,1000)==NONE,"registered app to another registered app retains pause");
  check(p.decide(true,true,true,wallet,false,true,1000+CompatibilitySwitchPolicy.MAX_PAUSE_MS,1000)==RESTORE,"switching registered apps never renews maximum pause");
  p=new CompatibilitySwitchPolicy(false,java.util.Arrays.asList(bank,wallet));
  p.setTargets(java.util.Collections.singleton(bank));
  check(p.decide(true,true,true,wallet,false,true,1100,1000)==RESTORE,"removing current target restores monitoring");
  p.setTargets(java.util.Collections.emptySet());
  check(p.decide(true,true,true,bank,true,false,1200,-1)==NONE,"empty target list cannot disable monitoring");
  check(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,null,null,guard,1000,2000,wallet,java.util.Collections.singleton(bank))==null,"unregistered launch target cannot claim preparation grace");
  check(bank.equals(CompatibilitySwitchPolicy.foregroundDuringLaunch(true,guard,guard+".CompatibilityLaunchActivity",guard,1000,2000,bank,java.util.Collections.singleton(bank))),"preparation uses the selected package instead of a fixed app");
  System.out.println("PASS "+count+" compatibility switch and restoration cases");
 }
}
""")
    policy=root/"app/src/main/java/jp/personal/entertainmentguard/CompatibilitySwitchPolicy.java"
    subprocess.run(["javac","-d",str(tmp),str(policy),str(source)],check=True)
    subprocess.run(["java","-cp",str(tmp),"CompatibilitySwitchTest"],check=True)
