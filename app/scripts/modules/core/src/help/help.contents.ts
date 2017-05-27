import { module } from 'angular';

export interface IHelpContents {
  [key: string]: string;
}

export const HELP_CONTENTS = 'spinnaker.core.help.contents';
module(HELP_CONTENTS, [])
  .constant('helpContents', {
    'core.serverGroup.strategy': 'The deployment strategy tells Spinnaker what to do with the previous version of the server group.',
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
        Quickly filter the displayed security groups by the following fields:
        <ul>
          <li>VPC (prefixed, e.g. <samp>vpc:main</samp>)
          <li>Security Group Name</li>
          <li>Server Group Name</li>
          <li>Load Balancer Name</li>
          <li>Region</li>
          <li>Account</li>
        </ul>
        <p>You can search for multiple words or word fragments. For instance, to find all security groups in a prod stack with "canary" in the details, enter <samp>prod canary</samp>.</p>`,
    'executions.search': `
        Quickly filter the displayed executions by the following fields:
        <ul>
          <li>Name</li>
          <li>Trigger</li>
          <li>Context - server groups, bakery results, etc.</li>
        </ul>`,
    'pipeline.config.lock.allowUnlockUi': `
        <p><strong>Checked</strong> - the pipeline can be unlocked via the Spinnaker UI.</p>
        <p><strong>Unchecked</strong> - the pipeline can only be unlocked via the Spinnaker API.</p>`,
    'pipeline.config.lock.description': `
        <p>Friendly description of why this pipeline is locked.</p>
        <p>Please include an email address or slack channel as appropriate.</p>`,
    'pipeline.config.optionalStage': `
        <p>When this option is enabled, stage will only execute when the supplied expression evaluates true.</p>
        <p>The expression <em>does not</em> need to be wrapped in \${ and }.</p>`,
    'pipeline.config.checkPreconditions.failPipeline': `
        <p><strong>Checked</strong> - the overall pipeline will fail whenever this precondition is false.</p>
        <p><strong>Unchecked</strong> - the overall pipeline will continue executing but this particular branch will stop.</p>`,
    'pipeline.config.checkPreconditions.expectedSize': 'Number of server groups in the selected cluster',
    'pipeline.config.checkPreconditions.expression': `
        <p>Value must evaluate to "true".</p>
        <p>Use of the <b>Spring Expression Language</b> allows for complex evaluations.</p>`,
    'pipeline.config.deploy.template': `
        <p>Select an existing cluster to use as a template for this deployment, and we'll pre-fill
        the configuration based on the newest server group in the cluster.</p>
        <p>If you want to start from scratch, select "None".</p>
        <p>You can always edit the cluster configuration after you've created it.</p>`,

    'loadBalancer.advancedSettings.healthTimeout': '<p>Configures the timeout, in seconds, for reaching the healthCheck target.</p><p> Default: <b>5</b></p>',
    'loadBalancer.advancedSettings.healthInterval': '<p>Configures the interval, in seconds, between ELB health checks.</p><p>Default: <b>10</b></p>',
    'loadBalancer.advancedSettings.healthyThreshold': '<p>Configures the number of healthy observations before reinstituting an instance into the ELB’s traffic rotation.</p><p>Default: <b>10</b></p>',
    'loadBalancer.advancedSettings.unhealthyThreshold': '<p>Configures the number of unhealthy observations before deservicing an instance from the ELB.</p><p>Default: <b>2</b></p>',
    'pipeline.config.resizeAsg.action': `
        <p>Configures the resize action for the target server group.
        <ul>
          <li><b>Scale Up</b> increases the size of the target server group by an incremental or percentage amount</li>
          <li><b>Scale Down</b> decreases the size of the target server group by an incremental or percentage amount</li>
          <li><b>Scale to Cluster Size</b> increases the size of the target server group to match the largest server group in the cluster, optionally with an incremental or percentage additional capacity. Additional capacity will not exceed the existing maximum size.</li>
          <li><b>Scale to Exact Size</b> adjusts the size of the target server group to match the provided capacity</li>
        </ul></p>`,
    'pipeline.config.resizeAsg.cluster': '<p>Configures the cluster upon which this resize operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.modifyScalingProcess.cluster': '<p>Configures the cluster upon which this modify scaling process operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.enableAsg.cluster': '<p>Configures the cluster upon which this enable operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.disableAsg.cluster': '<p>Configures the cluster upon which this disable operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.destroyAsg.cluster': '<p>Configures the cluster upon which this destroy operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.jenkins.propertyFile': '<p>(Optional) Configures the name to the Jenkins artifact file used to pass in properties to later stages in the Spinnaker pipeline.</p>',
    'pipeline.config.travis.job.isFiltered': '<p>Note that for performance reasons, not all jobs are displayed. Please use the search field to limit the number of jobs.</p>',
    'pipeline.config.travis.propertyFile': '<p>(Optional) Configures the name to the Travis artifact file used to pass in properties to later stages in the Spinnaker pipeline.</p>',
    'pipeline.config.bake.package': `
        <p>The name of the package you want installed (without any version identifiers).</p>
        <p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>
        <p>If there are multiple packages (space separated), then they will be installed in the order they are entered.</p>`,
    'pipeline.config.docker.bake.targetImage': '<p>The name of the resulting docker image.</p>',
    'pipeline.config.docker.bake.targetImageTag': '<p>The tag of the resulting docker image, defaults to commit hash if available.</p>',
    'pipeline.config.docker.bake.organization': '<p>The name of the organization or repo to use for the resulting docker image.</p>',
    'pipeline.config.bake.baseAmi': '<p>(Optional) ami-????????</p>',
    'pipeline.config.bake.amiSuffix': '<p>(Optional) String of date in format YYYYMMDDHHmm, default is calculated from timestamp,</p>',
    'pipeline.config.bake.amiName': '<p>(Optional) Default = $package-$arch-$ami_suffix-$store_type</p>',
    'pipeline.config.bake.templateFileName': '<p>(Optional) The explicit packer template to use, instead of resolving one from rosco\'s configuration.</p>',
    'pipeline.config.bake.varFileName': '<p>(Optional) The name of a json file containing key/value pairs to add to the packer command.</p>',
    'pipeline.config.bake.extendedAttributes': '<p>(Optional) Any additional attributes that you want to pass onto rosco, which will be injected into your packer runtime variables.</p>',
    'pipeline.config.manualJudgment.instructions': '<p>(Optional) Instructions are shown to the user when making a manual judgment.</p><p>May contain HTML.</p>',
    'pipeline.config.manualJudgment.propagateAuthentication': `
        <p><strong>Checked</strong> - the pipeline will continue with the permissions of the approver.</p>
        <p><strong>Unchecked</strong> - the pipeline will continue with its current permissions.</p>
          pipeline.config.manualJudgment.judgmentInputs': '<p>(Optional) Entries populate a dropdown displayed when performing a manual judgment.</p>
        <p>The selected value can be used in a subsequent <strong>Check Preconditions</strong> stage to determine branching.</p>
        <p>For example, if the user selects "rollback" from this list of options, that branch can be activated by using the expression:
          <samp class="small">execution.stages[n].context.judgmentInput=="rollback"</samp></p>`,
    'pipeline.config.haltPipelineOnFailure': 'Immediately halts execution of all running stages and fails the entire execution.',
    'pipeline.config.haltBranchOnFailure': 'Prevents any stages that depend on this stage from running, but allows other branches of the pipeline to run.',
    'pipeline.config.haltBranchOnFailureFailPipeline': 'Prevents any stages that depend on this stage from running, but allows other branches of the pipeline to run. The pipeline will be marked as failed once complete.',
    'pipeline.config.ignoreFailure': 'Continues execution of downstream stages, marking this stage as failed/continuing.',
    'pipeline.config.jenkins.markUnstableAsSuccessful.true': 'If Jenkins reports the build status as UNSTABLE, Spinnaker will mark the stage as SUCCEEDED and continue execution of the pipeline.',
    'pipeline.config.jenkins.markUnstableAsSuccessful.false': `
        If Jenkins reports the build status as UNSTABLE,
        Spinnaker will mark the stage as FAILED; subsequent execution will be determined based on the configuration of the
        <b>If build fails</b> option for this stage.`,
    'pipeline.config.travis.markUnstableAsSuccessful.true': 'If Travis reports the build status as UNSTABLE, Spinnaker will mark the stage as SUCCEEDED and continue execution of the pipeline.',
    'pipeline.config.travis.markUnstableAsSuccessful.false': `
        If Travis reports the build status as UNSTABLE,
        Spinnaker will mark the stage as TERMINAL; subsequent execution will be determined based on the configuration of the
        <b>If build fails</b> option for this stage.`,
    'pipeline.config.cron.expression': '<strong>Format (Year is optional)</strong><p><samp>Seconds  Minutes  Hour  DayOfMonth  Month  DayOfWeek  (Year)</samp></p>' +
    '<p><strong>Example: every 30 minutes</strong></p><samp>0 0/30 * * * ?</samp>' +
    '<p><strong>Example: every Monday at 10 am</strong></p><samp>0 0 10 ? * 2</samp>' +
    '<p><strong>Note:</strong> values for "DayOfWeek" are 1-7, where Sunday is 1, Monday is 2, etc. You can also use MON,TUE,WED, etc.',

    'cluster.description': '<p>A cluster is a collection of server groups with the same name (stack + detail) in the same account.</p>',
    'pipeline.config.findAmi.cluster': 'The cluster to look at when selecting the image to use in this pipeline.',
    'pipeline.config.findAmi.imageNamePattern': 'A regex used to match the name of the image. Must result in exactly one match to succeed. Empty is treated as match any.',
    'pipeline.config.dependsOn': 'Declares which stages must be run <em>before</em> this stage begins.',
    'pipeline.config.parallel.cancel.queue': '<p>If concurrent pipeline execution is disabled, then the pipelines that are in the waiting queue will get canceled by default. <br><br>Check this box if you want to keep them in the queue.</p>',
    'pipeline.config.timeout': `
        <p>Allows you to override the amount of time the stage can run before failing.</p>
        <p><b>Note:</b> this is not the overall time the stage has, but rather the time for specific tasks.</p>`,
    'pipeline.config.timeout.bake': '<p>For the Bake stage, the timeout will apply to both the "Create Bake" and "Monitor Bake" tasks.</p>',
    'pipeline.config.timeout.deploy': '<p>For the Deploy stage, the timeout will apply to both the "Monitor Deploy" and "Wait For Up Instances" tasks.</p>',
    'pipeline.config.timeout.jenkins': '<p>For the Jenkins stage, the timeout will apply to both the "Wait For Jenkins Job Start" and "Monitor Jenkins Job" tasks.</p>',
    'pipeline.config.trigger.runAsUser': 'The current user must have access to the specified service account, and the service account must have access to the current application. Otherwise, you\'ll receive an \'Access is denied\' error.',
    'pipeline.config.script.repoUrl': '<p>Path to the repo hosting the scripts in Stash. (e.g. <samp>CDL/mimir-scripts</samp>). Leave empty to use the default.</p>',
    'pipeline.config.script.repoBranch': '<p>Git Branch. (e.g. <samp>master</samp>). Leave empty to use the master branch.</p>',
    'pipeline.config.script.path': '<p>Path to the folder hosting the scripts in Stash. (e.g. <samp>groovy</samp>, <samp>python</samp> or <samp>shell</samp>)</p>',
    'pipeline.config.script.command': '<p>Executable script and parameters. (e.g. <samp>script.py --ami-id ${deploymentDetails[0].ami}</samp> ) </p>',
    'pipeline.config.script.image': '<p>(Optional) image passed down to script execution as IMAGE_ID</p>',
    'pipeline.config.script.account': '<p>(Optional) account passed down to script execution as ENV_PARAM</p>',
    'pipeline.config.script.region': '<p>(Optional) region passed down to script execution as REGION_PARAM</p>',
    'pipeline.config.script.cluster': '<p>(Optional) cluster passed down to script execution as CLUSTER_PARAM</p>',
    'pipeline.config.script.cmc': '<p>(Optional) cmc passed down to script execution as CMC</p>',
    'pipeline.config.script.propertyFile': '<p>(Optional) The name to the properties file produced by the script execution to be used by later stages of the Spinnaker pipeline. </p>',
    'pipeline.config.docker.trigger.tag': '<p>(Optional) If specified, only the tags that match this Java Regular Expression will be triggered. Leave empty to trigger builds on any tag pushed.</p>',
    'pipeline.config.git.trigger.branch': '<p>(Optional) If specified, only pushes to the branches that match this Java Regular Expression will be triggered. Leave empty to trigger builds for every branch.</p>',
    'pipeline.config.git.trigger.githubSecret': '<p>(Optional, but recommended) If specified, verifies GitHub as the sender of this trigger. See <a target="_blank" href="https://developer.github.com/webhooks/securing/">GitHub docs</a> for more information.</p>',
    'serverGroupCapacity.useSourceCapacityTrue': `
        <p>Spinnaker will use the current capacity of the existing server group when deploying a new server group.</p>
        <p>This setting is intended to support a server group with auto-scaling enabled, where the bounds and desired capacity are controlled by an external process.</p>
        <p>In the event that there is no existing server group, the deploy will fail.</p>`,
    'serverGroupCapacity.useSourceCapacityFalse': '<p>The specified capacity is used regardless of the presence or size of an existing server group.</p>',
    'strategy.redblack.scaleDown': `
        <p>Resizes the target server group to zero instances before disabling it.</p>
        <p>Select this if you wish to retain the launch configuration for the old server group without running any instances.</p>`,
    'strategy.redblack.maxRemainingAsgs': `
        <p><b>Optional</b>: indicates the maximum number of server groups that will remain in this cluster - including the newly created one.</p>
        <p>If you wish to destroy all server groups except the newly created one, select "Highlander" as the strategy.</p>
        <p><strong>Minimum value:</strong> 2</p>`,
    'strategy.rollingPush.relaunchAll': '<p>Incrementally terminates each instance in the server group, waiting for a new one to come up before terminating the next one.</p>',
    'strategy.rollingPush.totalRelaunches': '<p>Total number of instances to terminate and relaunch.</p>',
    'strategy.rollingPush.concurrentRelaunches': '<p>Number of instances to terminate and relaunch at a time.</p>',
    'strategy.rollingPush.order': `
        <p>Determines the order in which instances will be terminated.
        <ul><li><b>Oldest</b> will terminate the oldest instances first</li>
        <li><b>Newest</b> will terminate those most recently launched.</li></ul></p>`,

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
    'project.cluster.stack': '<p>(Optional field)</p><p>Filters displayed clusters by stack.</p><p>Enter <samp>*</samp> to include all stacks; leave blank to omit any clusters with a stack.</p>',
    'project.cluster.detail': '<p>(Optional field)</p><p>Filters displayed clusters by detail.</p><p>Enter <samp>*</samp> to include all details; leave blank to omit any clusters with a detail.</p>',
    'instanceType.storageOverridden': '<p>These storage settings have been cloned from the base server group and differ from the default settings for this instance type.</p>',
    'instanceType.unavailable': '<p>This instance type is not available for the selected configuration.</p>',
    'execution.forceRebake': `
        <p>By default, the bakery will <b>not</b> create a new image if the contents of the package have not changed;
          instead, it will return the previously baked image.</p>
        <p>Select this option to force the bakery to create a new image, regardless of whether or not the selected package exists.</p>`,
    'user.verification': `
        Typing into this verification field is annoying! But it serves as a reminder that you are
        changing something in an account deemed important, and prevents you from accidentally changing something
        when you meant to click on the "Cancel" button.`,
    'pipeline.waitForCompletion': 'if unchecked, marks the stage as successful right away without waiting for the pipeline to complete',
    'jenkins.waitForCompletion': 'if unchecked, marks the stage as successful right away without waiting for the jenkins job to complete',
    'travis.waitForCompletion': 'if unchecked, marks the stage as successful right away without waiting for the Travis job to complete',
    'script.waitForCompletion': 'if unchecked, marks the stage as successful right away without waiting for the script to complete',
    'markdown.examples': 'Some examples of markdown syntax: <br/> *<em>emphasis</em>* <br/> **<b>strong</b>** <br/> [link text](http://url-goes-here)',
    'pipeline.config.webhook.payload': 'JSON payload to be added to the webhook call.',
    'pipeline.config.webhook.waitForCompletion': 'If not checked, we consider the stage succeeded if the webhook returns an HTTP status code 2xx, otherwise it will be failed. If checked, it will poll a status url (defined below) to determine the progress of the stage.',
    'pipeline.config.webhook.statusUrlResolutionIsGetMethod': 'Use the webhook\'s URL with GET method as status endpoint.',
    'pipeline.config.webhook.statusUrlResolutionIsLocationHeader': 'Pick the status url from the Location header of the webhook\'s response call.',
    'pipeline.config.webhook.statusUrlResolutionIsWebhookResponse': 'Pick the status url from the JSON returned by the webhook\'s response call.',
    'pipeline.config.webhook.statusUrlJsonPath': 'JSON path to the status url in the webhook\'s response JSON. (i.e. <samp>$.buildInfo.url</samp>)',
    'pipeline.config.webhook.statusJsonPath': 'JSON path to the status information in the webhook\'s response JSON. (e.g. <samp>$.buildInfo.status</samp>)',
    'pipeline.config.webhook.progressJsonPath': 'JSON path to a descriptive message about the progress in the webhook\'s response JSON. (e.g. <samp>$.buildInfo.progress</samp>)',
    'pipeline.config.webhook.successStatuses': 'Comma-separated list of strings that will be considered as SUCCESS status.',
    'pipeline.config.webhook.canceledStatuses': 'Comma-separated list of strings that will be considered as CANCELED status.',
    'pipeline.config.webhook.terminalStatuses': 'Comma-separated list of strings that will be considered as TERMINAL status.',
    'pipeline.config.webhook.customHeaders': 'Key-value pairs to be sent as additional headers to the service.',
  });
