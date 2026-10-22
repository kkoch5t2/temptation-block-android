package jp.personal.entertainmentguard;
public class BlockedTabMatcherTest{
  private static void check(boolean p,String name){if(!p)throw new AssertionError(name);System.out.println("PASS "+name);}
  public static void main(String[] a){
    check(BlockedTabMatcher.score("https://www.youtube.com/","youtube.com","YouTube")==2,"Youtube homepage match");
    check(BlockedTabMatcher.score("https://www.youtube.com/","youtube.com","Rick Astley - YouTube")==1,"Youtube video less preferred");
    check(BlockedTabMatcher.score("https://www.youtube.com/watch?v=abc","youtube.com","Rick Astley - YouTube")==2,"Youtube video preferred");
    check(BlockedTabMatcher.score("https://www.youtube.com/watch?v=abc","youtube.com","YouTube")==1,"Youtube homepage less preferred");
    check(BlockedTabMatcher.score("https://www.youtube.com","youtube.com","Python documentation")==-1,"Unrelated tab never removed");
    check(BlockedTabMatcher.score("https://piccoma.com/","piccoma.com","ピッコマ")==2,"Manga tab match");
    check(BlockedTabMatcher.score("https://example.com/","example.com","Example Domain")==-1,"Unknown domain cannot close tab");
    check(BlockedTabMatcher.score("https://youtube.com/","youtube.com",null)==-1,"Null never match");
  }
}