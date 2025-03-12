# Plugin Framework V2

The V2 plugin framework is an under-the-hood rework loading plugins, and offers Spinnaker a better foundation for adding more power in the future.
The primary change is that V2 uses Spring more under the covers to perform dependency injection of plugin classes.
The goal of this refactor is to make it less necessary for plugins to use `kork-plugins-spring-api`.

## Facts

- Existing plugins do not need to change: V2 is backwards-compatible from an API perspective.
- The main `Plugin` class for a plugin continues to not be managed by Spring:
  It will not have dependency injection available to it beyond `@PluginConfiguration` classes and `PluginSdks`.
- All plugin classes that implement `SpinnakerExtensionPoint` are automatically candidates for autowiring.
- All plugin classes that implement `SpinnakerExtensionPoint` will be auto-promoted to the service's `ApplicationContext`, making it available for autowiring within the service itself.
  No other plugin classes are candidates for promotion.
- Other plugin classes that should be available for autowiring can be annotated with `@PluginComponent`.
  This annotation is roughly equivalent to Spring's own `@Component` annotation.
- Service components are valid candidates for autowiring into plugin classes automatically.
  Expose components by defining them (or their interface) in the `{service}-api` module.
- Extensions are now initialized much later in the application startup lifecycle.
  As a result, Spinnaker services may need to refactor how extensions are wired into the application, either via a Registry that extensions inject themselves into, or via `Provider` or `ObjectProvider`.

## Plugin Configuration Changes

In the V1 framework, a plugin could also include configuration for specific extension points.
This functionality has been removed, partly due to incompatibilities, but also to simplify the experience for plugin developers and operators alike.

A plugin configuration in V1 might've looked like this:

```yaml
spinnaker:
  extensibility:
    plugins:
      netflix.example:
        enabled: true
        config:
          pluginConfig: someValue
        extensions:
          my-extension:
            config:
              myConfig: myValue
```

Whereas now, plugin configuration is defined only at the top-level:

```yaml
spinnaker:
  extensibility:
    plugins:
      netflix.example:
        enabled: true
        config:
          pluginConfig: someValue
          myConfig: myValue
```

When using the `@PluginConfiguration` annotation, you still have the option of providing a `value` property.
When doing so, it will namespace the config:

```kotlin
@PluginConfiguration("my-namespace")
class MyConfig(var foo: String)
```

```yaml
spinnaker:
  extensibility:
    plugins:
      netflix.example:
        enabled: true
        my-namespace:
          config:
            foo: bar
```

## Autowiring

Autowiring of plugin components are available.
The root package of a plugin will be recursively scanned for autowiring candidates:
If the `Plugin` class is found on the package `io.spinnaker.myplugin`, that package, and all packages under it will be scanned for components.

Similar to Spring, `kork-plugins-api` exposes a `@PluginComponent` annotation that can be applied to any class.
This class will then become available for constructor autowiring, as well as being injected into other classes.
Classes implementing `SpinnakerExtensionPoint` are automatically selected for autowiring and do not need the annotation.
