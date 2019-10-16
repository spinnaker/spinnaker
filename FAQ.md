# Frequently Asked Questions

### What is the goal of this project?

We want to make delivery and infrastructure management simpler for engineering teams, while at the same time letting experts create and share best practices for deploying software and defining infrastructure at their companies.

### What are the key features?

There are three major buckets of new functionality planned for Keel, each one building on top of the last.

**Step 1: Declarative Infrastructure** 

Essentially bringing Kubernetes-like definition and continuous management ([see this question](#how-is-this-different-from-existing-products)) of resources to all other cloud providers, and making it possible to inject intelligent defaults and abstractions on top of lower-level infrastructure configurations.

**Step 2: Declarative Delivery** 

Declarative modeling of how new artifacts (i.e. software versions) flow through different environments (like test -> staging -> prod), and when they qualify for delivery into new environments.
Environments are made up of a collection of declaratively defined infrastructure.

**Step 3: Managed Delivery** 

Provides abstractions that simplify how you configure your delivery, a way to templatize and share these configurations, and adds hook points so that you can opt into decisions made by centralized teams or other subject matter experts.

For example: 

* Opting in to a plugin that picks the type of VM you deploy to, or compute resources you need to run your application.

* Delegating autoscaling rules/capacity management to a subject matter expert

* Allowing central, automated systems to configure networking rules and security policies



### How is this different from existing products?
_More specifically, how is this different from Kubernetes, Terraform, or Cloud Formation?_

Declarative Infrastructure is the most comparable with Kubernetes in that it takes declarative resource configurations and constantly enforces that each resource is in the desired state.
Terraform is potentially the second most similar — that said, tools like Terraform or Cloud Formation are optimized for different goals. 

A few specifics in the context of Terraform:

1. Terraform is optimized for 'stand up a new X' use cases — e.g. putting a footprint in a new account/region or similar. 
While in theory it does support updating environments that already exist, that isn't its strongest quality. Keel aims to serve both needs equally.

2. Terraform keeps its own state of the world to compare against desired state, which can have different/bad consequences vs. using actual cloud state as we do in Keel (or Kubernetes). 
While you could theoretically run Terraform apply in a loop to continuously reconcile desired state, the moment actual cloud state got out of sync with Terraform state you'd run the risk of Terraform taking wrong or even dangerous actions based on an incorrect view of reality.

3. Separate from how cloud state is computed, the way Terraform looks at how to plan and apply is not well suited to safe, reliable and continuous reconciliation of desired state. 
One example is the common gotcha of Terraform deciding to destroy and then recreate a resource — that behavior is fine when standing things up, but in an existing production environment it's potentially disastrous. 
We want managing infrastructure to be as safe and reliable as delivery of new software versions can be today, and will always favor safe and predictable outcomes even if more work is required.

Part of the value of Declarative Infrastructure comes from bringing the great things about Kubernetes to all cloud providers in Spinnaker, regardless of paradigm (containers, VMs, functions, etc.). That said, there are additional capabilities built into Declarative Infrastructure that go beyond what Kubernetes itself provides and help build toward the vision of Managed Delivery [defined above](#what-is-the-goal-of-this-project).
So how does Kubernetes itself actually work together with Declarative Infrastructure? That’s a question we're working to answer with the community. In short, we see Declarative Infrastructure as a framework that will help apply intelligent, opinionated defaults on top of Kubernetes resources, making it possible to leverage the Declarative Delivery + Managed Delivery approach we're working on now in Keel. 

### Does this mean that pipelines will be deprecated?

No! 

We think that there are plenty of times when using pipelines and defining things imperatively is a great approach. 
For example, a pipeline is perfect for general (e.g. non-deployment) orchestration like batch/cron work, or any other use case where “do this after that” logic can’t be modified to fit a declarative format. 
Keel is also built on top of the same orchestration and cloud operations platform (orca, clouddriver, etc.) that powers imperative pipelines today, so the technology that supports existing pipelines will continue to be actively developed and improved.

That said, we believe that the declarative approach we are building will be a useful complement to the imperative approach. 
For apps both large and small, we expect defining infrastructure and delivery in terms of what you want, not how to get there, will help make it easier to understand, change, and improve how Spinnaker manages your application over time.
An approach like the one we’re taking with Keel also has benefits that are hard to replicate with pipelines around things like:

* Managing a family or group of similar applications

* Creating lots of homogeneous infrastructure with small variations per app or environment

* Making use of shared, reusable opinions and best practices from subject matter experts or central teams

### How can I use this?

These features are not ready for production yet, but if you’d like to get involved please join the [Spinnaker-As-Code sig](https://www.spinnaker.io/community/governance/sigs/#existing-sigs) and join us in the `#sig-spinnaker-as-code` Slack channel!

If you’re using AWS EC2 and want to test things out we can help you stand up `keel`.
