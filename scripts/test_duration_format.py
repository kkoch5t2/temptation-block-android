#!/usr/bin/env python3
"""Check duration display boundaries without touching device settings."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="guard-duration-") as folder:
    tmp = Path(folder)
    source = tmp / "DurationFormatTest.java"
    source.write_text("""
import jp.personal.entertainmentguard.DurationFormat;
public class DurationFormatTest {
  static int count;
  static void check(long ms,String expected){
    String actual=DurationFormat.remaining(ms);
    if(!actual.equals(expected))throw new AssertionError(ms+": "+actual);
    count++;
  }
  public static void main(String[] args){
    check(-1,"0時間0分0秒");
    check(0,"0時間0分0秒");
    check(1,"0時間0分1秒");
    check(59999,"0時間1分0秒");
    check(60000,"0時間1分0秒");
    check(3599000,"0時間59分59秒");
    check(3600000,"1時間0分0秒");
    check(86400000,"1日 0時間0分0秒");
    check(90061000,"1日 1時間1分1秒");
    check(31536000000000L,"365000日 0時間0分0秒");
    check(Long.MAX_VALUE,"106751991167日 7時間12分56秒");
    if(DurationFormat.remainingFraction(1000,1000)!=1)throw new AssertionError("full bar");
    if(DurationFormat.remainingFraction(500,1000)!=0.5)throw new AssertionError("half bar");
    if(DurationFormat.remainingFraction(0,1000)!=0)throw new AssertionError("empty bar");
    if(DurationFormat.remainingFraction(-1,1000)!=0)throw new AssertionError("negative");
    if(DurationFormat.remainingFraction(100,0)!=0)throw new AssertionError("no budget");
    if(DurationFormat.remainingFraction(2000,1000)!=1)throw new AssertionError("clamp");
    if(DurationFormat.remainingPercent(1,1000)!=1)throw new AssertionError("tiny remainder");
    if(DurationFormat.remainingPercent(250,1000)!=25)throw new AssertionError("quarter");
    if(DurationFormat.remainingFraction(Long.MAX_VALUE,Long.MAX_VALUE)!=1)throw new AssertionError("large duration");
    System.out.println("PASS "+count+" duration display cases and 9 remaining balance cases");
  }
}
""")
    subprocess.run(["javac","-d",str(tmp),str(root/"app/src/main/java/jp/personal/entertainmentguard/DurationFormat.java"),str(source)],check=True)
    subprocess.run(["java","-cp",str(tmp),"DurationFormatTest"],check=True)
