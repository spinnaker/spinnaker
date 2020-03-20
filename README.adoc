:doctype: book

image:http://join.spinnaker.io/badge.svg[Slack Status,link=http://join.spinnaker.io]
image:https://travis-ci.org/spinnaker/spinnaker.svg?branch=master["Build Status", link="https://travis-ci.org/spinnaker/spinnaker"]

= Welcome to the Spinnaker Project

Spinnaker is an open-source continuous delivery platform for releasing software changes with high velocity and confidence.
Through a powerful abstraction layer, Spinnaker provides compelling tooling that empowers developers to own their application code from commit to delivery. As the most mature and widely productionalized continuous delivery platform, Spinnaker can apply the expertise of Netflix, Google, Microsoft,
 and Amazon to your SDLC. User companies include Target, Salesforce, Airbnb, Cerner, Adobe, and JPMorgan Chase.

Manage your SDLC in Spinnaker using the GUI (graphical user interface), or config-as-code. View, manage, and construct https://www.spinnaker.io/concepts/#application[application] workflows involving one or all of these resources: 

- https://www.spinnaker.io/reference/pipeline/stages/#bake[Virtual machine (VM) deployments to a public cloud provider], "baked" as immutable infrastructure
- https://www.spinnaker.io/reference/providers/[Container deployments to a cloud]
- https://www.spinnaker.io/guides/user/kubernetes-v2/deploy-manifest/[Container deployments to Kubernetes]
- https://www.spinnaker.io/concepts/#load-balancer[Load balancers]
- Security groups
- https://www.spinnaker.io/concepts/#server-group[Server groups]
- https://www.spinnaker.io/concepts/#cluster[Clusters]
- https://www.spinnaker.io/concepts/#firewall[Firewalls]
- Functions


Facilitate GitOps, and grant developers control of provisioning infrastructure for apps. Use Spinnaker’s Clouddriver to deploy to all of the major public cloud providers and Kubernetes. You may even orchestrate configuration and firmware changes as part of Spinnaker delivery https://www.spinnaker.io/concepts/#firewall[pipelines].

= Why Do I Need Spinnaker?

With Spinnaker, create a “paved road” for application delivery, with guardrails that ensure only valid infrastructure and configuration reach production.
Free development teams from burdensome ops provisioning while automating reinforcement of business and regulatory requirements. Delivery automation
strategies such as canary deployments provide the safety necessary to capture value from quick innovation, while protecting against business and end-user
 impact.
 
= Tech Specs

Spinnaker consists of a number of https://www.spinnaker.io/reference/architecture/[independent microservices], with the https://github.com/spinnaker/halyard[Halyard CLI tool] or the https://docs.armory.io/spinnaker/operator/[Kubernetes Operator] (Beta)
managing the lifecycle of the other services. A https://www.spinnaker.io/setup/other_config/[variety of SDLC tools] integrate with Spinnaker, and its plugin framework makes Spinnaker more easily customizable to your needs. Plugins allow us to add system integrations without updating Spinnaker, broadening its potential
to easily leverage the entire software delivery toolchain. With this in place, Spinnaker is evolving towards a smaller threat surface,
enabling performance and operational advantages. Meanwhile, managed delivery, a newer Spinnaker featureset, provides declarative definitions of common
infrastructure and other requirements; users can declare requirements using those
prebuilt definitions and move changes through environments via a visual interface.

'''

This repository centralizes issue tracking across Spinnaker for each microservice. The core code making up Spinnaker’s microservices is found in the other https://github.com/spinnaker[Spinnaker repositories].
The core code making up Spinnaker’s microservices is found in the other https://github.com/spinnaker[Spinnaker repositories].

= Using Spinnaker

Spinnaker users should refer to the main
https://www.spinnaker.io/[Spinnaker site] and https://www.spinnaker.io/setup/[Installation] guide.

For more information on how Spinnaker is designed, see the https://www.spinnaker.io/concepts/[Documentation Overview].

If you're interested in a detailed walkthrough of the Spinnaker systems, check the https://www.spinnaker.io/reference/[Reference Material].

= Setting Up Spinnaker For Development

To pull Spinnaker from source and set it up to run locally against any of the https://www.spinnaker.io/setup/install/providers/#supported-providers[Cloud Providers], follow the https://spinnaker.io/guides/developer/getting-set-up/[Developer Setup Guide]
