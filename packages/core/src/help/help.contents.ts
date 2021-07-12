import { SETTINGS } from '../config/settings';

import { HelpContentsRegistry } from './helpContents.registry';

export interface IHelpContents {
  [key: string]: string;
}

const helpContents: { [key: string]: string } = {
  'core.serverGroup.detail':
    '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables in naming a cluster.',
  'core.serverGroup.strategy':
    'The deployment strategy tells Spinnaker what to do with the previous version of the server group.',
  'cluster.search': `
      Quickly filter the displayed server groups by the following fields:
      <ul>
        <li>Build # (e.g. <samp>#337</samp>)</li>
        <li>Jenkins host</li>
        <li>Jenkins job name</li>
        <li>Cluster (prefixed, e.g. <samp>cluster:myapp-int</samp>)
        <li>VPC (prefixed, e.g. <samp>vpc:main</samp>)
        <li>Clusters (comma-separated list, e.g. <samp>clusters:myapp-int, myapp-test</samp>)
        <li>Server Group Name</li>
        <li>Region</li>
        <li>Account</li>
        <li>Load Balancer Name</li>
        <li>Instance ID</li>
        <li>Labels (comma-separated list of key-value pairs that must all apply to entity, e.g. <samp>labels:app=spinnaker, source=prod</samp>)</li>
      </ul>
      <p>You can search for multiple words or word fragments. For instance, to find all server groups in a prod stack with "canary" in the details, enter <samp>prod canary</samp>.</p>
      <p>To find a particular instance, enter the instance ID. Only the containing server group will be displayed, and the instance
      will be highlighted for you.</p>`,
  'loadBalancer.search': `
      Quickly filter the displayed load balancers by the following fields:
      <ul>
        <li>VPC (prefixed, e.g. <samp>vpc:main</samp>)
        <li>Server Group Name</li>
        <li>Load Balancer Name</li>
        <li>Region</li>
        <li>Account</li>
        <li>Instance ID</li>
      </ul>
      <p>You can search for multiple words or word fragments. For instance, to find all load balancers in a prod stack with "canary" in the details, enter <samp>prod canary</samp>.</p>`,
  'securityGroup.search': `
      Filter by the following fields:
      <ul>
        <li>VPC (prefixed, e.g. <samp>vpc:main</samp>)
        <li>Name</li>
        <li>Server Group Name</li>
        <li>Load Balancer Name</li>
        <li>Region</li>
        <li>Account</li>
      </ul>`,
  'executions.search': `
      Quickly filter the displayed executions by the following fields:
      <ul>
        <li>Name</li>
        <li>Trigger</li>
      </ul>`,
  'pipeline.config.triggers.respectQuietPeriod': `
      <p>The quiet period is a system operator designated period of time when automated pipelines and deploys should not run.</p>`,
  'pipeline.config.expectedArtifact':
    'Artifacts required for trigger to execute.  Only one of the artifacts needs to be present for the trigger to execute.',
  'pipeline.config.artifact.help': `
      <p>There are certain types of triggers (e.g. Pub/Sub triggers) that can produce artifacts and inject them into the execution context for a pipeline.</p>
      <p>You can specify artifacts that your pipeline expects to be present in the execution context in this section.</p>`,
  'pipeline.config.artifact.missingPolicy': `
      <p>The behavior of the pipeline if the Artifact is missing from the pipeline execution.</p>`,
  'pipeline.config.artifact.name': `
      <p>The name of the Artifact.</p>`,
  'pipeline.config.artifact.type': `
      <p>The type of the Artifact, e.g. 'gcs/object' or 'rpm'.</p>`,
  'pipeline.config.lock.allowUnlockUi': `
      <p><strong>Checked</strong> - the pipeline can be unlocked via the Spinnaker UI.</p>
      <p><strong>Unchecked</strong> - the pipeline can only be unlocked via the Spinnaker API.</p>`,
  'pipeline.config.lock.description': `
      <p>Friendly description of why this pipeline is locked.</p>
      <p>Please include an email address or slack channel as appropriate.</p>`,
  'pipeline.config.optionalStage': `
      <p>When this option is enabled, stage will only execute when the supplied expression evaluates true.</p>
      <p>The expression <em>does not</em> need to be wrapped in \${ and }.</p>
      <p>If this expression evaluates to false, the stages following this stage will still execute.</p>`,
  'pipeline.config.checkPreconditions.failPipeline': `
      <p><strong>Checked</strong> - the overall pipeline will fail whenever this precondition is false.</p>
      <p><strong>Unchecked</strong> - the overall pipeline will continue executing but this particular branch will stop.</p>`,
  'pipeline.config.checkPreconditions.failureMessage': `
      <p> This failure message will be shown to the user if the precondition evaluates to false. </p>`,
  'pipeline.config.checkPreconditions.expectedSize': 'Number of server groups in the selected cluster',
  'pipeline.config.checkPreconditions.expression': `
      <p>Value must evaluate to "true".</p>
      <p>Use of the <b>Spring Expression Language</b> allows for complex evaluations.</p>`,
  'pipeline.config.deploy.template': `
      <p>Select an existing cluster to use as a template for this deployment, and we'll pre-fill
      the configuration based on the newest server group in the cluster.</p>
      <p>If you want to start from scratch, select "None".</p>
      <p>You can always edit the cluster configuration after you've created it.</p>`,
  'pipeline.config.expectedArtifact.matchArtifact': `
      <p>
        This specifies which fields in your incoming artifact to match against. Every field that
        you supply will be used to match against all incoming artifacts. If all specified fields
        match, the incoming artifact is bound to your pipeline context.
      </p>
      <p>
        The field comparisons are done against the incoming artifact.  Example: if you are parsing
        artifacts from pub/sub messages via a Jinja template, the comparison will be done after
        the pub/sub -> Spinnaker artifact translation.
      </p>
      <p>For example, if you want to match against any GCS object, only supply <b>type</b> = gcs/object. If you also want to restrict the matches by other fields, include those as well.</p>
      <p>Regex is accepted, so you could for example match on a filepath like so <b>name</b> = .*\\.yaml to match all incoming YAML files.</p>
      <p>See the <a href="https://www.spinnaker.io/reference/artifacts/in-pipelines/#expected-artifacts">reference</a> for more information.</p>`,
  'pipeline.config.expectedArtifact.ifMissing': `
      <p>If no artifact was supplied by your trigger to match against this expected artifact, you have a few options:
        <ol>
          <li>Attempt to match against an artifact in the prior pipeline execution's context. This ensures that you will always be using the most recently supplied artifact to this pipeline, and is generally a safe choice.</li>
          <li>If option 1 fails, or isn't specified, you can provide a default artifact with the required fields to use instead.</li>
          <li>Fail the pipeline if options 1 or 2 fail or aren't selected.</li>
        </ol>
      </p>
      <p>See the <a href="https://www.spinnaker.io/reference/artifacts/in-pipelines">reference</a> for more information.</p>`,
  'pipeline.config.expectedArtifact.usePriorExecution': `
      <p>Attempt to match against an artifact in the prior pipeline execution's context. This ensures that you will always be using the most recently supplied artifact to this pipeline, and is generally a safe choice.</p>`,
  'pipeline.config.expectedArtifact.defaultArtifact': `
      <p>If your artifact either wasn't supplied from a trigger, or it wasn't found in a prior execution, the artifact specified below will end up in your pipeline's execution context.</p>
      <p>See the <a href="https://www.spinnaker.io/reference/artifacts/in-pipelines">reference</a> for more information.</p>`,
  'pipeline.config.expectedArtifact.gcs.name': `
      <p>The GCS object name, in the form <code>gs://bucket/path/to/file.yml</code>.</p>`,
  'pipeline.config.expectedArtifact.defaultGcs.reference': `
      <p>The GCS object name, <i>optionally</i> appending the version. An example: <code>gs://bucket/file.yml#123948581</code></p>`,
  'pipeline.config.expectedArtifact.s3.name': `
      <p>The S3 object name, in the form <code>s3://bucket/path/to/file.yml</code>.</p>`,
  'pipeline.config.expectedArtifact.defaultS3.reference': `
      <p>The S3 object name, <i>optionally</i> appending the version. An example: <code>s3://bucket/file.yml#123948581</code></p>`,
  'pipeline.config.expectedArtifact.oracle.name': `
      <p>The Oracle object artifact name, in the form <code>oci://bucket/path/file.yml</code>.</p>`,
  'pipeline.config.expectedArtifact.defaultOracle.reference': `
      <p>The  Oracle object artifact name, <i>optionally</i> appending the version. An example: <code>oci://bucket/file.yml#9ce463aa-d843-4438-b206-5365cd643e2e</code></p>`,
  'pipeline.config.expectedArtifact.docker.name': `
      <p>The Docker image name you want to trigger on changes to. By default, this does <i>not</i> include the image tag or digest, only the registry and image repository.</p>`,
  'pipeline.config.expectedArtifact.defaultDocker.reference': `
      <p>The fully-qualified docker image to deploy. An example: <code>gcr.io/project/image@sha256:59bb771c86</code></p>`,
  'pipeline.config.expectedArtifact.git.name': `
      <p>The file's path from the git root, in the form 'path/to/file.json'</p>`,
  'pipeline.config.expectedArtifact.defaultGithub.version': `
      <p>Either the commit or branch to checkout.</p>`,
  'pipeline.config.expectedArtifact.defaultGithub.reference': `
      <p>The GitHub API content url the artifact lives under. The domain name may change if you're running GHE.</p>
      <p>An example for GitHub.com is <code>https://api.github.com/repos/$ORG/$REPO/contents/$FILEPATH</code>. An example for GitHub Enterprise is <code>https://github.domain.com/api/v3/repos/$ORG/$REPO/contents/$FILEPATH</code>. See <a href="https://www.spinnaker.io/reference/artifacts/types/github-file/#fields">our docs</a> for more info.</p>`,
  'pipeline.config.expectedArtifact.defaultGitlab.version': `
      <p>Either the commit or branch to checkout.</p>`,
  'pipeline.config.expectedArtifact.defaultGitlab.reference': `
      <p>The Gitlab API file url the artifact lives under. The domain name may change if you're running your own Gitlab server. The repository and path to files must be URL encoded.</p>
      <p>An example is <code>https://gitlab.com/api/v4/projects/$ORG%2F$REPO/repository/files/path%2Fto%2Ffile.yml/raw</code>. See <a href="https://www.spinnaker.io/reference/artifacts/types/gitlab-file/#fields">our docs</a> for more info.</p>`,
  'pipeline.config.expectedArtifact.gitrepo.url': '<p>The location of your Git repository.</p>',
  'pipeline.config.expectedArtifact.gitrepo.branch': '<p>The branch of the repository you want to use.</p>',
  'pipeline.config.expectedArtifact.gitrepo.checkoutSubpath':
    '<p>Check this if you want to specify a subpath; doing so will reduce the size of the generated artifact.</p>',
  'pipeline.config.expectedArtifact.gitrepo.subpath': `
    <p>The subpath within the Git repository you desire to checkout.</p>
    <p>e.g.: <b>examples/wordpress/mysql/</b></p>`,
  'pipeline.config.expectedArtifact.helm.account': `
      <p>The account contains url the charts can be found</p>`,
  'pipeline.config.expectedArtifact.helm.name': `
      <p>The name of chart you want to trigger on changes to</p>`,
  'pipeline.config.expectedArtifact.helm.version': `
      <p>The version of chart you want to trigger on changes to</p>`,
  'pipeline.config.expectedArtifact.defaultBitbucket.reference': `
      <p>The Bitbucket API file url the artifact lives under. The domain name may change if you're running your own Bitbucket server. The repository and path to files must be URL encoded.</p>
      <p>An example is <code>https://api.bitbucket.org/1.0/repositories/$ORG/$REPO/raw/$VERSION/$FILEPATH</code>. See <a href="https://www.spinnaker.io/reference/artifacts/types/bitbucket-file/#fields">our docs</a> for more info.</p>`,
  'pipeline.config.expectedArtifact.defaultBitbucket.filepath': `
      <p>The file path within your repo. path/to/file.yml is an example.</p>`,
  'pipeline.config.trigger.helm.chart': `The Helm chart name.`,
  'pipeline.config.trigger.helm.version': `The Helm chart version, as semver.`,
  'pipeline.config.trigger.helm.version.manual': `The Helm chart version, as an exact version.`,
  'pipeline.config.trigger.webhook.source': `
      <p>Determines the target URL required to trigger this pipeline, as well as how the payload can be transformed into artifacts.</p>
  `,
  'pipeline.config.trigger.webhook.payloadConstraints': `
      <p>When provided, only a webhook with a payload containing at least the specified key/value pairs will be allowed to trigger this pipeline. For example, if you wanted to lock down the systems/users that can trigger this pipeline via this webhook, you could require the key "secret" and value "something-secret" as a constraint.</p>
      <p>The constraint values may be supplied as regex.</p>
  `,
  'pipeline.config.trigger.pubsub.attributeConstraints': `
      <p>Pubsub messages can have system-specific metadata accompanying the payload called <b>attributes</b>.</p>
      <p>When provided, only a pubsub message with attributes containing at least the specified key/value pairs will be allowed to trigger this pipeline.</p>
      <p>The constraint value is a java regex string.</p>
  `,
  'pipeline.config.trigger.pubsub.payloadConstraints': `
      <p>
        When provided, only a pubsub message with a payload containing at least the specified
        key/value pairs will be allowed to trigger this pipeline. For example, if you wanted
        to restrict the systems/users that can trigger this pipeline via this pubsub
        subscription, you could require the key "secret" and value "something-secret" as a constraint.
      </p>
      <p>
        The key/value pairs are matched against the unprocessed payload body, prior to any
        transformation using, for example, a Jinja template in a pubsub subscription configuration.
      </p>
      <p>The constraint value is a java regex string.</p>
  `,
  'pipeline.config.findArtifactFromExecution.considerExecutions': `
      <p>Select the types of executions to consider. When no selection is made, the default is "any execution".</p>
      <p>This will always evaluate to the most recent execution matching your provided criteria.</p>
  `,
  'pipeline.config.tags': `<p>Pipeline tags let you filter pipelines/executions by addition dimensions in the executions page</p>`,
  'loadBalancer.advancedSettings.healthTimeout':
    '<p>Configures the timeout, in seconds, for reaching the healthCheck target.  Must be less than the interval.</p><p> Default: <b>5</b></p>',
  'loadBalancer.advancedSettings.idleTimeout':
    '<p>Configures the idle timeout, in seconds. If no data has been sent or received by the time that the idle timeout period elapses, the load balancer closes the connection. </p><p> Default: <b>60</b></p>',
  'loadBalancer.advancedSettings.deletionProtection':
    '<p>To prevent your load balancer from being deleted accidentally, you can enable deletion protection.</p><p> Default: <b>false</b></p>',
  'loadBalancer.advancedSettings.healthInterval':
    '<p>Configures the interval, in seconds, between ELB health checks.  Must be greater than the timeout.</p><p>Default: <b>10</b></p>',
  'loadBalancer.advancedSettings.healthyThreshold':
    '<p>Configures the number of healthy observations before reinstituting an instance into the ELB’s traffic rotation.</p><p>Default: <b>10</b></p>',
  'loadBalancer.advancedSettings.unhealthyThreshold':
    '<p>Configures the number of unhealthy observations before deservicing an instance from the ELB.</p><p>Default: <b>2</b></p>',
  'loadBalancer.advancedSettings.loadBalancingCrossZone':
    '<p>Cross-zone load balancing distributes traffic evenly across all targets in the Availability Zones enabled for the load balancer.</p><p> Default: <b>True</b></p>',
  'loadBalancer.advancedSettings.albIpAddressType':
    '<p>Assigns both a v4 and v6 IP address to the load balancer. If left unchecked, this value will default to <b>"ipv4"</b>.</p>',
  'loadBalancer.advancedSettings.nlbIpAddressType':
    '<p>Assigns both a v4 and v6 IP address to the load balancer. This option is only valid for NLBs which are external and only have Ip targets (not instance targets). If left unchecked, this value will default to <b>"ipv4"</b>.</p>',
  'pipeline.config.resizeAsg.action': `
      <p>Configures the resize action for the target server group.
      <ul>
        <li><b>Scale Up</b> increases the size of the target server group by an incremental or percentage amount</li>
        <li><b>Scale Down</b> decreases the size of the target server group by an incremental or percentage amount</li>
        <li><b>Scale to Cluster Size</b> increases the size of the target server group to match the largest server group in the cluster, optionally with an incremental or percentage additional capacity. Additional capacity will not exceed the existing maximum size.</li>
        <li><b>Scale to Exact Size</b> adjusts the size of the target server group to match the provided capacity</li>
      </ul></p>`,
  'pipeline.config.resizeAsg.cluster':
    '<p>Configures the cluster upon which this resize operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
  'pipeline.config.modifyScalingProcess.cluster':
    '<p>Configures the cluster upon which this modify scaling process operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
  'pipeline.config.enableAsg.cluster':
    '<p>Configures the cluster upon which this enable operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
  'pipeline.config.disableAsg.cluster':
    '<p>Configures the cluster upon which this disable operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
  'pipeline.config.destroyAsg.cluster':
    '<p>Configures the cluster upon which this destroy operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
  'pipeline.config.jenkins.trigger.propertyFile':
    '<p>(Optional) Configures the name to the Jenkins artifact file used to pass in properties to later stages in the Spinnaker pipeline. The contents of this file will now be available as a map under the trigger and accessible via <em>trigger.properties</em>. See <a target="_blank" href="https://www.spinnaker.io/guides/user/pipeline-expressions/">Pipeline Expressions docs</a> for more information.</p>',
  'pipeline.config.jenkins.trigger.payloadConstraints': `<p>(Optional, Requires Property File) When provided, only a build that contains a Property File with correct constraints will trigger this pipeline.  For example, you could restrict the trigger to certain branches by placing the branch name in your Property File and adding a constraint with a key like "branch" and value "master".</p>
    <p>The constraint values may be supplied as regex.</p>`,
  'pipeline.config.jenkins.propertyFile':
    '<p>(Optional) Configures the name to the Jenkins artifact file used to pass in properties to later stages in the Spinnaker pipeline. The contents of this file will now be available as a map under the stage context. See <a target="_blank" href="https://www.spinnaker.io/guides/user/pipeline-expressions/">Pipeline Expressions docs</a> for more information.</p>',
  'pipeline.config.travis.job.isFiltered':
    '<p>Note that for performance reasons, not all jobs are displayed. Please use the search field to limit the number of jobs.</p>',
  'pipeline.config.travis.trigger.propertyFile':
    '<p>(Optional) Configures the name to the Travis artifact file used to pass in properties to later stages in the Spinnaker pipeline. The contents of this file will now be available as a map under the trigger and accessible via <em>trigger.properties</em>. See <a target="_blank" href="https://www.spinnaker.io/guides/user/pipeline-expressions/">Pipeline Expressions docs</a> for more information.</p>',
  'pipeline.config.travis.trigger.payloadConstraints': `<p>(Optional, Requires Property File) When provided, only a build that contains a Property File with correct constraints will trigger this pipeline.  For example, you could restrict the trigger to certain branches by placing the branch name in your Property File and adding a constraint with a key like "branch" and value "master".</p>
    <p>The constraint values may be supplied as regex.</p>`,
  'pipeline.config.travis.propertyFile':
    '<p>(Optional) Configures the name to the Travis artifact file used to pass in properties to later stages in the Spinnaker pipeline. The contents of this file will now be available as a map under the stage context. See <a target="_blank" href="https://www.spinnaker.io/guides/user/pipeline-expressions/">Pipeline Expressions docs</a> for more information.</p>',
  'pipeline.config.bake.skipRegionDetection': `
        <p>By default, Spinnaker will detect regions to bake in from downstream deploy stages.</p>
        <p>To prevent failed deploys from accidentally missed regions during the bake process.</p>
        <p>This setting will disable this detection mechanism.</p>`,
  'pipeline.config.bake.package': `
      <p>The name of the package you want installed (without any version identifiers).</p>
      <p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>
      <p>If there are multiple packages (space separated), then they will be installed in the order they are entered.</p>`,
  'pipeline.config.bake.packageArtifacts': `
      <p>Artifacts representing packages you want installed.</p>
      <p>These artifacts must be either deb or rpm packages, whichever applies to the operating system on your base image.</p>
      <p>Package artifacts are installed in order, after any packages in the 'Packages' field are installed.</p>`,
  'pipeline.config.docker.bake.targetImage': '<p>The name of the resulting docker image.</p>',
  'pipeline.config.docker.bake.targetImageTag':
    '<p>The tag of the resulting docker image, defaults to commit hash if available.</p>',
  'pipeline.config.docker.bake.organization':
    '<p>The name of the organization or repo to use for the resulting docker image.</p>',
  'pipeline.config.bake.baseAmi':
    '<p>(Optional) If Base AMI is specified, this will be used instead of the Base OS provided',
  'pipeline.config.bake.amiSuffix':
    '<p>(Optional) String of date in format YYYYMMDDHHmm, default is calculated from timestamp,</p>',
  'pipeline.config.bake.amiName': '<p>(Optional) Default = $package-$arch-$ami_suffix-$store_type</p>',
  'pipeline.config.bake.templateFileName':
    "<p>(Optional) The explicit packer template to use, instead of resolving one from rosco's configuration.</p>",
  'pipeline.config.bake.varFileName':
    '<p>(Optional) The name of a json file containing key/value pairs to add to the packer command.</p>',
  'pipeline.config.bake.extendedAttributes':
    '<p>(Optional) Any additional attributes that you want to pass onto rosco, which will be injected into your packer runtime variables.</p>',
  'pipeline.config.cf.createservice.updatable':
    '<p>If checked, this will try to update the existing service. If not, if the service exists this stage will succeed and not try to update.</p>',
  'pipeline.config.cf.createservice.versioned':
    '<p>If checked, this stage should always deploy a new service with an incremented version number (mysql-v001).</p>',
  'pipeline.config.manualJudgment.instructions':
    '<p>(Optional) Instructions are shown to the user when making a manual judgment.</p><p>May contain HTML.</p>',
  'pipeline.config.manualJudgment.propagateAuthentication': `
      <p><strong>Checked</strong> - the pipeline will continue with the permissions of the approver.</p>
      <p><strong>Unchecked</strong> - the pipeline will continue with its current permissions.</p>`,
  'pipeline.config.manualJudgment.judgmentInputs': `
      <p>(Optional) Entries populate a dropdown displayed when performing a manual judgment.</p>
      <p>The selected value can be used in a subsequent <strong>Check Preconditions</strong> stage to determine branching.</p>
      <p>For example, if the user selects "rollback" from this list of options, that branch can be activated by using the expression:
        <samp class="small">execution.stages[n].context.judgmentInput=="rollback"</samp></p>`,
  'pipeline.config.bake.manifest.expectedArtifact': '<p>This is the template you want to render.</p>',
  'pipeline.config.bake.manifest.overrideExpressionEvaluation':
    '<p>Explicitly evaluate SpEL expressions in overrides just prior to manifest baking. Can be paired with the "Skip SpEL evaluation" option in the Deploy Manifest stage when baking a third-party manifest artifact with expressions not meant for Spinnaker to evaluate as SpEL.</p>',
  'pipeline.config.bake.manifest.templateRenderer': '<p>This is the engine used for rendering your manifest.</p>',
  'pipeline.config.bake.manifest.helm.chartFilePath': `
    <p>This is the relative path to the Chart.yaml file within your Git repo.</p>
    <p>e.g.: <b>helm/my-chart/Chart.yaml</b></p>`,
  'pipeline.config.bake.manifest.helm.rawOverrides':
    'Use <i>--set</i> instead of <i>--set-string</i> when injecting override values. Values injected using <i>--set</i> will be converted to primitive types by Helm.',
  'pipeline.config.bake.manifest.kustomize.filePath': `
    <p>This is the relative path to the kustomization.yaml file within your Git repo.</p>
    <p>e.g.: <b>examples/wordpress/mysql/kustomization.yaml</b></p>`,
  'pipeline.config.bake.cf.manifest.name':
    '<p> Name should be the same as the expected artifact in the Produces Artifact section. </p>',
  'pipeline.config.bake.cf.manifest.templateArtifact': `
    <p> This is the manifest template needing resolution. Variables in this template should use double parentheses notation.</p>
    <p>e.g.: </p>
    <p>---</p>
    <p>buildpack: ((javabuildpack)) </p>
    <p>foo: ((some.nestedKey)) </p>`,
  'pipeline.config.bake.cf.manifest.varsArtifact': `
    <p> These are the variables that will be substituted in the manifest template. These should be yaml files and follow standard convention. </p>
    <p>e.g.: </p>
    <p>---</p>
    <p>javabuildpack: java_buildpack_offline </p>
    <p>some: </p>
    <p style="padding-left: 1em">nestedKey: bar </p>`,
  'pipeline.config.haltPipelineOnFailure':
    'Immediately halts execution of all running stages and fails the entire execution.',
  'pipeline.config.haltBranchOnFailure':
    'Prevents any stages that depend on this stage from running, but allows other branches of the pipeline to run.',
  'pipeline.config.haltBranchOnFailureFailPipeline':
    'Prevents any stages that depend on this stage from running, but allows other branches of the pipeline to run. The pipeline will be marked as failed once complete.',
  'pipeline.config.ignoreFailure': 'Continues execution of downstream stages, marking this stage as failed/continuing.',
  'pipeline.config.jenkins.markUnstableAsSuccessful.true':
    'If Jenkins reports the build status as UNSTABLE, Spinnaker will mark the stage as SUCCEEDED and continue execution of the pipeline.',
  'pipeline.config.jenkins.markUnstableAsSuccessful.false': `
      If Jenkins reports the build status as UNSTABLE,
      Spinnaker will mark the stage as FAILED; subsequent execution will be determined based on the configuration of the
      <b>If build fails</b> option for this stage.`,
  'pipeline.config.travis.markUnstableAsSuccessful.true':
    'If Travis reports the build status as UNSTABLE, Spinnaker will mark the stage as SUCCEEDED and continue execution of the pipeline.',
  'pipeline.config.travis.markUnstableAsSuccessful.false': `
      If Travis reports the build status as UNSTABLE,
      Spinnaker will mark the stage as TERMINAL; subsequent execution will be determined based on the configuration of the
      <b>If build fails</b> option for this stage.`,
  'pipeline.config.wercker.markUnstableAsSuccessful.true':
    'If Wercker reports the build status as UNSTABLE, Spinnaker will mark the stage as SUCCEEDED and continue execution of the pipeline.',
  'pipeline.config.wercker.markUnstableAsSuccessful.false': `
      If Wercker reports the build status as UNSTABLE,
      Spinnaker will mark the stage as FAILED; subsequent execution will be determined based on the configuration of the
      <b>If build fails</b> option for this stage.`,
  'pipeline.config.cron.expression':
    '<strong>Format (Year is optional)</strong><p><samp>Seconds  Minutes  Hour  DayOfMonth  Month  DayOfWeek  (Year)</samp></p>' +
    '<p><strong>Example: every 30 minutes</strong></p><samp>0 0/30 * * * ?</samp>' +
    '<p><strong>Example: every Monday at 10 am</strong></p><samp>0 0 10 ? * 2</samp>' +
    '<p><strong>Note:</strong> values for "DayOfWeek" are 1-7, where Sunday is 1, Monday is 2, etc. You can also use MON,TUE,WED, etc.',

  'cluster.rollback.explicit': `
      <p>A server group running the previous build will be enabled and appropriately resized.</p>
      <p>The current server group will be disabled after the resize completes.</p>
  `,
  'cluster.rollback.previous_image': `
      <p>The current server group will be cloned with the previous build.</p>
  `,
  'pipeline.config.findAmi.cluster': 'The cluster to look at when selecting the image to use in this pipeline.',
  'pipeline.config.findAmi.imageNamePattern':
    'A regex used to match the name of the image. Must result in exactly one match to succeed. Empty is treated as match any.',
  'pipeline.config.dependsOn': 'Declares which stages must be run <em>before</em> this stage begins.',
  'pipeline.config.parallel.cancel.queue':
    '<p>If concurrent pipeline execution is disabled, then the pipelines that are in the waiting queue will get canceled when the next execution starts. <br><br>Check this box if you want to keep them in the queue.</p>',
  'pipeline.config.timeout': `
      <p>Allows you to force the stage to fail if its running time exceeds a specific length.</p>
      <p><b>Note:</b> By default, Spinnaker will use sensible timeouts that depend on the stage type and the operations the stage needs to perform at runtime. These defaults can vary based on chosen configuration and other external factors.
      </p>`,
  'pipeline.config.trigger.runAsUser':
    "The current user must have access to the specified service account, and the service account must have access to the current application. Otherwise, you'll receive an 'Access is denied' error.",
  'pipeline.config.trigger.authorizedUser':
    "The current user must have the permission to approve the manual judgment stage. Otherwise, you'll not be able continue to the next pipeline stage.",
  'pipeline.config.script.repoUrl':
    '<p>Path to the repo hosting the scripts in Stash. (e.g. <samp>CDL/mimir-scripts</samp>). Leave empty to use the default.</p>',
  'pipeline.config.script.repoBranch':
    '<p>Git Branch. (e.g. <samp>master</samp>). Leave empty to use the master branch.</p>',
  'pipeline.config.script.path':
    '<p>Path to the folder hosting the scripts in Stash. (e.g. <samp>groovy</samp>, <samp>python</samp> or <samp>shell</samp>)</p>',
  'pipeline.config.script.command':
    '<p>Executable script and parameters. (e.g. <samp>script.py --ami-id ${deploymentDetails[0].ami}</samp> ) </p>',
  'pipeline.config.script.image': '<p>(Optional) image passed down to script execution as IMAGE_ID</p>',
  'pipeline.config.script.account': '<p>(Optional) account passed down to script execution as ENV_PARAM</p>',
  'pipeline.config.script.region': '<p>(Optional) region passed down to script execution as REGION_PARAM</p>',
  'pipeline.config.script.cluster': '<p>(Optional) cluster passed down to script execution as CLUSTER_PARAM</p>',
  'pipeline.config.script.cmc': '<p>(Optional) cmc passed down to script execution as CMC</p>',
  'pipeline.config.script.propertyFile':
    '<p>(Optional) The name to the properties file produced by the script execution to be used by later stages of the Spinnaker pipeline. </p>',
  'pipeline.config.docker.trigger.tag':
    '<p>(Optional) If specified, only the tags that match this Java Regular Expression will be triggered. Leave empty to trigger builds on any tag pushed.</p><p>Builds will not be triggered off the latest tag or updates to existing tags.</p>',
  'pipeline.config.docker.trigger.digest': '<p>The SHA256 hash of the image.</p>',
  'pipeline.config.git.trigger.branch':
    '<p>(Optional) If specified, only pushes to the branches that match this Java Regular Expression will be triggered. Leave empty to trigger builds for every branch.</p>',
  'pipeline.config.git.trigger.githubSecret':
    '<p>(Optional, but recommended) If specified, verifies GitHub as the sender of this trigger. See <a target="_blank" href="https://developer.github.com/webhooks/securing/">GitHub docs</a> for more information.</p>',
  'serverGroup.ipv6': `<p>Associating an IPv6 address to nodes allows for ingress and egress communication over IPv6. Most modern software supports IPv6 and enablement is transparent. This is an incremental addition to IPv4 and will not remove the ability for connectivity over IPv4.</p>`,
  'serverGroupCapacity.useSourceCapacityTrue': `
      <p>Spinnaker will use the current capacity of the existing server group when deploying a new server group.</p>
      <p>This setting is intended to support a server group with auto-scaling enabled, where the bounds and desired capacity are controlled by an external process.</p>
      <p>In the event that there is no existing server group, the deploy will fail.</p>`,
  'serverGroupCapacity.useSourceCapacityFalse':
    '<p>The specified capacity is used regardless of the presence or size of an existing server group.</p>',
  'strategy.redblack.scaleDown': `
      <p>Resizes the target server group to zero instances before disabling it.</p>
      <p>Select this if you wish to retain the launch configuration for the old server group without running any instances.</p>`,
  'strategy.redblack.maxRemainingAsgs': `
      <p><b>Optional</b>: indicates the maximum number of server groups that will remain in this cluster - including the newly created one.</p>
      <p>If you wish to destroy all server groups except the newly created one, select "Highlander" as the strategy.</p>
      <p><strong>Minimum value:</strong> 2</p>`,
  'strategy.redblack.rollback': `
    <p>Disable the new server group and ensure that the previous server group is restored to its original capacity.</p>
    <p>The rollback <strong>will only be</strong> initiated if instances in the new server group fail to launch and become healthy.</p>
    <p>Should an error occur disabling or destroying other server groups in the cluster, the new server group <strong>will not be</strong> rolled back.</p>
  `,
  'strategy.rollingPush.relaunchAll':
    '<p>Incrementally terminates each instance in the server group, waiting for a new one to come up before terminating the next one.</p>',
  'strategy.rollingPush.totalRelaunches': '<p>Total number of instances to terminate and relaunch.</p>',
  'strategy.rollingPush.concurrentRelaunches': '<p>Number of instances to terminate and relaunch at a time.</p>',
  'strategy.rollingPush.concurrentRelaunches.migration': `
    <p>Number of instances to terminate and relaunch at a time.</p>
    <p>Can be expressed as an explicit instance count or as a percentage of instances in server group being migrated.</p>
  `,
  'strategy.rollingPush.order': `
      <p>Determines the order in which instances will be terminated.
      <ul><li><b>Oldest</b> will terminate the oldest instances first</li>
      <li><b>Newest</b> will terminate those most recently launched.</li></ul></p>`,
  'strategy.rollingRedBlack.targetPercentages':
    '<p>Rolling red black will slowly scale up the new server group. It will resize the new server group by each percentage defined.</p>',
  'strategy.rollingRedBlack.rollback':
    '<p>Disable the new server group and ensure that the previous server group is restored to its original capacity.</p>',
  'strategy.monitored.deploySteps':
    '<p>Monitored Deploy will scale up the new server group as specified by these per cent steps. After each step, the health of the new server group will be evaluated by the specified deployment monitor.</p>',
  'strategy.monitored.rollback':
    '<p>If deploy fails, disable the new server group and ensure that the previous server group is active and restored to its original capacity.</p>',
  'strategy.monitored.destroyFailedAsg':
    '<p>If deploy fails and rollback succeeds destroys the server group that failed the deploy instead of just disabling it.</p>',
  'loadBalancers.filter.serverGroups': `
      <p>Displays all server groups configured to use the load balancer.</p>
      <p>If the server group is configured to <em>not</em> add new instances to the load balancer, it will be grayed out.</p>`,
  'loadBalancers.filter.instances': `
      <p>Displays all instances in the context of their parent server group. The color of the instance icon
        indicates <em>only its health in relation to the load balancer</em>. That is, if the load balancer health check reports the instance
        as healthy, the instance will appear green - even if other health indicators (Discovery, other load balancers, etc.) report the instance
        as unhealthy.</p>
      <p>A red icon indicates the instance is failing the health check for the load balancer.</p>
      <p>A gray icon indicates the instance is currently detached from the load balancer.</p>`,
  'loadBalancers.filter.onlyUnhealthy': `
      <p>Filters the list of load balancers and server groups (if enabled)
      to only show load balancers with instances failing the health check for the load balancer.</p>`,
  'project.cluster.stack':
    '<p>(Optional field)</p><p>Filters displayed clusters by stack.</p><p>Enter <samp>*</samp> to include all stacks; leave blank to omit any clusters with a stack.</p><p>Only <samp>*</samp> is valid for Kubernetes V2 accounts.</p>',
  'project.cluster.detail':
    '<p>(Optional field)</p><p>Filters displayed clusters by detail.</p><p>Enter <samp>*</samp> to include all details; leave blank to omit any clusters with a detail.</p><p>Only <samp>*</samp> is valid for Kubernetes V2 accounts.</p>',
  'instanceType.storageOverridden':
    '<p>These storage settings have been cloned from the base server group and differ from the default settings for this instance type.</p>',
  'instanceType.unavailable': '<p>This instance type is not available for the selected configuration.</p>',
  'execution.forceRebake': `
      <p>By default, the bakery will <b>not</b> create a new image if the contents of the package and base image have not changed.
        Instead, it will return the previously baked image, though this behavior is not guaranteed.</p>
      <p>Select this option to force the bakery to create a new image, regardless of whether or not a matching image exists.</p>`,
  'execution.dryRun': `
      <p>Select this option to run the pipeline without <em>really</em> executing anything.</p>
      <p>This is a good way to test parameter-driven behavior, expressions, optional stages, etc.</p>`,
  'user.verification': `
      Typing into this verification field is annoying! But it serves as a reminder that you are
      changing something in an account deemed important, and prevents you from accidentally changing something
      when you meant to click on the "Cancel" button.`,
  'pipeline.waitForCompletion':
    'if unchecked, marks the stage as successful right away without waiting for the pipeline to complete',
  'jenkins.waitForCompletion':
    'if unchecked, marks the stage as successful right away without waiting for the Jenkins job to complete',
  'travis.waitForCompletion':
    'if unchecked, marks the stage as successful right away without waiting for the Travis job to complete',
  'wercker.waitForCompletion':
    'if unchecked, marks the stage as successful right away without waiting for the Wercker job to complete',
  'script.waitForCompletion':
    'if unchecked, marks the stage as successful right away without waiting for the script to complete',
  // eslint-disable-next-line no-useless-escape
  'markdown.examples': `
    Some examples of markdown syntax: <br/> \`*italic*\` <br/> \`**bold**\` <br/> \`[link text](http://url-goes-here)\`
  `,
  'pipeline.config.webhook.payload': 'JSON payload to be added to the webhook call.',
  'pipeline.config.webhook.cancelPayload':
    'JSON payload to be added to the webhook call when it is called in response to a cancellation.',
  'pipeline.config.webhook.waitForCompletion':
    'If not checked, we consider the stage succeeded if the webhook returns an HTTP status code 2xx, otherwise it will be failed. If checked, it will poll a status url (defined below) to determine the progress of the stage.',
  'pipeline.config.webhook.statusUrlResolutionIsGetMethod': "Use the webhook's URL with GET method as status endpoint.",
  'pipeline.config.webhook.statusUrlResolutionIsLocationHeader':
    "Pick the status url from the Location header of the webhook's response call.",
  'pipeline.config.webhook.statusUrlResolutionIsWebhookResponse':
    "Pick the status url from the JSON returned by the webhook's response call.",
  'pipeline.config.webhook.statusUrlJsonPath':
    "JSON path to the status url in the webhook's response JSON. (i.e. <samp>$.buildInfo.url</samp>)",
  'pipeline.config.webhook.retryStatusCodes':
    'Normally, webhook stages only retry on 429 and 5xx status codes. <br>You can specify additional status codes here that will cause the monitor to retry (e.g. <samp>404, 418</samp>)',
  'pipeline.config.webhook.waitBeforeMonitor':
    'Optional delay (in seconds) to wait before starting to poll the endpoint for monitoring status',
  'pipeline.config.webhook.statusJsonPath':
    "JSON path to the status information in the webhook's response JSON (e.g. <samp>$.buildInfo.status</samp>). <br>If left empty, a 200 response from the status endpoint will be treated as a success.",
  'pipeline.config.webhook.progressJsonPath':
    "JSON path to a descriptive message about the progress in the webhook's response JSON. (e.g. <samp>$.buildInfo.progress</samp>)",
  'pipeline.config.webhook.successStatuses':
    'Comma-separated list of strings (that will be returned in the response body in the previously defined `statusJsonPath` field) that will be considered as SUCCESS status.',
  'pipeline.config.webhook.canceledStatuses':
    'Comma-separated list of strings (that will be returned in the response body in the previously defined `statusJsonPath` field) that will be considered as CANCELED status.',
  'pipeline.config.webhook.terminalStatuses':
    'Comma-separated list of strings (that will be returned in the response body in the previously defined `statusJsonPath` field) that will be considered as TERMINAL status.',
  'pipeline.config.webhook.customHeaders': 'Key-value pairs to be sent as additional headers to the service.',
  'pipeline.config.webhook.failFastCodes':
    'Comma-separated HTTP status codes (4xx or 5xx) that will cause this webhook stage to fail without retrying.',
  'pipeline.config.webhook.signalCancellation':
    'Trigger a specific webhook if this stage is cancelled by user or due to pipeline failure',
  'pipeline.config.parameter.label': '(Optional): a label to display when users are triggering the pipeline manually',
  'pipeline.config.parameter.description': `(Optional): if supplied, will be displayed to users as a tooltip
      when triggering the pipeline manually. You can include HTML in this field.`,
  'pipeline.config.parameter.pinned': `(Optional): if checked, this parameter will be always shown in a pipeline execution view, otherwise it'll be collapsed by default.`,
  'pipeline.config.parameter.pinAll': `(Optional): if checked, all parameters will be shown in a pipeline execution view.`,
  'pipeline.config.failOnFailedExpressions': `When this option is enabled, the stage will be marked as failed if it contains any failed expressions`,
  'pipeline.config.roles.help': `
    <p> When the pipeline is triggered using an automated trigger, these roles will be used to decide if the pipeline has permissions to access a protected application or account.</p>
    <ul>
    <li>
    To read from a protected application or account, the pipeline must have at least one role that has read access to the application or account.
    </li>
    <li>
    To write to a protected application or account, the pipeline must have at least one role that has write access to the application or account.
    </li>
    </ul>
    <p><strong>Note:</strong> To prevent privilege escalation vulnerabilities, a user must be a member of <strong>all</strong> of the groups specified here in order to modify, and execute the pipeline.</p>`,
  'pipeline.config.entitytags.namespace': `All tags have an associated namespace (<strong>default</strong> will be used if unspecified) that provides a means of grouping tags by a logical owner.`,
  'pipeline.config.entitytags.value': `Value can either be a string or an object. If you want to use an object, input a valid JSON string.`,
  'pipeline.config.entitytags.region': `(Optional) Target a specific region, use * if you want to apply to all regions.`,
  'pipeline.config.deliveryConfig.manifest': `(Optional) Name of the file with your Delivery Config manifest. Leave blank to use the default name (<strong><i>${SETTINGS.managedDelivery?.defaultManifest}</i></strong>).`,
  'pipeline.config.codebuild.source': `(Optional) Source of the build. It will be overridden to Spinnaker artifact if checked. If not checked, source configured in CodeBuild project will be used.`,
  'pipeline.config.codebuild.sourceType': `(Optional) Type of the source. It can be specified explicitly; otherwise, it will be inferred from source artifact.`,
  'pipeline.config.codebuild.sourceVersion': `(Optional) Source version of the build. If not specified, the artifact version will be used. If artifact doesn't have a version, the latest version will be used. See the <a href="https://docs.aws.amazon.com/codebuild/latest/APIReference/API_StartBuild.html#CodeBuild-StartBuild-request-sourceVersion">CodeBuild reference</a> for more information.`,
  'pipeline.config.codebuild.buildspec': `(Optional) Inline buildspec definition of the build. If not specified, buildspec configured in CodeBuild project will be used.`,
  'pipeline.config.codebuild.secondarySources': `(Optional) Secondary sources of the build. It can be overridden by adding Spinnaker Artifacts. If not specified, secondary sources configured in CodeBuild project will be used.`,
  'pipeline.config.codebuild.secondarySourcesVersionOverride': `(Optional) Existing secondary sources version overrides. Pre-defined secondary source's version can be overriden by specifying the source identifier and version. If not specified, secondary sources configured in CodeBuild project will be used.`,
  'pipeline.config.codebuild.sourceIdentifier': `(Optional) Name of a pre-defined secondary source.`,
  'pipeline.config.codebuild.secondarySourceVersion': `Version of secondary source identifier to use. Required if specifying a source identifier.`,
  'pipeline.config.codebuild.image': `(Optional) Image in which the build will run. It can be overridden by specifying the name of the image. If not specified, image configured in CodeBuild project will be used.`,
  'pipeline.config.codebuild.envVar': `(Optional) Environment variables that will be propagated into the build.`,
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
