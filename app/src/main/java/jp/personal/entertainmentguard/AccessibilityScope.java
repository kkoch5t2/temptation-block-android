package jp.personal.entertainmentguard;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

/** Event subscriptions contain only supported browsers and actual restriction targets. */
public final class AccessibilityScope {
  private AccessibilityScope(){}
  public static SortedSet<String> packages(String own,Collection<String> targets){
    if(own==null||own.isEmpty())throw new IllegalArgumentException("Missing service package");
    SortedSet<String> result=new TreeSet<>();
    result.add(own);
    result.add("com.android.chrome");
    result.add("com.brave.browser");
    if(targets!=null)for(String pkg:targets){
      if(pkg==null||pkg.isEmpty())continue;
      result.add(pkg);
      // Gemini can display its UI inside the Google app's shared package.
      if(AppSurfaceRules.GEMINI.equals(pkg))result.add(AppSurfaceRules.GOOGLE);
    }
    return result;
  }
}
