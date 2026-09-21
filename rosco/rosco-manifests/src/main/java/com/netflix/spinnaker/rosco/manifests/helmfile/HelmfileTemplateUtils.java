/*
 * Copyright 2023 Grab Holdings, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.rosco.manifests.helmfile;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.HelmBakeTemplateUtils;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmfileConfigurationProperties;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HelmfileTemplateUtils extends HelmBakeTemplateUtils<HelmfileBakeManifestRequest> {

  // Matches helm's --post-renderer / --post-renderer-args flags, however helmfile passes them
  // along (a bare "--post-renderer", "--post-renderer=/some/script", or a separate "=value"
  // form), wherever they appear in a helmDefaults/release "args" list.
  private static final Pattern POST_RENDERER_FLAG_PATTERN =
      Pattern.compile("^--post-renderer(-args)?(=.*)?$");

  private static final String HOOKS_KEY = "hooks";
  private static final String POST_RENDERERS_KEY = "postRenderers";
  private static final String HELM_DEFAULTS_KEY = "helmDefaults";
  private static final String ARGS_KEY = "args";
  private static final String RELEASES_KEY = "releases";
  private static final String BASES_KEY = "bases";
  private static final String HELMFILES_KEY = "helmfiles";
  private static final String HELMFILES_PATH_KEY = "path";
  private static final List<String> HELMFILE_FILE_NAMES = List.of("helmfile.yaml", "helmfile.yml");

  private final RoscoHelmfileConfigurationProperties helmfileConfigurationProperties;
  private final RoscoHelmConfigurationProperties helmConfigurationProperties =
      new RoscoHelmConfigurationProperties();
  private final YamlHelper yamlHelper;

  public HelmfileTemplateUtils(
      ArtifactDownloader artifactDownloader,
      Optional<ArtifactStore> artifactStore,
      ArtifactStoreConfigurationProperties artifactStoreConfig,
      RoscoHelmfileConfigurationProperties helmfileConfigurationProperties,
      YamlHelper yamlHelper) {
    super(artifactDownloader, artifactStore, artifactStoreConfig.getHelm());
    this.helmfileConfigurationProperties = helmfileConfigurationProperties;
    this.yamlHelper = yamlHelper;
  }

  public BakeRecipe buildBakeRecipe(
      BakeManifestEnvironment env, HelmfileBakeManifestRequest request) throws IOException {
    Path helmfileFilePath;

    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }

    log.info("helmfileFilePath: '{}'", request.getHelmfileFilePath());
    helmfileFilePath =
        getHelmTypePathFromArtifact(env, inputArtifacts, request.getHelmfileFilePath());

    log.info("path to helmfile: {}", helmfileFilePath);

    if (!helmfileConfigurationProperties.isAllowHooksAndPostRenderers()) {
      rejectHooksAndPostRenderers(helmfileFilePath);
    }

    return buildCommand(request, getValuePaths(inputArtifacts, env), helmfileFilePath);
  }

  public String fetchFailureMessage(String description, Exception e) {
    return "Failed to fetch helmfile " + description + ": " + e.getMessage();
  }

  public String getHelmExecutableForRequest(HelmfileBakeManifestRequest request) {
    return helmConfigurationProperties.getV3ExecutablePath();
  }

  public BakeRecipe buildCommand(
      HelmfileBakeManifestRequest request, List<Path> valuePaths, Path helmfileFilePath) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    List<String> command = new ArrayList<>();
    String executable = helmfileConfigurationProperties.getExecutablePath();

    command.add(executable);
    command.add("template");
    command.add("--file");
    command.add(helmfileFilePath.toString());

    command.add("--helm-binary");
    command.add(getHelmExecutableForRequest(null));

    String environment = request.getEnvironment();
    if (environment != null && !environment.isEmpty()) {
      command.add("--environment");
      command.add(environment);
    }

    String namespace = request.getNamespace();
    if (namespace != null && !namespace.isEmpty()) {
      command.add("--namespace");
      command.add(namespace);
    }

    if (request.isIncludeCRDs()) {
      command.add("--include-crds");
    }

    Map<String, Object> overrides = request.getOverrides();
    if (overrides != null && !overrides.isEmpty()) {
      List<String> overrideList = buildOverrideList(overrides);
      command.add("--set");
      command.add(String.join(",", overrideList));
    }

    if (!valuePaths.isEmpty()) {
      valuePaths.forEach(
          path -> {
            command.add("--values");
            command.add(path.toString());
          });
    }

    result.setCommand(command);

    if (!helmfileConfigurationProperties.isAllowHooksAndPostRenderers()) {
      // hooks:/postRenderers: are guarded above by static YAML inspection, but helmfile's
      // exec/envExec/readFile/readDir/readDirEntries template functions - and its fetching of
      // remote bases:/helmfiles:/values:/chart repos - are neither expressible as a YAML key
      // rosco can look for nor blocked by that guard. These two env vars are helmfile's own
      // opt-in flags for disabling exactly those features; set them on the subprocess unless the
      // operator has already opted this helmfile source into full trust.
      result.setEnv(
          Map.of(
              "HELMFILE_DISABLE_HOOKS", "true",
              "HELMFILE_DISABLE_INSECURE_FEATURES", "true"));
    }

    return result;
  }

  /**
   * Helmfile's `hooks:` (events like `prepare`/`cleanup` fire even for the read-only `template`
   * command) and `postRenderers:` (equivalently, `helmDefaults.args`/per-release `args` containing
   * `--post-renderer`/`--post-renderer-args`) both cause helmfile to run an arbitrary local command
   * as part of baking. Since helmfileFilePath is downloaded from an input artifact that may be less
   * trusted than the pipeline itself, refuse to bake it if it declares either feature, so a hostile
   * helmfile.yaml can't get code execution on the rosco host.
   *
   * <p>This walks the entry file (or, if helmfileFilePath is a directory, the helmfile.yaml/
   * helmfile.yml/helmfile.d it resolves to) plus any locally-referenced `bases:`/`helmfiles:`.
   * Content this guard can't fully resolve and inspect - a remote or templated `bases:`/
   * `helmfiles:` reference, or a file that fails to parse as YAML (e.g. nested past SnakeYAML's
   * default depth limit, or containing unresolved Go template control-flow syntax) - is rejected
   * outright rather than skipped, since rosco can't vouch for content it never examined and
   * helmfile's own parser has no equivalent limits.
   */
  private void rejectHooksAndPostRenderers(Path helmfileFilePath) {
    for (Path yamlFile : findHelmfileYamlFiles(helmfileFilePath)) {
      validateHelmfileYamlFile(yamlFile, new HashSet<>());
    }
  }

  private List<Path> findHelmfileYamlFiles(Path path) {
    if (!Files.isDirectory(path)) {
      return List.of(path);
    }

    for (String candidateName : HELMFILE_FILE_NAMES) {
      Path candidate = path.resolve(candidateName);
      if (Files.isRegularFile(candidate)) {
        return List.of(candidate);
      }
    }

    // helmfile also accepts a "helmfile.d" directory of fragments, which it merges together.
    Path helmfileD = path.resolve("helmfile.d");
    if (Files.isDirectory(helmfileD)) {
      try (Stream<Path> children = Files.list(helmfileD)) {
        return children
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
            .sorted(Comparator.comparing(Path::toString))
            .collect(Collectors.toList());
      } catch (IOException e) {
        log.debug(
            "Unable to list {} while checking for helmfile hooks/postRenderers", helmfileD, e);
      }
    }

    return List.of();
  }

  @SuppressWarnings("unchecked")
  private void validateHelmfileYamlFile(Path file, Set<Path> visited) {
    Path real;
    try {
      real = file.toRealPath();
    } catch (IOException e) {
      // Nothing on disk to validate (e.g. a test double, or a path helmfile itself will
      // ultimately fail to find); let helmfile report that.
      return;
    }
    if (!visited.add(real)) {
      return;
    }

    Map<String, Object> doc;
    try (Reader reader = Files.newBufferedReader(file)) {
      Object loaded = yamlHelper.newSafeConstructorYaml().load(reader);
      if (!(loaded instanceof Map)) {
        return;
      }
      doc = (Map<String, Object>) loaded;
    } catch (IOException | RuntimeException e) {
      // This guard can only vouch for content it could fully parse and inspect. Content that
      // fails to parse here - whether genuinely malformed, nested past SnakeYAML's default
      // 50-level limit, or containing Go template control-flow syntax (e.g. "{{- if }}") that
      // isn't valid YAML until helmfile's own templating pass resolves it - could just as easily
      // be hiding a hooks/postRenderers declaration helmfile's own (unlimited-depth) parser would
      // still execute. Refuse to bake it rather than silently skipping validation.
      throw hookRejection(file, "content that rosco could not fully parse (" + e.toString() + ")");
    }

    checkForHooksAndPostRenderers(doc, file);

    Object releases = doc.get(RELEASES_KEY);
    if (releases instanceof List) {
      for (Object release : (List<?>) releases) {
        if (release instanceof Map) {
          checkForHooksAndPostRenderers((Map<String, Object>) release, file);
        }
      }
    }

    for (String refBase : resolveLocalReferences(doc.get(BASES_KEY), file)) {
      validateHelmfileYamlFile(Path.of(refBase), visited);
    }
    for (String refHelmfile : resolveLocalReferences(doc.get(HELMFILES_KEY), file)) {
      validateHelmfileYamlFile(Path.of(refHelmfile), visited);
    }
  }

  /**
   * Resolves the local-file entries of a {@code bases:} or {@code helmfiles:} list against {@code
   * file}'s parent directory, rejecting the bake outright if any entry is remote or templated
   * rather than silently skipping it - rosco has no way to inspect content it never fetches, so it
   * can't vouch for a helmfile that references such content unless the operator has explicitly
   * opted in via {@code helmfile.allow-hooks-and-post-renderers}.
   *
   * <p>{@code helmfiles:} entries may be a bare string path or a map with a {@code path} key;
   * {@code bases:} entries are always bare strings.
   */
  @SuppressWarnings("unchecked")
  private List<String> resolveLocalReferences(Object refsValue, Path file) {
    if (!(refsValue instanceof List)) {
      return List.of();
    }
    List<String> resolved = new ArrayList<>();
    for (Object ref : (List<?>) refsValue) {
      String refPath;
      if (ref instanceof String) {
        refPath = (String) ref;
      } else if (ref instanceof Map) {
        Object pathValue = ((Map<String, Object>) ref).get(HELMFILES_PATH_KEY);
        if (!(pathValue instanceof String)) {
          continue;
        }
        refPath = (String) pathValue;
      } else {
        continue;
      }

      if (isUnresolvableReference(refPath)) {
        throw hookRejection(
            file,
            "a remote or templated reference ('"
                + refPath
                + "') whose content rosco cannot fetch and inspect for hooks/postRenderers");
      }
      Path resolvedPath = file.getParent().resolve(refPath).normalize();
      if (Files.exists(resolvedPath)) {
        resolved.add(resolvedPath.toString());
      }
    }
    return resolved;
  }

  @SuppressWarnings("unchecked")
  private void checkForHooksAndPostRenderers(Map<String, Object> section, Path file) {
    Object hooks = section.get(HOOKS_KEY);
    if (hooks instanceof List && !((List<?>) hooks).isEmpty()) {
      throw hookRejection(file, "`" + HOOKS_KEY + "`");
    }

    Object postRenderers = section.get(POST_RENDERERS_KEY);
    if (postRenderers instanceof List && !((List<?>) postRenderers).isEmpty()) {
      throw hookRejection(file, "`" + POST_RENDERERS_KEY + "`");
    }

    checkArgsForPostRenderer(section.get(ARGS_KEY), file);

    Object helmDefaults = section.get(HELM_DEFAULTS_KEY);
    if (helmDefaults instanceof Map) {
      Map<String, Object> helmDefaultsMap = (Map<String, Object>) helmDefaults;
      Object helmDefaultsPostRenderers = helmDefaultsMap.get(POST_RENDERERS_KEY);
      if (helmDefaultsPostRenderers instanceof List
          && !((List<?>) helmDefaultsPostRenderers).isEmpty()) {
        throw hookRejection(file, "`" + HELM_DEFAULTS_KEY + "." + POST_RENDERERS_KEY + "`");
      }
      checkArgsForPostRenderer(helmDefaultsMap.get(ARGS_KEY), file);
    }
  }

  private void checkArgsForPostRenderer(Object argsValue, Path file) {
    if (!(argsValue instanceof List)) {
      return;
    }
    for (Object arg : (List<?>) argsValue) {
      if (arg instanceof String && POST_RENDERER_FLAG_PATTERN.matcher((String) arg).matches()) {
        throw hookRejection(file, "an `args` entry containing `" + arg + "`");
      }
    }
  }

  private static boolean isUnresolvableReference(String ref) {
    return ref.contains("://") || ref.startsWith("git::") || ref.contains("{{");
  }

  private static IllegalArgumentException hookRejection(Path file, String what) {
    return new IllegalArgumentException(
        "The helmfile content at "
            + file
            + " contains "
            + what
            + ". Rosco refuses to bake helmfile content it cannot fully validate for "
            + "hooks/postRenderers - both of which helmfile executes as an arbitrary local "
            + "command/script even during 'helmfile template' - because its content may not be "
            + "as trusted as the pipeline referencing it. If you trust this source, set "
            + "helmfile.allow-hooks-and-post-renderers: true in rosco's configuration.");
  }
}
