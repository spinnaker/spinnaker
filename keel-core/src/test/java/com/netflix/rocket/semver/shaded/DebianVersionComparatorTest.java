package com.netflix.rocket.semver.shaded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DebianVersionComparatorTest {
  private static NetflixVersionComparator comparator = new NetflixVersionComparator();

  @ParameterizedTest(name = "[{index}]: {0} < {1}")
  @MethodSource("compareVersionsLessThan")
  void shouldCompareVersionsLessThan(String s0, String s1) {
    assertThat(comparator.compare(s0, s1)).isLessThan(0);
  }

  private static Stream<Arguments> compareVersionsLessThan() {
    return Stream.of(
        of("1", "2"),
        of("5", "34"),
        of("1.2", "1.3"),
        of("1.2", "1.10"),
        of("1.2", "2.0"),
        of("1.0.0", "1.0.1"),
        of("1.9.100", "1.21.10"),
        of("11.34.0", "12.1.4"),
        of("3.5", "3.5.0"),
        of("1.0.0~rc.1", "1.0.0"),
        of("1.0.0~rc.5", "1.0.0~rc.12"),
        of("1.0.0~rc.1", "1.1.0~rc.1"),
        of("1.0.0~dev.1+abcdef", "1.0.0~rc.4"),
        of("1.0.0~dev.1+abcdef", "1.0.0~dev.2+123abc"),
        of("1.0.0~dev.1+abcdef", "1.0.0~dev.1+bcdef0"),
        of("1.0.0~rc.1.dev.2+abcdef", "1.0.0~rc.1"),
        of("1.0.0-h2.abcdef", "1.0.0-h11.123456"),
        of("1.0.0-LOCAL", "1.0.0-h1.abcdef"));
  }

  @ParameterizedTest(name = "[{index}]: {0} == {1}")
  @MethodSource("compareVersionsEqualTo")
  void shouldCompareVersionsEqualTo(String s0, String s1) {
    assertThat(comparator.compare(s0, s1)).isEqualTo(0);
  }

  private static Stream<Arguments> compareVersionsEqualTo() {
    return Stream.of(
        of("2", "2"),
        of("13", "13"),
        of("1.2.3", "1.2.3"),
        of("42.3", "42.3"),
        of("1.0.0.0.0", "1.0.0.0.0"),
        of("1.2.3~rc.2", "1.2.3~rc.2"),
        of("1.0.0~rc.1.dev.2+abcdef", "1.0.0~rc.1.dev.2+abcdef"),
        of("1.0.0~dev.3+abcdef", "1.0.0~dev.3+abcdef"));
  }

  @ParameterizedTest(name = "[{index}]: {0} > {1}")
  @MethodSource("compareVersionsGreaterThan")
  void shouldCompareVersionsGreaterThan(String s0, String s1) {
    assertThat(comparator.compare(s0, s1)).isGreaterThan(0);
  }

  private static Stream<Arguments> compareVersionsGreaterThan() {
    return Stream.of(
        of("2", "1"),
        of("23", "12"),
        of("4.2.1", "4.2.0"),
        of("10.0.0", "1.0.0"),
        of("10.0.0", "9.325.24"),
        of("1.0.0", "1.0"),
        of("1.2.2", "1.2.2~rc.3"),
        of("1.0.0~rc.2", "1.0.0~rc.1"),
        of("1.0.0~rc.12", "1.0.0~rc.8"),
        of("1.0.1~rc.1", "1.0.0~rc.352"),
        of("1.0.0~dev.2+abcdef", "1.0.0~dev.1+abcdef"),
        of("1.0.0~rc.1", "1.0.0~dev.1+abcdef"),
        of("1.0.0~dev.2+bcdefa", "1.0.0~dev.2+abcdef"),
        of("1.0.0~rc.1", "1.0.0~rc.1.dev.1+abcdef"),
        of("1.0.0-h23.123abc", "1.0.0-h12.34ad35"),
        of("1.0.0-h2.123456", "1.0.0-LOCAL"));
  }

  @Test
  void shouldSortSingleNumbersCorrectly() {
    // given
    List<String> versions = Stream.of("1", "12", "6", "3").collect(Collectors.toList());

    // when
    List<String> sorted = versions.stream().sorted(comparator).collect(Collectors.toList());

    // then
    assertThat(sorted).containsExactly("1", "3", "6", "12");
  }

  @Test
  void shouldSort3DigitSemverCorrectly() {
    // given
    List<String> versions =
        Stream.of("1.12.5", "1.0.0", "0.15.0", "1.5.1", "0.0.1").collect(Collectors.toList());

    // when
    List<String> sorted = versions.stream().sorted(comparator).collect(Collectors.toList());

    // then
    assertThat(sorted).containsExactly("0.0.1", "0.15.0", "1.0.0", "1.5.1", "1.12.5");
  }

  @Test
  void shouldSortNetflixStyleDebianVersions() {
    // given
    List<String> versions =
        Stream.of(
                "1.2.5-h21.abcdef",
                "1.2.5-h2.123456",
                "0.1.0-h1.abcdef",
                "3.5.7-h32.abcdef",
                "3.5.7~rc.23-h23.abcdef",
                "1.12.1~dev.3+abcdef-h56.abcdef",
                "325.3-h4.abcdef",
                "12.3.235~rc.1.dev.32+abcdef-h132.abcdef",
                "12.3.235~rc.1.dev.4+abcdef-h32.abcdef",
                "12.3.235~rc.1-h30.abcdef")
            .collect(Collectors.toList());

    // when
    List<String> sorted = versions.stream().sorted(comparator).collect(Collectors.toList());

    // then
    assertThat(sorted)
        .containsExactly(
            "0.1.0-h1.abcdef",
            "1.2.5-h2.123456",
            "1.2.5-h21.abcdef",
            "1.12.1~dev.3+abcdef-h56.abcdef",
            "3.5.7~rc.23-h23.abcdef",
            "3.5.7-h32.abcdef",
            "12.3.235~rc.1.dev.4+abcdef-h32.abcdef",
            "12.3.235~rc.1.dev.32+abcdef-h132.abcdef",
            "12.3.235~rc.1-h30.abcdef",
            "325.3-h4.abcdef");
  }
}
