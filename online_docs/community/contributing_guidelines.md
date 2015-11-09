---
layout: toc-page
title: Contributing to Spinnaker
id: contributing_guidelines
lang: en
---

* Table of contents. This line is required to start the list.
{:toc}

# Contributing guidelines

## Filing issues

Please file issues through the [Spinnaker GitHub issue tracker](https://github.com/spinnaker/spinnaker/issues).

## How to Become a Contributor

### Contributor License Agreements

We'd love to accept your patches! Before we can take them, we have to
jump through a couple of legal hurdles.

Please fill out either the individual or corporate Contributor License
Agreement (CLA).

* If you are an individual writing original source code and you're
  sure you own the intellectual property, then you'll need to sign an
  {% include link.to id="individual_cla" text="individual CLA" %}.
* If you work for a company that wants to allow you to contribute your
  work, then you'll need to sign a {% include link.to
  id="corporate_cla" text="corporate CLA" %}.

Follow either of the two links above to access the appropriate CLA and
instructions for how to sign and return it. Once we receive it, we'll
be able to accept your pull requests.

Only original source code from you and other people that have signed
the CLA can be accepted into the main repository.

We use gradle for builds; all third-party dependencies should be
brought in using gradle's dependency management support.

### Contributing a Patch

1. Submit an issue describing your proposed change to the repo in question.
1. The repo owner will respond to your issue promptly.
1. If your proposed change is accepted, and you haven't already done
so, sign a Contributor License Agreement (see details above).
1. Fork the desired repo, develop and test your code changes.
1. Submit a pull request.

### Protocols for Collaborative Development

Please read {% include link.to id="collab_dev" text="this doc" %} for
information on how we're running development for the project. Also,
take a look at the {% include link.to id="developer_guide"
text="developer's guide" %} for information on how to setup your
environment, run tests, manage dependencies, etc.
