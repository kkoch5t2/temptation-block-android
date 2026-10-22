package jp.personal.entertainmentguard;
public class ClassifierTest {
  static void check(boolean ok,String title){if(!ok)throw new AssertionError(title);System.out.println("PASS "+title);}
  public static void main(String[] args){
    Classifier.Hit a=Classifier.inspect("https://www.piccoma.com/web/");
    check(a.flagged()&&a.strong&&a.category.equals("漫画"),"Manga domain");
    Classifier.Hit b=Classifier.inspect("https://www.youtube.com/watch?v=123");
    check(b.flagged()&&b.strong,"YouTube immediate block");
    check(Classifier.inspect("https://youtu.be/abc").strong,"Short video URL");
    check(!Classifier.inspect("https://youtube.com.evil.org/").flagged(),"No suffix spoofing");
    check(!Classifier.inspect("https://google.com/search?q=TOEIC%20%E6%BC%AB%E7%94%BB").flagged(),"Search is not a site visit");
    Classifier.Hit c=Classifier.inspect("https://www.google.com/search?q=%E6%BC%AB%E7%94%BB");
    check(!c.flagged()&&!c.search,"Search result does not auto-block");
    check(!Classifier.inspect("https://www.google.com/search?q=TOEIC%20%E8%8B%B1%E8%AA%9E%E5%AD%A6%E7%BF%92").flagged(),"TOEIC exempt");
    check(!Classifier.inspect("https://github.com/example/repo").flagged(),"Development exempt");
    check(!Classifier.inspect("https://google.com/search?q=game").flagged(),"Game query does not restrict");
    check(!Classifier.inspect("https://google.com/search?q=manga").flagged(),"Legacy imported query does not restrict");
    check(Classifier.inspect("https://www.dazn.com/live").strong,"Sports streaming");
    check(Classifier.inspect("https://www.mlb.com").flagged()&&!Classifier.inspect("https://www.mlb.com").strong,"MLB read-only warning");
    check(Classifier.host("youtube.com/watch?v=1").equals("youtube.com"),"Bare host normalization");
  }
}