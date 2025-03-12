/*
 * Copyright 2020 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** This class handles resolving a collection of {@link ExpectedArtifact} instances. */
@NonnullByDefault
public final class ArtifactResolver {
  private final ImmutableList<Artifact> currentArtifacts;
  private final Supplier<ImmutableList<Artifact>> priorArtifacts;
  private final boolean requireUniqueMatches;

  private ArtifactResolver(
      Iterable<Artifact> currentArtifacts,
      Supplier<? extends Iterable<Artifact>> priorArtifacts,
      boolean requireUniqueMatches) {
    this.currentArtifacts = ImmutableList.copyOf(currentArtifacts);
    this.priorArtifacts =
        Suppliers.memoize(Suppliers.compose(ImmutableList::copyOf, priorArtifacts::get));
    this.requireUniqueMatches = requireUniqueMatches;
  }

  /**
   * Returns an instance of an {@link ArtifactResolver} that resolves against the supplied current
   * artifacts, and prior artifacts.
   *
   * <p>The {@link Supplier} for prior artifacts is memoized for the lifetime of this instance and
   * will be called at most once, the first time prior artifacts are needed by this instance.
   *
   * @param currentArtifacts The current artifacts to consider when resolving expected artifacts
   * @param priorArtifacts A supplier that when invoked returns an {@link Iterable} of the prior
   *     artifacts to consider when resolving artifacts
   * @param requireUniqueMatches Whether the resolver should require that each expected artifact
   *     matches at most one artifact.
   * @return An instance of {@link ArtifactResolver}
   */
  public static ArtifactResolver getInstance(
      Iterable<Artifact> currentArtifacts,
      Supplier<? extends Iterable<Artifact>> priorArtifacts,
      boolean requireUniqueMatches) {
    return new ArtifactResolver(currentArtifacts, priorArtifacts, requireUniqueMatches);
  }

  /**
   * Returns an instance of an {@link ArtifactResolver} that resolves against the supplied current
   * artifacts.
   *
   * @param currentArtifacts The current artifacts to consider when resolving expected artifacts
   * @param requireUniqueMatches Whether the resolver should require that each expected artifact
   *     matches at most one artifact.
   * @return An instance of {@link ArtifactResolver}
   */
  public static ArtifactResolver getInstance(
      Iterable<Artifact> currentArtifacts, boolean requireUniqueMatches) {
    return new ArtifactResolver(currentArtifacts, ImmutableList::of, requireUniqueMatches);
  }

  /**
   * Resolves the input expected artifacts, returning the result of the resolution as a {@link
   * ResolveResult}.
   *
   * <p>Resolving an expected artifact means finding an artifact that matches that expected
   * artifact. To find a matching artifact, the following are considered in order:
   *
   * <ul>
   *   <li>The {@link ArtifactResolver}'s current artifacts
   *   <li>If the expected artifact has {@link ExpectedArtifact#isUsePriorArtifact()} true, the
   *       {@link ArtifactResolver}'s prior artifacts
   *   <li>If the expected artifact has {@link ExpectedArtifact#isUseDefaultArtifact()} true, the
   *       {@link ExpectedArtifact}'s default artifact
   * </ul>
   *
   * In order to determine whether an {@link ExpectedArtifact} matches an {@link Artifact}, the
   * expected artifact's {@link ExpectedArtifact#matches(Artifact)} method is used.
   *
   * <p>If an expected artifact does not match any artifacts, an {@link InvalidRequestException} is
   * thrown.
   *
   * <p>If {@link #requireUniqueMatches} is true, and an expected artifact matches more than one
   * artifact in any of the above steps, an {@link InvalidRequestException} is thrown.
   *
   * @param expectedArtifacts The expected artifacts to resolve
   * @return The result of the artifact resolution
   */
  public ResolveResult resolveExpectedArtifacts(Iterable<ExpectedArtifact> expectedArtifacts) {
    // We keep track of resolved artifacts in an ImmutableSet.Builder so that duplicates are not
    // added (in the case that an artifact matches more than one expected artifact). An ImmutableSet
    // iterates in the order elements were added (including via the builder), so calling asList()
    // on the resulting set will return the resolved artifacts in the order they were resolved.
    ImmutableSet.Builder<Artifact> resolvedArtifacts = ImmutableSet.builder();
    ImmutableList.Builder<ExpectedArtifact> boundExpectedArtifacts = ImmutableList.builder();

    for (ExpectedArtifact expectedArtifact : expectedArtifacts) {
      Artifact resolved =
          resolveSingleArtifact(expectedArtifact)
              .orElseThrow(
                  () ->
                      new InvalidRequestException(
                          format(
                              "Unmatched expected artifact %s could not be resolved.",
                              expectedArtifact)));
      resolvedArtifacts.add(resolved);
      boundExpectedArtifacts.add(expectedArtifact.toBuilder().boundArtifact(resolved).build());
    }
    return new ResolveResult(resolvedArtifacts.build().asList(), boundExpectedArtifacts.build());
  }

  private Optional<Artifact> resolveSingleArtifact(ExpectedArtifact expectedArtifact) {
    Optional<Artifact> resolved = matchSingleArtifact(expectedArtifact, currentArtifacts);

    if (!resolved.isPresent() && expectedArtifact.isUsePriorArtifact()) {
      resolved = matchSingleArtifact(expectedArtifact, priorArtifacts.get());
    }

    if (!resolved.isPresent() && expectedArtifact.isUseDefaultArtifact()) {
      resolved = Optional.ofNullable(expectedArtifact.getDefaultArtifact());
    }

    return resolved;
  }

  private Optional<Artifact> matchSingleArtifact(
      ExpectedArtifact expectedArtifact, ImmutableList<Artifact> possibleArtifacts) {
    ImmutableList<Artifact> matches =
        possibleArtifacts.stream().filter(expectedArtifact::matches).collect(toImmutableList());

    if (matches.isEmpty()) {
      return Optional.empty();
    }

    if (matches.size() > 1 && requireUniqueMatches) {
      throw new InvalidRequestException(
          "Expected artifact " + expectedArtifact + " matches multiple artifacts " + matches);
    }

    return Optional.of(matches.get(0));
  }

  /**
   * This class represents the result of calling {@link
   * ArtifactResolver#resolveExpectedArtifacts(Iterable)}.
   */
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Getter
  public static final class ResolveResult {
    /**
     * This field contains the resolved artifacts; these are the artifacts that matched any input
     * expected artifact. If an artifact matches more than one expected artifact, it is returned
     * only once.
     */
    private final ImmutableList<Artifact> resolvedArtifacts;
    /**
     * This field contains the resolved expected artifacts; each resolved expected artifact is a
     * copy of the input expected artifact with its {@link ExpectedArtifact#getBoundArtifact()} set
     * to the artifact that it matched during resolution.
     */
    private final ImmutableList<ExpectedArtifact> resolvedExpectedArtifacts;
  }
}
