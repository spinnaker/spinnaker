package com.netflix.rocket.semver.shaded;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetflixVersionComparator implements Comparator<String> {
  private static final String PRERELEASE_SPLIT = "~";
  private static final String DOT_SEPARATOR = "\\.";
  private static final String PRERELEASE_SEPARATOR = "[.+]";

  // separate out the upstream version (everything before the last hyphen) from the debian version
  // (everything after the last hyphen)
  private static final Pattern upstreamVersionDebianVersionPattern = Pattern.compile("(.*)-(.*)");
  private static final Pattern netflixDebianVersionPattern = Pattern.compile("h(\\d+)\\.[0-9a-f]+");

  @Override
  public int compare(String s0, String s1) {
    if (s0.equals(s1)) {
      return 0;
    }

    Matcher m0 = upstreamVersionDebianVersionPattern.matcher(s0);
    Matcher m1 = upstreamVersionDebianVersionPattern.matcher(s1);

    if (m0.matches() && m1.matches()) {
      int versionCompare = compareVersionPart(m0.group(1), m1.group(1));
      if (versionCompare == 0) {
        return compareBuildPart(m0.group(2), m1.group(2));
      }
      return versionCompare;
    }

    return compareVersionPart(s0, s1);
  }

  private int compareVersionPart(String s0, String s1) {
    String[] mainPrerelease0 = s0.split(PRERELEASE_SPLIT);
    String[] mainPrerelease1 = s1.split(PRERELEASE_SPLIT);
    int mainComparison = compareMainPart(mainPrerelease0[0], mainPrerelease1[0]);
    if (mainComparison == 0) {
      if (mainPrerelease0.length == mainPrerelease1.length && mainPrerelease0.length == 2) {
        String[] prerelease0 = mainPrerelease0[1].split(PRERELEASE_SEPARATOR);
        String[] prerelease1 = mainPrerelease1[1].split(PRERELEASE_SEPARATOR);
        for (int i = 0; i < prerelease0.length; i++) {
          if (i >= prerelease1.length) {
            if (prerelease0[i].equals("dev")) {
              return -1;
            } else {
              return 1;
            }
          }
          boolean isNumeric0 = isNumeric(prerelease0[i]);
          final boolean isNumeric1 = isNumeric(prerelease1[i]);
          if (isNumeric0 && isNumeric1) {
            int diff = Integer.parseInt(prerelease0[i]) - Integer.parseInt(prerelease1[i]);
            if (diff != 0) {
              return diff;
            }
          }
          if (!isNumeric0 && !isNumeric1) {
            int diff = prerelease0[i].compareTo(prerelease1[i]);
            if (diff != 0) {
              return diff;
            }
          }
        }
        if (prerelease1.length > prerelease0.length) {
          if (prerelease1[prerelease0.length].equals("dev")) {
            return 1;
          } else {
            return -1;
          }
        }
      }
      return mainPrerelease1.length - mainPrerelease0.length;
    }
    return mainComparison;
  }

  /**
   * Compare the main parts of debian versions given 1.2.3~rc.1-h1.abcdef, 1.2.3 is the main part
   *
   * @param s0 String
   * @param s1 String
   * @return negative number if s0 less than s1, 0 if s0 equal to s1, positive number if s0 greater
   *     than s1
   */
  private int compareMainPart(String s0, String s1) {
    String[] mainParts0 = s0.split(DOT_SEPARATOR);
    String[] mainParts1 = s1.split(DOT_SEPARATOR);
    for (int i = 0; i < mainParts0.length; i++) {
      if (i >= mainParts1.length) {
        return 1;
      }
      int diff = Integer.parseInt(mainParts0[i]) - Integer.parseInt(mainParts1[i]);
      if (diff != 0) {
        return diff;
      }
    }
    return mainParts0.length - mainParts1.length;
  }

  private static boolean isNumeric(String strNum) {
    try {
      Integer.parseInt(strNum);
    } catch (NumberFormatException | NullPointerException nfe) {
      return false;
    }
    return true;
  }

  private int compareBuildPart(String s0, String s1) {
    Matcher m0 = netflixDebianVersionPattern.matcher(s0);
    Matcher m1 = netflixDebianVersionPattern.matcher(s1);

    if (m0.matches() && m1.matches()) {
      return Integer.parseInt(m0.group(1)) - Integer.parseInt(m1.group(1));
    }

    return s0.compareTo(s1);
  }
}
