Bluespar Amazon Components
---

This project provides common utilities for working with the Amazon AWS SDK.

Edda
---

Through the +AmazonClientProvider+, callers can get supply an [Edda](https://github.com/Netflix/edda/wiki) host as a String pattern in the form that formats to a hostname that includes a region and test (in that order) in the URL. For example: +http://edda.%s.%s.netflix.com+ will be formatted to +http://edda.us-east-1.test.netflix.com+ when I am requesting a client in "us-east-1" with an +AmazonCredentials+ object that uses "test" as its environment.

Credentials
---

There sometimes is the need to draw an internal correlation of a given account to an arbitrarily named environment. To facilitate that relationship, this project projects an +AmazonCredentials+ class, which provides that relationship to the +AmazonClientProvider+.

Authors
---
Dan Woods

License and Copyright
---

This project is licensed under the Apache Software License. See [LICENSE.txt](license.txt) for futher information.
This project is Copyright (C) 2014 Netflix, Inc.
