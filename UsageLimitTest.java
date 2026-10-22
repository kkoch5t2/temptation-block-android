package jp.personal.entertainmentguard;
public final class UsageLimitTest {
  private static int passed=0;
  private static void ok(boolean truth,String label){
    if(!truth)throw new AssertionError(label);
    passed++;System.out.println("PASS "+label);
  }
  public static void main(String[] args){
    ok(UsageLimit.validMinutes(1)&&UsageLimit.validMinutes(10)&&UsageLimit.validMinutes(60),"Minute options available");
    ok(!UsageLimit.validMinutes(0)&&!UsageLimit.validMinutes(2),"Unknown options rejected");
    long spent=0;
    for(int i=0;i<599;i++)spent=UsageLimit.accrue(spent,1000,600000);
    ok(spent==599000&&!UsageLimit.isExhausted(600000,spent),"10min below cap allowed");
    spent=UsageLimit.accrue(spent,1000,600000);
    ok(spent==600000&&UsageLimit.isExhausted(600000,spent),"At ten minutes deny");
    ok(UsageLimit.remainingSeconds(600000,599001)==1,"Round up remaining");
    ok("1時間0分0秒".equals(UsageLimit.formatRemaining(3600000,0)),"Hour display");
    ok("0時間9分59秒".equals(UsageLimit.formatRemaining(600000,1001)),"Minutes and seconds display");
    ok("0時間0分1秒".equals(UsageLimit.formatRemaining(600000,599001)),"Round up display");
    ok(UsageLimit.accrue(599500,10000,600000)==600000,"Clamp excess");
    ok(UsageLimit.accrue(5000,0,600000)==5000,"Do not accrue when closed");
    ok(UsageLimit.accrue(0,40000,600000)==2000,"Clamp background gap");
    long deadline=CooldownPolicy.deadline(100000);
    ok(deadline==130000,"Penalty lasts 30s");
    ok(CooldownPolicy.waiting(100000,deadline),"Penalty starts active");
    ok(CooldownPolicy.remainingSeconds(112000,deadline)==18,"Countdown 18s remaining");
    ok(!CooldownPolicy.waiting(130000,deadline),"Penalty display deadline ends");
    ok(CooldownPolicy.remainingSeconds(130000,deadline)==0,"Countdown zero");
    System.out.println(passed+" policy checks passed");
  }
}