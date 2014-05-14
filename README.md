Kato
===

Deployment libraries for the Asgard platform.

Quick Use
===

In a Gradle project:

```groovy
buildscript {
  dependencies {
    classpath "org.springframework.boot:spring-boot-gradle-plugin:1.0.2.RELEASE"
  }
}

apply plugin: "spring-boot"

repositories {
  jcenter()
}

dependencies {
  // Include if you want deployment operations for Amazon
  compile "com.netflix.asgard.kato:kato-aws:1.1"

  // Include if you want deployment operations for Google Compute Engine
  compile "com.netflix.asgard.kato:kato-gce:1.1"

  // Base include
  compile "com.netflix.asgard.kato:kato-web:1.1"
}

run {
  main = 'com.netflix.asgard.kato.Main'
}
```

You can then execute the task: `gradle bootRun`. Kato, by default, runs on port 8501.

Documentation
===

Start an instance of Kato and point to +/manual+.

Authors
===

Dan Woods

Copyright and License
===

Copyright (C) 2014 Netflix. Licensed under the Apache License.

See [LICENSE.txt](https://raw.githubusercontent.com/Asgard/kato/master/LICENSE.txt) for more information.
