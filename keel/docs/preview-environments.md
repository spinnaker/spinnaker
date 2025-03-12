# Preview Environments V1

This document describes a design proposal to support Preview Environments in Managed Delivery. It's split in two main parts: the first focuses on use cases and the experience; the second contains a high-level design proposal meant to identify major technical challenges and design directions early, and help guide implementation later.

Author(s): @luispollo

# Context

## What are Preview Environments?

Preview environments (a.k.a. "feature stacks") provide a means to test changes in an application's code repo, in a live environment, without having to merge those changes to the main development branch. Developers create a pull request based on a "feature branch", and the changes pushed to the branch are deployed to a temporary environment in the cloud, where developers can preview and test them. Once the PR is finally merged (or declined, or deleted), the temporary environment is cleaned up to avoid wasting resources and to make sure that old versions of code aren't left running unattended as they may impact other environments/systems.

**Why not just call it "feature stacks" in Managed Delivery?**
We think that, while that's a somewhat popular term among certain Netflix engineering teams, the word "stacks" is reminiscent of the old way of reasoning about *environments*, which are a first-class concept in MD. We feel that "preview environments" captures the purpose of the feature well and is better aligned with the terminology and vision of MD. (Also, [it's not a new term](https://jenkins-x.io/docs/build-test-preview/preview/).)

# Problem statement

Feature stacks using pipelines are a somewhat common pattern in Netflix engineering teams. However, there's currently no off-the-shelf "paved road" solution to set them up, and no centralized support and maintenance. The exception are solutions provided by Platform teams for specific application types. These managed development experiences use Spinnaker pipelines to deploy code from feature branches to temporary clusters. Finally, there are teams who've created their own home-baked solutions to have some level of standardization.

For those teams who can currently take advantage of narrower solutions, the experience they get for delivery workflows (seeing all the different environments, figuring out what version of code is where, being able to take action on deployment failures, etc.) is not easy or intuitive. That kind of experience is where Managed Delivery shines, so the opportunity to migrate part or all of those solutions to MD would be a major win.

Finally, for teams already using Managed Delivery, it's hard to contemplate going back to the old way of managing infrastructure and delivery flows (or a hybrid experience with both MD and pipelines), so adding support in MD is a natural evolution of the product that will provide those customers with a seamless experience for all their continuous delivery needs.

# Product requirements and UX

## Requirements

These are the high-level requirements, which will be translated into a user journey further down the document.

|Name                                                                                                                   |Tags         |
|-----------------------------------------------------------------------------------------------------------------------|-------------|
|Provide a means for users to easily setup preview environments for their application                                   |Onboarding   |
|Onboarding should be a one-time process (with subsequent tweaks to config allowed)                                     |Onboarding   |
|Use of preview environments should be opt-in (i.e. off by default)                                                     |Onboarding   |
|Onboarding should use a branch naming convention instead of asking the user                                            |Onboarding   |
|Configuration should live in the delivery config in git in line with the MD as-code model                              |Onboarding   |
|New artifacts originating from the feature branch should be deployed to preview environment as they become available   |Deployment   |
|Resource changes made to the base environment in the feature branch should be reflected in the preview environment     |Deployment   |
|Preview environment resources should be named automatically based on feature branch and/or conventions                 |Deployment   |
|Names of preview environment server groups should respect Spinnaker/DNS naming rules                                   |Deployment   |
|Preview environment server groups should inherit FPs from the base environment                                         |Deployment   |
|VIP override for clusters is set as FP (to ensure no traffic collision with other clusters)                            |Deployment   |
|Provide a means for users to configure verification tests to be executed on preview environments after deployment      |Testing      |
|User is notified of deployment results via Slack if configured                                                         |Notifications|
|User is notified of verification test results via Slack if configured                                                  |Notifications|
|Feature branch PR is updated with links to deployed preview environment clusters                                       |Notifications|
|The Stash commit status is updated with the result of the corresponding artifact deployment                            |Notifications|
|Resources are cleaned up automatically upon PR merged/declined/deleted                                                 |Cleanup      |
|User documentation is provided covering all the configuration paths, examples, and workarounds for unsupported features|Documentation|
|                                                                                                                       |             |

### Out of scope

The following are out of scope in V1:

- Automatic routing configuration other than VIP overrides for clusters (to prevent preview environment clusters from taking traffic that is destined to the base environment clusters).

  We understand it would be helpful for users if we provided a mechanism to simplify this configuration, but based on many discussion threads in this document and elsewhere, routing is a complex topic that can easily become a "can of worms", creating an opportunity for unexpected behaviors and bugs, so we consciously choose to leave it out from V1. The expectation is that users will reconfigure their clients (both client apps/UIs and other backend services) to point to the preview environment endpoints.

  Specific things we're not addressing in this proposal:

    - Wall-e (re-)configuration
    - DNS (re-)configuration
    - Load balancer (re-)configuration
- Support for Jenkins-based verifications. MD has made a choice to prioritize verifications via container-based tests. Our decision for V1 is to leave Jenkins tests out of the picture. We may reevaluate based on customer feedback.
- TTLs. It is unclear at this time whether this is a "must have" requirement, so we're leaving it out and prioritizing automatic cleanup based on actions taken against the feature branch PR (merged, declined, deleted).
- Reusing resources from the base environment (as opposed to copying/renaming them in the preview environment). This is valid requirement would definitely be useful, particularly for security groups. However, for the sake of simplicity and expediency, we're starting with a simple approach of copying in V1.
- Taking action on feature branches matching the specified filter if they are *not* associated with a PR. The customer input we've gathered tells us that branches without PRs are uncommon and so not a priority.
- Modeling deployment dependencies/ordering (e.g. between backend and frontend components of the same app).

## User journey

There are three primary steps in the user journey to address the requirements above:

- Everything starts with **onboarding**, which happens once (and may reoccur sporadically for tweaks). The developer sets up the configuration for preview environments for a single app/repo via their delivery config, then creates a feature branch and a corresponding PR.
- Once things are setup, developers go back to their **day-to-day flow**, where they make changes to the application code in the feature branch, and those changes are built and deployed automatically to the preview environment.

  Developers may also make changes to the infrastructure resource definitions in the branch, and those changes are similarly reflected in the preview environment. This is what will allow them to test out changes to infrastructure in an isolated environment, before promoting them to the static environments defined in the delivery config.

  If you're thinking to yourself *"That sounds a lot like canaries..."*, you'd be right! We're leveraging lessons learned and code from ChAP to build this feature. More details below.

- When they're finally done, and merge the branch/PR back to the main branch (or close the PR, or delete the branch), **cleanup** happens: the associated preview environment is destroyed automatically.

Let's look at each of those steps in the user journey in more detail.

### Onboarding

Onboarding is normally a one-time process: we need to collect some basic information from the user in order to set things up. Since the configuration for preview environments will [live in the delivery config](#proposed-changes-to-the-delivery-config), this process can be done manually by editing the file. At Netflix, the plan is to offer an integration in an internal project onboarding tool to make this process easier, which is outside to scope of this open spec.

### Day-to-day flow

After setting up a preview environment template in the delivery config, Managed Delivery will start monitoring for changes to the branch. At some point, the developer will push the branch to the git remote and open a PR, at which point the following would happen:

- A CI build would kick in and build/publish an artifact
- MD would detect the branch update and create the preview environment, including deploying that new artifact when available

The developer will then continue to make changes to the branch and push to the remote, which will reflect in the temporary environment. There are two sub-cases here:

**App code changes**

In this scenario, the developer makes code changes to their application, and pushes to the remote branch. Again, this triggers the CI build and a code event coming into MD. Since there are no changes to the delivery config, MD doesn't make any changes to the preview environment immediately. Whenever the artifact originating from the branch becomes available, MD deploys it to the preview environment.

Here's what this would look like from the user's perspective:

1. Commit a code change to the app's repo. Push to the remote branch.
2. As soon as the artifact is published from the CI build, MD displays it on the artifact versions list in the UI.
3. The new artifact is deployed to the preview environment, with the usual experience in the UI, plus:
    - The user gets a notification in Slack (if configured) about the deployment succeeding or failing
    - The PR is updated to show a link to the deployed version in the Environments UI
    - The commit status in Stash is updated with the result of the deployment

---

**Infrastructure changes**

Now let's look at an example where there's no application code changes, but the user wants to test out a change to the infrastructure configuration in the preview environment. There's no UI-based flow for this, since we don't currently have a UI/CLI flow for changing the delivery config, so the user would change the delivery config in source.

The original delivery config might look like this:

```yaml
name: myapp
application: myapp
artifacts:
  - type: docker
    name: myorg/myapp
    reference: main
    from:
      branch:
        name: main
environments:
  - name: test
    resources:
      - kind: titus/cluster@v1
        spec:
          # ... (omitted for brevity)
previewEnvironments:
  - branch:
      name: "feature/infra-changes"
    baseEnvironment: test
```

The user then creates a branch called `feature/infra-changes`, which matches the branch filter for the preview environment template, opens a PR, and makes a change to add a new resource (a load balancer) to the base environment:

```yaml
name: myapp
application: myapp
artifacts:
  - type: docker
    name: myorg/myapp
    reference: main
    from:
      branch:
        name: main
environments:
  - name: test
    resources:
      - kind: titus/cluster@v1
        spec:
          # ... (omitted for brevity)
      - kind: ec2/application-load-balancer@v1.2
        spec:
          # ... (omitted for brevity)
previewEnvironments:
  - branch:
      name: "feature/infra-changes"
    baseEnvironment: test
```

The experience from the user's perspective would look like this:

1. Update the delivery config in the branch, push to the remote.
2. New resource soon appears in the preview environment in the Environments UI.
    - TBD: Similar to the app code change experience above, we could consider updating the PR/commit with the results of the infrastructure actions, but I'd prefer to leave that out of V1.
    - A "nice to have" here would be the ability to view a diff of the preview environment resources and the base environment to see what's changing

### Cleanup

The very last step in the user journey for a preview environment is cleanup. This would kick in if one of the following occurred:

- The feature branch PR was merged back to the main branch
- The PR was declined
- The PR was deleted

Let's pick the first one just for the sake of the example. Here's what the experience would look like:

1. Developer is happy with his tests in the preview environment and merges the PR.
2. MD starts cleaning up the preview environment resources, with a clear indication in the UI (mock-ups TBD)
3. When the cleanup is complete, the developer can still see the deleted environment in the UI for future reference (maybe greyed out, or in a separate area of the UI — TBD)

---

# High-level design

Please do *not* expect to find every implementation detail figured out and documented in this proposal. The goal is to capture enough directional design decisions such that whoever works on the various pieces of this feature later will have enough context to make their own judgement calls or drill down into the design as needed. As they do, I fully expect there will be areas that will require more discussion, and perhaps revisiting.

## Assumptions

- This proposal assumes that Spinnaker supports a single source code repository per Spinnaker application (as configured in the Config page for the application in the Spinnaker UI). Multi-artifact deployments (e.g. a backend and frontend combo) are only supported via the "monorepo" approach.
- Changes to the delivery config in a feature branch will **not** affect any static environments defined in the "golden copy" from the main branch, only preview environments.
- This proposal assumes that there can be multiple preview environment templates in the delivery config, although our expectation is that the vast majority of users would only configure one, and we'll promote that pattern with Netflix users.
- There should be no overlap/conflict between this proposal and the [environment versioning proposal](https://docs.google.com/document/d/1mSiqAipb2HKU4fhdgzgRz8X9a2G-nNEmQvKBzBN0itI/edit) that @robfletcher is currently implementing. The two features should complement each other.
- The solution will rely heavily on Rocket integration and so will likely not be available out-of-the-box in OSS.

## Proposed changes to the delivery config

Consider the following simplified delivery config:

```yaml
name: myapp
application: myapp
artifacts:
  - type: docker
    name: myorg/myapp
    reference: main
    from:
      branch:
        name: main
environments:
  - name: test
    resources:
      - kind: titus/cluster@v1
        spec:
          container:
            reference: main
          # ... (omitted for brevity)
```

In this example, we have a Docker artifact and a `test` environment with a Titus cluster where this artifact gets deployed whenever a new image originating from the `main` branch is published.

To configure an application for preview environments, we would introduce a new top-level key in the delivery config: `**previewEnvironments`.** Each entry under this key would represent a preview environment *template* that allows Keel to **dynamically create preview environments** based on an existing (static) environment in the delivery config. For each branch matching the filter in the template, a separate and isolated environment is dynamically created.

The updated delivery config would look like this:

```yaml
name: myapp
application: myapp
artifacts:
  - type: docker
    name: myorg/myapp
    reference: main
    from:
      branch:
        name: main
environments:
  - name: test
    resources:
      - kind: titus/cluster@v1
        reference: test-cluster
        spec:
          moniker:
            application: myapp
            stack: test
          container:
            reference: main
          # ... (omitted for brevity)
previewEnvironments:
  - branch:
      startsWith: "feature/"
    baseEnvironment: test
    notifications: []
    verifyWith: []
```

Here are the interesting new things to note in this updated delivery config:

- A new `previewEnvironments` top-level key is introduced in the delivery config. This is an array, where each entry represents a preview environment template and has the following fields:
    - `branch`: this is where the user would specify a branch filter for Keel to monitor. In this example, we're matching against any branch name starting with `feature/`.
    - `baseEnvironment`: the static environment upon which preview environments should be based on. In this example, we're saying that they should be based on the `test` environment. This means that every preview environment based on this definition would include all the resources defined in the associated base environment. Note that **preview environments will *not* inherit constraints, notifications or verifications from the base environment**. Constraints do not apply to preview environments. Notifications and verifications can be specified explicitly with the keys below.
    - `notifications`: allows the user to specify notifications for preview environments. We do not copy the notification configuration from the base environment because we cannot assume that's what the user will want for preview environments, so we err on the side of caution. In the example above, we're saying "no notifications", which is also the default.
    - `verifyWith`: similar to notifications, we don't assume the user wants to run the same verifications in the preview environments as they do in the base environment, so they need to specify them explicitly here. But, keep in mind you can always use [YAML anchors and aliases](https://support.atlassian.com/bitbucket-cloud/docs/yaml-anchors/) to avoid repetition! In the example above, we're saying "no verifications", which is also the default.

### Naming of preview environments and resources

The name of each preview environment would be automatically generated using the name of the base environment and the name of the branch. This ensures that **each feature branch gets a dedicated preview environment**. For example, if the feature branch is called `feature/myfeature`, then the environment would be named `test-feature-myfeature` using our sample delivery config (forward slashes replaced with dashes).

This naming mechanism would apply not only to the environment itself, but to all resources defined within it, by overriding the `detail` field of each resource's `moniker`. In our example, the Titus cluster name (which in `test` would be `myapp-test`) would become `myapp-test-feature-myfeature` (where application is `myapp`, stack is `test`, and detail is `feature-myfeature`).

Heads-up: Spinnaker enforces a maximum cluster name length of 63 characters in compliance with [RFC-1035](https://tools.ietf.org/html/rfc1035).

There are some open questions to sort out around the naming proposal above, which will be addressed when we get closer to implementation:

- We'll need some special handling for resource dependencies (e.g. cluster dependencies on security groups), since those would be copied from the base environment but are just names, and so don't carry enough metadata to infer what resources they refer to, so that we can fix the references accordingly. We'll need some smarts in resource resolution to address this.
- Copying resources and renaming them has an undesirable side-effect in that security groups attached to *other* resources which may grant access to the original resources in the base environment would know nothing about the copies, and so would need to be manually adjusted. This is similar to the routing problem discussed in the beginning of this document and which is proposed to be out of scope for V1. I think we can do the same with this issue.

## System behavior on onboarding (config created)

As soon as the user defines a preview environment template in the delivery config (in the main branch), Keel will start monitoring for Rocket code events matching the branch filter in the spec. No other action is taken until there's a match.

**Implementation note**
Keel already receives Rocket code events from Igor via the `POST /artifacts/events` API, we just currently ignore them. This means the work involved in supporting onboarding is to model the new format of the delivery config to support the `previewEnvironments`key, and to implement handling for Rocket code events, hooking into the existing API.

## System behavior in day-to-day workflow (branch created or updated)

Most of this flow is already supported by Keel today, since we already detect the creation of or updates to a delivery config, and react accordingly to take action on resources or monitor for artifacts. The difference here is that we'll pull the delivery config from the feature branch, and generate the environment definition dynamically.

The complete flow would go like this:

1. User creates a branch called `feature/myfeature`.
2. User pushes the branch to the remote and opens a PR against the main branch.
3. Keel receives a code event from Rocket, and detects that the branch name matches the spec of the preview environment and that it's associated with a PR.
4. Keel retrieves the delivery config from the branch.

   **Implementation note**
   Keel [already has a `DeliveryConfigImporter`component](https://github.com/spinnaker/keel/blob/master/keel-igor/src/main/kotlin/com/netflix/spinnaker/keel/igor/DeliveryConfigImporter.kt) for retrieving the delivery config from git via Igor.

5. Keel creates a new environment called``test-feature-my-feature``. The resources in this environment will also have their names based on the naming convention including the branch name, as discussed in the delivery config format above.

   Clusters defined in the preview environment will additionally have all their artifact `from` specs automatically overwritten to pick up artifacts from the matching feature branch. We will also add a special flag or marker of some kind to these resources such that we can implement special behavior when resolving artifacts for deployment, with the following logic:

    - If a matching artifact originating from the feature branch can be found, pick that
    - If a matching artifact originating from the feature branch can *not* be found, pick the artifact matching the branch filter from the base environment

   This behavior will allow flexibility to developers in what they want to change in a preview environment. For example, in a backend + frontend app example, if they only want to make changes to the frontend in the branch, they can. Keel will pick the artifact from the branch for the frontend, and fallback to the artifact from the main branch for the backend.

6. (Netflix only) Keel ensures that Fast Properties associated with the base environment can be inherited by the preview environment via normal FP scoping (using `cluster` and `stack`) and overrides an FP for the cluster VIP scoped to the preview environment's cluster, to prevent the preview environment from taking traffic destined for the base environment.

7. Keel creates a new Titus cluster resource called `myapp-test-feature-myfeature` which runs the Docker image specified in the `feature-branches` artifact.
8. Keel sends out Slack notification if configured.
9. At this point:
    - If specified, Keel runs any verifications against the temporary environment.
        - TBD: what environment variables Keel should pass to the container so it can find the right endpoints to connect to (cluster VIP, Eureka DNS name, load balancer hostname, etc.)
    - User can begin testing against the new environment.

### Changes to application code

This is already supported by MD today. The flow goes like this:

1. User makes a change to the application code, pushes to the remote branch.
2. CI system builds and publishes an artifact with the new version of the code. The package contains metadata about the source (commit hash, branch, author, etc.) and the build.
3. Keel receives an artifact event, records it in the database.
4. Keel detects a difference in desired and current state for the cluster (Docker image hash changes), and starts a deployment to the preview environment.
5. Upon completion, Keel sends out Slack notification if configured.
6. At this point:
    - If specified, Keel runs any verifications against the temporary environment.
    - User can test his changes in the preview environment.

### Changes to infrastructure in the delivery config

This is new. The idea here is to allow the user to **test infrastructure changes in the same way they would application changes**.

Here's the flow:

1. User makes a change to the delivery config in a feature branch matching the filter. Let's say they add a load balancer to the `test` environment.
2. CI system may or may not build a new artifact depending on how the build is configured (if it's smart enough to ignore changes to the delivery config, there will be no artifact published). That is irrelevant for this particular flow.
3. Keel receives a code event from Rocket and sees that it matches the branch filter in the preview environment template.
4. Keel retrieves the updated delivery config from the branch.
5. Keel updates the `test-feature-my-feature` environment and creates a new load balancer resource called `myapp-test-feature-myfeature`. **The original** `test` **environment remains untouched.**
6. Keel sends out Slack notification if configured.
7. At this point:
    - If specified, Keel runs any verifications against the temporary environment.
    - User can test the change in the feature stack environment.

### More examples

**Environment with multiple artifacts**

Let's consider a common case of backend + frontend deployment. The delivery config in the main branch might look like this:

```yaml
name: myapp
application: myapp
branches:
  main:
    branch: &main-branch
      name: main
  features:
    branch: &feature-branches
      startsWith: "feature/"
artifacts:
  - type: docker
    name: myorg/myfrontend
    reference: frontend-main
    from: *main-branch
  - type: docker
    name: myorg/mybackend
    reference: backend-main
    from: *main-branch
environments:
  - name: test
    resources:
      - kind: titus/cluster@v1
        spec:
          moniker:
            application: myapp
            stack: test
          container:
            reference: backend-main
          # ... (omitted for brevity)
      - kind: titus/cluster@v1
        spec:
          moniker:
            application: myapp
            stack: test
          container:
            reference: frontend-main
          # ... (omitted for brevity)
previewEnvironments:
  - <<: *feature-branches
    baseEnvironment: test
```

In the example above, we have 2 artifacts, one for a backend and one for a frontend component. As discussed previously, the resulting preview environment for the `feature/myfeature` branch would attempt to run the versions of both artifacts originating from the feature branch, and fallback to the version from the base environment `test` when there's no matching artifact from the feature branch. For example, if the user publishes only the frontend artifact from the branch, then only that one will be different in the preview environment.

## System behavior on cleanup (PR merged, declined or deleted)

When the user is ready with the PR, they'll merge the changes back to the main branch. At this point, Keel needs to do some cleanup. Here's the flow:

1. User merges, declines or deletes the PR.
2. Keel receives a code event from Rocket, determines that PR was merged/declined/deleted.
3. Keel takes a snapshot of the environment config JSON at the time of deletion so it can be listed on the UI for historical reference (see earlier in the doc for the proposed UX).
4. Keel destroys all resources in the preview environment. We may need periodic retries around this with some additional state management (like a "marked-for-deletion" status before actually deleting records from the database).

   This is brand new behavior, and the first time Keel will destroy any cloud resources that is not encapsulated by deployment stages. We need to have good documentation and notifications around this cleanup process.

5. Keel deletes its database records for the temporary environment and temporary resources (but keeps the snapshot from step 3 in a separate table).
6. Keel sends Slack notifications if configured.