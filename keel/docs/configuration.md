# Configuration

Configuration lives in classes in the `com.netflix.spinnaker.config` package, annotated with `@ConfigurationProperties`.

## Dynamic configuration

The standard Spring configuration values are initialized once, on startup.
If you want configuration values that can change at runtime, use a pattern like this:

```kotlin
import org.springframework.core.env.Environment

class Notifier(
  private val springEnv: Environment,
  ...
) {

  private val notificationsEnabled: Boolean
    get() = springEnv.getProperty("keel.notifications.resource", Boolean::class.java, true)

```
