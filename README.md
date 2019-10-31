_**IMPORTANT:** This service is currently under development and is not ready for production._

---
# High Level Project Overview
Keel is the service where much of our work on the new Managed Delivery iniative is happening. For a high level overview of the Managed Delivery project and its goals, [check out our introductory post on the Spinnaker blog](https://blog.spinnaker.io/managed-delivery-evolving-continuous-delivery-at-netflix-eb74877fb33c).

Continue reading for technical architecture, or check out our [FAQ section](FAQ.md) for more information about what we're building. Join us in the [#sig-spinnaker-as-code](https://slack.com/app_redirect?team=T091CRSGH&channel=CERACDPDZ) channel on the [Spinnaker Slack workspace](http://join.spinnaker.io/) if you have questions or want to get involved!

# Keel Architecture

This document describes the architecture of Spinnaker’s Keel service, which is responsible for managed delivery.


## Terminology


### Resources

Resources model the specification for a concrete thing or set of homogeneous things in the cloud. Each distinct type of resource is defined by its _API version_ and _kind_. For example:



*   _EC2 Cluster_: a specification for a set of homogeneous auto-scaling groups deployed in one or more regions.
*   _EC2 Security Group_: a specification for a  set of homogeneous security groups existing in one or more regions.
*   _EC2 Classic _or_ Application Load Balancer_: a specification for a set of homogeneous load balancers existing in one or more regions.
*   _Bakery Image_: a specification for an AMI derived from successive versions of an artifact.

The representation of resources is consciously designed around the equivalent concept in Kubernetes and we model resources using Kubernetes’ [CRD](https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/) format.


### Artifacts

An artifact represents a deployable piece of software that has successive versions. For example a docker image or a debian package (that is baked into an AMI for deployment). When resources depend on artifacts there is no need for a team to update their resource specs when a new version of the artifact is released. 

Edge cases exist where some teams may require specific versions of artifacts and may “pin” cluster resources to a particular version, essentially opting out of managed artifact promotion. We want to support such cases, but they are a small minority.


### Environments

Environments group resources and define _constraints_ that determine how and when a new artifact version is allowed into the environment.

The intention is that environments in the same application should be largely homogeneous, with specific differences in naming (for example differences in Moniker’s concept of a _stack_ or _detail_ field in a resource’s name), or deployment location (for example at Netflix the resources in different environments would typically be deployed in different AWS accounts).

A simple example would be an application with three environments:



*   Test: no constraints, the latest artifact version is deployed as soon as it is available.
*   Staging: constrained by a dependence on the stability of the test environment with artifact versions being promoted once a condition is met such as a metric threshold over a defined time, a smoke test has run successfully, etc.
*   Production: constrained by dependency on the stability of the staging environment and additional constraints such as canary success or manual approval. Additional rules may define things such as region-by-region roll-out of new artifact versions, or a veto on managed changes during certain time windows.


### Delivery Configuration Manifests

Environments are further grouped into delivery configuration manifests enabling interdependence between environments (e.g. constraints on one environment requiring successful deployment to another).


## Source of Truth

Keel’s database is in all cases the source of truth for a resource spec. Although we intend to support a GitOps style workflow for teams that want it (and we anticipate this being the majority case), we do not require it. Other teams may prefer to submit their resource definitions to Keel’s REST API. For example, teams that use their own tooling to generate specs for ephemeral resources.

We also do not want to be reliant on connectivity to a Git repository in order to determine the desired state of resources.


## Resource Monitoring

At the core of Keel is a resource monitoring loop that regularly compares the _current_ and _desired_ versions of a resource and takes steps to bring them into alignment if they differ.

_Resource handlers_ (one for each resource kind) are responsible for reporting the current state and _resolving_ the desired state (see below). The logic for determining if a delta exists is _not_ specific to each handler but is common to all resource types. If a delta exists, the handler is invoked again in order to converge on the desired state. Existing handlers do this by invoking tasks in Orca such as cloning a new version of a server group, resizing a server group, creating a load balancer, etc.

Handlers can, if they wish, take different actions to align current and desired state depending on the details of the delta. For example; a server group that differs from its desired state only in capacity can be resized rather than having to be re-deployed, a cluster whose actual state differs from the desired in only one region can re-deploy the server group in that one region rather than re-creating the entire cluster.


## Artifact Promotion

Similarly to the resource monitoring loop, Keel also regularly evaluates whether new artifact versions meet the constraints on various environments. If they do the version is promoted to that environment. This will affect the resolution of desired state for clusters within that environment.


## Resolution of Desired State

Resource handlers essentially model three operations:



1. Determine the current state of a particular resource.
2. Resolve the desired state of a particular resource.
3. Take action to reconcile a delta detected between current and desired state.

The first two operations are homeomorphic. They are passed a resource _spec _and return a model of the fully-resolved, fine-grained detail of the actual cloud resources the spec represents. For example the operations on an EC2 cluster handler accept a “cluster spec” and return a map of server group details keyed by region. The values returned by these two operations are diffed and must be identical if current and desired states are in alignment.

This resolution of desired state means that the resource spec can model things in terms of abstractions. For example:



*   Homogeneous server groups using the specification in multiple environments (in Spinnaker currently users would have to define these separately in the deploy stage of a pipeline).
*   An artifact which is resolved into a specific AMI according to the constraints that exist in the resource’s environment.
*   An EC2 instance type determined by automated recommendation from a central capacity planning team.

Resolution of desired state can change over time which is why it is performed every time a resource is evaluated by the resource monitoring loop. For example:



*   If a new artifact version is promoted to an environment, desired state resolution will include a different AMI the next time it is evaluated.
*   If a centralized team wants to migrate users to a new class of EC2 instance type they can update the recommendation, resulting in a different instance type in the resolved desired state of cluster resources.

Desired state resolution is modular. Multiple _resolvers_ may exist for a resource kind, each being responsible for particular opinions. This modularity not only simplifies testing of resolvers but allows specific resolvers to be enabled or disabled when Keel is deployed. For example, Netflix has specific opinions about the security groups that should be associated with a server group and we have a resolver for that outside of the open-source Keel repository.


## Tooling

Although Keel does some templating-style functionality, for example resources that span multiple regions, we anticipate the need to provide tooling that enables users to define homogeneous sets of resources that appear in all their environments based on templates. Whether this is done as a pre-processing step when files are submitted to Keel, or using external tools is yet to be determined.

