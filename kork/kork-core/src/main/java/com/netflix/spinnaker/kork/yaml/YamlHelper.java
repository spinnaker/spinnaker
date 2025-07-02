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

@Component
public class YamlHelper {

  private static YamlParserProperties yamlParserProperties;

  @Autowired
  public YamlHelper(YamlParserProperties props){
    yamlParserProperties = props;
  }

  private static boolean hasYamlSecurityPropertiesConfigured(){
    return yamlParserProperties != null && (yamlParserProperties.getMaxAliasesForCollections() != null || yamlParserProperties.getCodePointLimit() != null);
  }

  public static Yaml newYaml() {
    if(hasYamlSecurityPropertiesConfigured()){
      LoaderOptions opts = getLoaderOptions();

      Constructor  constructor  = new Constructor(opts);
      Representer  representer  = new Representer();
      DumperOptions dumperOpts  = new DumperOptions();
      Resolver     resolver     = new Resolver();    // default tag resolver

      return new Yaml(constructor, representer, dumperOpts, opts, resolver);
    }

    return new Yaml();
  }

  public static Yaml newYamlSafeConstructor() {
    if(hasYamlSecurityPropertiesConfigured()){
      LoaderOptions opts = getLoaderOptions();

      SafeConstructor constructor  = new SafeConstructor(opts);
      Representer  representer  = new Representer();
      DumperOptions dumperOpts  = new DumperOptions();

      return new Yaml(constructor, representer, dumperOpts, opts);
    }

    return new Yaml(new SafeConstructor());
  }

  public static Yaml newYamlDumperOptions(DumperOptions dumperOptions) {
    if(hasYamlSecurityPropertiesConfigured()){
      LoaderOptions opts = getLoaderOptions();

      SafeConstructor constructor  = new SafeConstructor(opts);
      Representer  representer  = new Representer();

      return new Yaml(constructor, representer, dumperOptions, opts);
    }

    return new Yaml(new SafeConstructor(), new Representer(), dumperOptions);
  }

  public static Yaml newYamlLoaderOptions(LoaderOptions loaderOptions) {
    if(hasYamlSecurityPropertiesConfigured()){
      LoaderOptions opts = getLoaderOptions();
      return new Yaml(opts);
    }
    return new Yaml(loaderOptions);
  }

  public static Yaml newYamlRepresenter(Constructor constructor, Representer representer) {
    if(hasYamlSecurityPropertiesConfigured()){
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
