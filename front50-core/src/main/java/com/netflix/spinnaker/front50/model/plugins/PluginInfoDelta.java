package com.netflix.spinnaker.front50.model.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PluginInfoDelta {
  public final List<PluginInfo.Release> removedReleases;
  public final List<PluginInfo.Release> addedReleases;
  public final @Nullable PluginInfo.Release oldPreferredRelease;
  public final @Nullable PluginInfo.Release newPreferredRelease;

  public PluginInfoDelta(@Nonnull PluginInfo newPluginInfo, @Nullable PluginInfo oldPluginInfo) {
    BiPredicate<PluginInfo.Release, PluginInfo.Release> isSameRelease =
        (release1, release2) -> release1.getVersion().equals(release2.getVersion());

    List<PluginInfo.Release> oldReleases =
        Optional.ofNullable(oldPluginInfo).map(PluginInfo::getReleases).orElse(new ArrayList<>());
    List<PluginInfo.Release> newReleases = newPluginInfo.getReleases();

    removedReleases = firstWithoutSecond(oldReleases, newReleases, isSameRelease);
    addedReleases = firstWithoutSecond(newReleases, oldReleases, isSameRelease);

    oldPreferredRelease =
        oldReleases.stream().filter(PluginInfo.Release::isPreferred).findFirst().orElse(null);
    newPreferredRelease =
        newReleases.stream().filter(PluginInfo.Release::isPreferred).findFirst().orElse(null);
  }

  private <T> List<T> firstWithoutSecond(List<T> first, List<T> second, BiPredicate<T, T> isSame) {
    return first.stream()
        .filter(it -> second.stream().noneMatch(it2 -> isSame.test(it, it2)))
        .collect(Collectors.toList());
  }
}
