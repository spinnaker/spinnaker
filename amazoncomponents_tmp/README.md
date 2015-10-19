Netflix Amazon Components
---

This project provides common utilities for working with the Amazon AWS SDK.

Edda
---

Through the `AmazonClientProvider`, callers can get supply an [Edda](https://github.com/Netflix/edda/wiki) host as a String pattern in the form that formats to a hostname that includes a region and environment name (in that order) in the URL, which formats to a URL that maps to an Edda host. For example: `http://edda.%s.%s.netflix.com` will be formatted to `http://edda.us-east-1.test.netflix.com` when I am requesting a client in "us-east-1" with an `AmazonCredentials` object that uses "test" as its environment.

Quick Use
---
From Gradle:

```groovy
repositories {
  mavenCentral()
}
dependencies {
  compile 'com.amazonaws:aws-java-sdk:1.7.9'
  compile 'com.netflix.amazoncomponents:amazoncomponents:0.8'
}

```

... and then ...

```groovy
import com.netflix.amazoncomponents.security.*
import com.amazonaws.auth.BasicAWSCredentials

def credentials = new AmazonCredentials(new BasicAWSCredentials("accessId", "secretKey"), "test")

def eddaFormat = "http://edda.%s.%s.netflix.com" // will get translated to http://edda.us-east-1.test.netflix.com
def provider = new AmazonClientProvider(eddaFormat)

def amazonEC2 = provider.getAmazonEC2(credentials, "us-east-1")

// This call will go through Edda
amazonEC2.describeSecurityGroups()
```


Credentials
---

There sometimes is the need to draw an internal correlation of a given account to an arbitrarily named environment. To facilitate that relationship, this project projects an `AmazonCredentials` class, which provides that relationship to the `AmazonClientProvider`.

Authors
---
Dan Woods

License and Copyright
---

This project is licensed under the Apache Software License. See [LICENSE](https://raw.githubusercontent.com/Netflix/amazoncomponents/master/LICENSE) for futher information.
This project is Copyright (C) 2014 Netflix, Inc.

