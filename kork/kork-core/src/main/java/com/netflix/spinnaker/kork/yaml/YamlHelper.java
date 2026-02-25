package com.netflix.spinnaker.kork.yaml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

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
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * Yaml yaml = YamlHelper.newYaml(); // creates a secure YAML parser if properties are set
 * Map<String, Object> data = yaml.load(yamlContent);
 * }</pre>
 *
 * <p>When {@link YamlParserProperties} is available in the Spring context, security properties are
 * automatically applied to all created {@link Yaml} instances.
 */
@Component
public class YamlHelper {

  private static YamlParserProperties yamlParserProperties;

  @Autowired
  public YamlHelper(YamlParserProperties props) {
    yamlParserProperties = props;
  }

  private static boolean hasYamlSecurityPropertiesConfigured() {
    return yamlParserProperties != null
        && (yamlParserProperties.getMaxAliasesForCollections() != null
            || yamlParserProperties.getCodePointLimit() != null);
  }

  /**
   * Creates a new {@link Yaml} instance using either default or secure {@link LoaderOptions},
   * depending on whether {@link YamlParserProperties} are configured.
   *
   * @return a new {@link Yaml} instance
   */
  public static Yaml newYaml() {
    if (hasYamlSecurityPropertiesConfigured()) {
      LoaderOptions opts = getLoaderOptions();

      Constructor constructor = new Constructor(opts);
      Representer representer = new Representer();
      DumperOptions dumperOpts = new DumperOptions();
      Resolver resolver = new Resolver(); // default tag resolver

      return new Yaml(constructor, representer, dumperOpts, opts, resolver);
    }

    return new Yaml();
  }

  /**
   * Creates a new {@link Yaml} instance with a {@link SafeConstructor}, ensuring that only standard
   * types are loaded (no arbitrary object instantiation). If security properties are set, they are
   * applied via {@link LoaderOptions}.
   *
   * @return a new {@link Yaml} instance with safe construction
   */
  public static Yaml newYamlSafeConstructor() {
    if (hasYamlSecurityPropertiesConfigured()) {
      LoaderOptions opts = getLoaderOptions();

      SafeConstructor constructor = new SafeConstructor(opts);
      Representer representer = new Representer();
      DumperOptions dumperOpts = new DumperOptions();

      return new Yaml(constructor, representer, dumperOpts, opts);
    }

    return new Yaml(new SafeConstructor());
  }

  /**
   * Creates a new {@link Yaml} instance using the specified {@link DumperOptions}. Applies
   * security-related {@link LoaderOptions} if available.
   *
   * @param dumperOptions configuration for YAML serialization
   * @return a new {@link Yaml} instance
   */
  public static Yaml newYamlDumperOptions(DumperOptions dumperOptions) {
    if (hasYamlSecurityPropertiesConfigured()) {
      LoaderOptions opts = getLoaderOptions();

      SafeConstructor constructor = new SafeConstructor(opts);
      Representer representer = new Representer();

      return new Yaml(constructor, representer, dumperOptions, opts);
    }

    return new Yaml(new SafeConstructor(), new Representer(), dumperOptions);
  }

  /**
   * Creates a new {@link Yaml} instance with the specified {@link LoaderOptions}. If security
   * properties are configured, they override the provided options.
   *
   * @param loaderOptions custom loader options for YAML parsing
   * @return a new {@link Yaml} instance
   */
  public static Yaml newYamlLoaderOptions(LoaderOptions loaderOptions) {
    if (hasYamlSecurityPropertiesConfigured()) {
      LoaderOptions opts = getLoaderOptions();
      return new Yaml(opts);
    }
    return new Yaml(loaderOptions);
  }

  /**
   * Creates a new {@link Yaml} instance using the given {@link Constructor} and {@link
   * Representer}. Applies secure {@link LoaderOptions} if configured.
   *
   * @param constructor the YAML constructor
   * @param representer the YAML representer
   * @return a new {@link Yaml} instance
   */
  public static Yaml newYamlRepresenter(Constructor constructor, Representer representer) {
    if (hasYamlSecurityPropertiesConfigured()) {
      LoaderOptions opts = getLoaderOptions();
      return new Yaml(constructor, representer, new DumperOptions(), opts);
    }
    return new Yaml(constructor, representer);
  }

  private static LoaderOptions getLoaderOptions() {
    LoaderOptions opts = new LoaderOptions();
    if (yamlParserProperties.getMaxAliasesForCollections() != null) {
      opts.setMaxAliasesForCollections(yamlParserProperties.getMaxAliasesForCollections());
    }

    if (yamlParserProperties.getCodePointLimit() != null) {
      opts.setCodePointLimit(yamlParserProperties.getCodePointLimit());
    }
    return opts;
  }
}
