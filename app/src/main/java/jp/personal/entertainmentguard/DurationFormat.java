package jp.personal.entertainmentguard;

/** Display-only duration formatting; does not modify restriction deadlines. */
public final class DurationFormat {
  private DurationFormat(){}
  public static double remainingFraction(long remaining,long total){
    if(total<=0||remaining<=0)return 0;
    return Math.min(1,remaining/(double)total);
  }
  public static int remainingPercent(long remaining,long total){
    return (int)Math.ceil(remainingFraction(remaining,total)*100);
  }
  public static String remaining(long milliseconds){
    long ms=Math.max(0,milliseconds);
    long seconds=ms/1000+(ms%1000==0?0:1);
    long days=seconds/86400;
    return (days>0?days+"日 ":"")+((seconds%86400)/3600)+"時間"+
      ((seconds%3600)/60)+"分"+(seconds%60)+"秒";
  }
}
