package com.netflix.spinnaker.kork.yaml;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Utility component for creating preconfigured {@link Yaml} instances with optional
 * security-related parsing limits.
 *
 * <p>This helper centralizes the creation of {@link Yaml} objects used across the Spinnaker
 * ecosystem, ensuring that YAML parsing behavior is consistent and secure. It applies limits
 * defined in {@link YamlParserProperties}, such as:
 *
 * <ul>
 *   <li>{@code maxAliasesForCollections} – to prevent Billion Laughs (entity expansion) attacks
 *   <li>{@code codePointLimit} – to restrict the maximum size of YAML input
 * </ul>
 *
 * <p>If no security-related properties are configured, the helper falls back to creating standard
 * {@link Yaml} instances using SnakeYAML defaults.
 *
 * <p>This class also provides convenience factory methods for constructing {@link Yaml} objects
 * with various configurations such as: {@link Constructor}, {@link SafeConstructor}, {@link
 * DumperOptions}, {@link LoaderOptions}, and {@link Representer}.
 *
 * <p><strong>Usage Example:</strong> inject the {@code YamlHelper} bean and use the instance API.
 *
 * <pre>{@code
 * private final YamlHelper yamlHelper; // constructor-injected
 *
 * Yaml yaml = yamlHelper.newSafeConstructorYaml(); // applies configured security limits
 * Map<String, Object> data = yaml.load(yamlContent);
 * }</pre>
 *
 * <p>When {@link YamlParserProperties} is available in the Spring context, security properties are
 * automatically applied to all created {@link Yaml} instances. The {@code static} factory methods
 * are deprecated and retained only for callers that are not yet Spring-managed.
 */
@Component
@Log4j2
public class YamlHelper {

  /**
   * Configured limits for the injected-bean (instance) API. Prefer injecting the {@code YamlHelper}
   * bean and calling the instance methods below.
   */
  private final YamlParserProperties yamlParserProperties;

  /**
   * Legacy static reference to the most-recently constructed helper's properties. Retained only so
   * the deprecated static methods keep functioning for callers that are not yet Spring-managed.
   * Remove once all callers inject the {@code YamlHelper} bean.
   */
  private static YamlParserProperties staticYamlParserProperties;

  @Autowired
  public YamlHelper(YamlParserProperties props) {
    this.yamlParserProperties = props;
    staticYamlParserProperties = props;
  }

  // ---------------------------------------------------------------------------
  // Shared logic (works off whichever properties are supplied)
  // ---------------------------------------------------------------------------

  private static boolean hasYamlSecurityPropertiesConfigured(YamlParserProperties props) {
    return props != null
        && (props.getMaxAliasesForCollections() != null || props.getCodePointLimit() != null);
  }

  private static LoaderOptions buildLoaderOptions(YamlParserProperties props) {
    LoaderOptions opts = new LoaderOptions();
    if (props != null) {
      if (props.getMaxAliasesForCollections() != null) {
        opts.setMaxAliasesForCollections(props.getMaxAliasesForCollections());
      }
      if (props.getCodePointLimit() != null) {
        opts.setCodePointLimit(props.getCodePointLimit());
      }
    }
    return opts;
  }

  private static Yaml buildYamlSafeConstructor(YamlParserProperties props) {
    if (hasYamlSecurityPropertiesConfigured(props)) {
      LoaderOptions opts = buildLoaderOptions(props);

      SafeConstructor constructor = new SafeConstructor(opts);
      DumperOptions dumperOpts = new DumperOptions();
      Representer representer = new Representer(dumperOpts);

      return new Yaml(constructor, representer, dumperOpts, opts);
    }

    return new Yaml(new SafeConstructor(new LoaderOptions()));
  }

  private static Yaml buildYamlDumperOptions(
      YamlParserProperties props, DumperOptions dumperOptions) {
    if (hasYamlSecurityPropertiesConfigured(props)) {
      LoaderOptions opts = buildLoaderOptions(props);

      SafeConstructor constructor = new SafeConstructor(opts);
      Representer representer = new Representer(dumperOptions);

      return new Yaml(constructor, representer, dumperOptions, opts);
    }

    return new Yaml(
        new SafeConstructor(new LoaderOptions()), new Representer(dumperOptions), dumperOptions);
  }

  private static Yaml buildYamlLoaderOptions(
      YamlParserProperties props, LoaderOptions loaderOptions) {
    if (hasYamlSecurityPropertiesConfigured(props)) {
      return new Yaml(buildLoaderOptions(props));
    }
    return new Yaml(loaderOptions);
  }

  private static Yaml buildYamlRepresenter(
      YamlParserProperties props, Constructor constructor, Representer representer) {
    if (hasYamlSecurityPropertiesConfigured(props)) {
      return new Yaml(constructor, representer, new DumperOptions(), buildLoaderOptions(props));
    }
    return new Yaml(constructor, representer);
  }

  // ---------------------------------------------------------------------------
  // Instance API — preferred. Inject the YamlHelper bean and call these.
  // ---------------------------------------------------------------------------

  /**
   * Builds {@link LoaderOptions} honoring the injected security limits (e.g. {@code
   * codePointLimit}, {@code maxAliasesForCollections}).
   */
  public LoaderOptions loaderOptions() {
    return buildLoaderOptions(yamlParserProperties);
  }

  /**
   * Creates a new {@link Yaml} instance with a {@link SafeConstructor}, applying the injected
   * security limits when configured.
   */
  public Yaml newSafeConstructorYaml() {
    return buildYamlSafeConstructor(yamlParserProperties);
  }

  /** Creates a new {@link Yaml} instance using the specified {@link DumperOptions}. */
  public Yaml newYaml(DumperOptions dumperOptions) {
    return buildYamlDumperOptions(yamlParserProperties, dumperOptions);
  }

  /** Creates a new {@link Yaml} instance using the specified {@link LoaderOptions}. */
  public Yaml newYaml(LoaderOptions loaderOptions) {
    return buildYamlLoaderOptions(yamlParserProperties, loaderOptions);
  }

  /**
   * Creates a new {@link Yaml} instance using the given {@link Constructor} and {@link
   * Representer}.
   */
  public Yaml newYaml(Constructor constructor, Representer representer) {
    return buildYamlRepresenter(yamlParserProperties, constructor, representer);
  }

  // ---------------------------------------------------------------------------
  // Legacy static API — DEPRECATED. Kept only for callers that are not yet
  // Spring-managed; migrate them to the injected bean and instance API above.
  // ---------------------------------------------------------------------------

  /**
   * @deprecated inject the {@link YamlHelper} bean and call {@link #newSafeConstructorYaml()}.
   */
  @Deprecated
  public static Yaml newYamlSafeConstructor() {
    return buildYamlSafeConstructor(staticYamlParserProperties);
  }

  /**
   * @deprecated inject the {@link YamlHelper} bean and call {@link #newYaml(DumperOptions)}.
   */
  @Deprecated
  public static Yaml newYamlDumperOptions(DumperOptions dumperOptions) {
    return buildYamlDumperOptions(staticYamlParserProperties, dumperOptions);
  }

  /**
   * @deprecated inject the {@link YamlHelper} bean and call {@link #newYaml(LoaderOptions)}.
   */
  @Deprecated
  public static Yaml newYamlLoaderOptions(LoaderOptions loaderOptions) {
    return buildYamlLoaderOptions(staticYamlParserProperties, loaderOptions);
  }

  /**
   * @deprecated inject the {@link YamlHelper} bean and call {@link #newYaml(Constructor,
   *     Representer)}.
   */
  @Deprecated
  public static Yaml newYamlRepresenter(Constructor constructor, Representer representer) {
    return buildYamlRepresenter(staticYamlParserProperties, constructor, representer);
  }

  /**
   * @deprecated inject the {@link YamlHelper} bean and call {@link #loaderOptions()}.
   */
  @Deprecated
  public static LoaderOptions getLoaderOptions() {
    return buildLoaderOptions(staticYamlParserProperties);
  }
}
