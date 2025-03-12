# Building and Running Kayenta

This guide is for building a deployable artifact of the Kayenta microservice.

While there are several approaches, this guide will build a fat jar via the SpringBoot Gradle plugin.

## Building the Artifact

First, we need to make an init script to pull in the SpringBoot Gradle plugin that has the logic we need in order to assemble a usable fat jar.

Create the following file. 

Please note that the version of the below code snippet is not guaranteed to be accurate.
Use `./gradlew kayenta-web:dependencyInsight --dependency spring-boot-starter-web` to see what version the project is using.

```groovy
initscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }

  dependencies {
    classpath("org.springframework.boot:spring-boot-gradle-plugin:2.2.0.RELEASE")
  }
}

allprojects {
  apply plugin: org.springframework.boot.gradle.plugin.SpringBootPlugin
}
```

Now that you have a init script you can run the following command to build the artifact.

```bash
./gradlew --init-script /path/to/init.gradle kayenta-web:bootJar
```

This should build a jar called `kayenta-web.jar` in `kayenta-web/build/libs/`. You can confirm that the jar is ready launching it with java.

```bash
$ java -jar kayenta-web/build/libs/kayenta-web.jar 
  __  __     ______     __  __     ______     __   __     ______   ______    
 /\ \/ /    /\  __ \   /\ \_\ \   /\  ___\   /\ "-.\ \   /\__  _\ /\  __ \   
 \ \  _"-.  \ \  __ \  \ \____ \  \ \  __\   \ \ \-.  \  \/_/\ \/ \ \  __ \  
  \ \_\ \_\  \ \_\ \_\  \/\_____\  \ \_____\  \ \_\\"\_\    \ \_\  \ \_\ \_\ 
   \/_/\/_/   \/_/\/_/   \/_____/   \/_____/   \/_/ \/_/     \/_/   \/_/\/_/ 
```

If you see the Kayenta ASCII banner, then the jar is good to go.

## Running Kayenta

### Configure Kayenta

See [Configuring Kayenta](./configuring-kayenta.md).

### Launching Kayenta

Kayenta configures itself via a YAML. Depending on your configuration, Kayenta's configuration might have secrets in it (API keys).

Here is an example script that fetches the config securely using [Cerberus](http://engineering.nike.com/cerberus/), a secure property store, and launches the application.

```bash
#!/usr/bin/env bash

LOG_DIR=/var/log/kayenta
LOG_OUT=${LOG_DIR}/stdout.log
LOG_ERR=${LOG_DIR}/stderr.log

# configure the jvm by using export JVM_BEHAVIOR_ARGS
. /path/to/some/file/that/does/advanced/jvm/config/

# Fetch the url for the latest linux version
CERBERUS_CLI_URL=$(curl -s https://api.github.com/repos/Nike-Inc/cerberus-cli/releases/latest | \
    jq -r '.assets[] | select(.name=="cerberus-cli-linux-amd64") | .browser_download_url')

# Download the CLI
curl --silent --location --output ./cerberus ${CERBERUS_CLI_URL}

# Make sure that it is executable
chmod +x ./cerberus

# Download props yaml
cerberus -r us-west-2 -u https://cerberus.example.com file download app/nde-ca-kayenta/kayenta_coalmine.yml -o /opt/kayenta/kayenta.yml
# Download the certificate to serve traffic over https
cerberus -r us-west-2 -u https://cerberus.example.com file download app/nde-ca-kayenta/certificate.pfx -o /opt/kayenta/certificate.pfx

APP_SPECIFIC_JVM_ARGS="\
-Dspring.application.name=kayenta \
-Dspring.config.name=kayenta \
-Dspring.config.location=file:/opt/kayenta/ \

java -jar \
    ${JVM_BEHAVIOR_ARGS} \
    ${APP_SPECIFIC_JVM_ARGS} \
    /opt/kayenta/kayenta-web.jar > ${LOG_OUT} 2> ${LOG_ERR}
```

The TL;DR of the above script is:

```bash
java -jar -Dspring.application.name=kayenta -Dspring.config.name=kayenta -Dspring.config.location=file:/path/to/dir/with/config kayenta-web.jar
```
