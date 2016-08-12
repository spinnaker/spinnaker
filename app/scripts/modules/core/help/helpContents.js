'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.help.contents', [])
  .constant('helpContents', {
    'application.platformHealthOnly': 'When this option is enabled, instance status as reported by the cloud provider will be considered sufficient to ' +
      'determine task completion. When this option is disabled, tasks will normally need health status reported by some other health provider (e.g. a ' +
      'load balancer or discovery service) to determine task completion.',
    'application.showPlatformHealthOverride': 'When this option is enabled, users will be able to toggle the option above on a task-by-task basis.',
    'core.serverGroup.strategy': 'The deployment strategy tells Spinnaker what to do with the previous version of the server group.',
    'aws.associateElasticIp.elasticIp': '<p>(Optional) <b>Elastic IP</b> is an IP address that Spinnaker will associate with this cluster.' +
      '<p>If specified, this elastic IP must exist and not already be attached to an instance or cluster.</p>' +
      '<p>If left blank, Spinnaker will make a selection from the list of available elastic IPs in the provided account and region.</p>',
    'aws.associateElasticIp.type': '<p><b>Type</b> of elastic IP to associate:' +
      '<ul>' +
      '<li><b>standard</b> is usable in EC2 Classic</li>' +
      '<li><b>vpc</b> is usable in VPC</li>' +
      '</ul>',
    'aws.serverGroup.subnet': 'The subnet selection determines the VPC in which your server group will run. Options vary by account and region; the most common ones are:' +
      '<ul>' +
      '<li><b>None (EC2 Classic)</b>: instances will not run in a VPC</li>' +
      '<li><b>internal</b> instances will be restricted to internal clients (i.e. require VPN access)</li>' +
      '<li><b>external</b> instances will be publicly accessible and running in VPC</li>' +
      '</ul>',
    'aws.loadBalancer.subnet': 'The subnet selection determines the VPC in which your load balancer will run.<br/>' +
      ' This also restricts the server groups which can be serviced by the load balancer.' +
      ' Options vary by account and region; the most common ones are:' +
      '<ul>' +
      '<li><b>None (EC2 Classic)</b>: the load balancer will not run in a VPC</li>' +
      '<li><b>internal</b> access to the load balancer will be restricted to internal clients (i.e. require VPN access)</li>' +
      '<li><b>external</b> the load balancer will be publicly accessible and running in VPC</li>' +
      '</ul>',
    'aws.loadBalancer.detail': '<p>(Optional) <b>Detail</b> is a string of free-form alphanumeric characters; by convention, we recommend using "frontend".</p><p>' +
      'However, if your stack name needs to be longer (load balancer names are limited to 32 characters), consider changing it to "elb", or omit it altogether.</p>',
    'aws.loadBalancer.stack': '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
    'aws.serverGroup.stack': '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
    'aws.serverGroup.detail': '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.',
    'aws.serverGroup.imageName': '(Required) <b>Image</b> is the deployable Amazon Machine Image. Images are restricted to the account and region selected.',
    'aws.serverGroup.legacyUdf': '<p>(Optional) <b>User Data Format</b> allows overriding of the format used when generating user data during deployment. The default format used is configured ' +
                                 'in the application\'s attributes, editable via the \'Config\' tab.</p>' +
                                 '<p><b>Default</b> will use the value from the application\'s configuration.</p>' +
                                 '<p><b>Modern</b> will use the modern template.</p>' +
                                 '<p><b>Legacy</b> will use the legacy (<b>deprecated</b>) template.</p>' +
                                 '<p>This option is intended to allow testing migration from legacy to modern before configuring it for the entire application. If unsure, pick <b>Default</b>.</p>',
    'aws.serverGroup.base64UserData': '(Optional) <b>UserData</b> is a base64 encoded string.',
    'aws.serverGroup.tags': '(Optional) <b>Tags</b> are propagated to the instances in this cluster.',
    'aws.serverGroup.allImages': 'Search for an image that does not match the name of your application.',
    'aws.serverGroup.filterImages': 'Select from a pre-filtered list of images matching the name of your application.',
    'aws.serverGroup.traffic': 'Enables the "AddToLoadBalancer" scaling process, which is used by Spinnaker and ' +
    ' discovery services to determine if the server group is enabled.',
    'aws.securityGroup.vpc': '<p>The VPC to which this security group will apply.</p>' +
      '<p>If you wish to use VPC but are unsure which VPC to use, the most common one is "Main".</p>' +
      '<p>If you do not wish to use VPC, select "None".</p>',
    'aws.scalingPolicy.search.restricted': '<p>Resets dimensions to "AutoScalingGroupName: {name of the ASG}" and provides' +
    ' a simpler, combined input for the namespace and metric name fields.</p>',
    'aws.scalingPolicy.search.all': '<p>Allows you to edit the dimensions and namespace to find a specific metric for' +
    ' this alarm.</p>',
    'cf.artifact.repository.options': '<p>You may include {job} and {buildNumber} to dynamically build a path to your artifact.</p>',
    'cluster.search': 'Quickly filter the displayed server groups by the following fields:' +
      '<ul>' +
      '<li>Build # (e.g. <samp>#337</samp>)</li>' +
      '<li>Jenkins host</li>' +
      '<li>Jenkins job name</li>' +
      '<li>Cluster (prefixed, e.g. <samp>cluster:myapp-int</samp>)' +
      '<li>VPC (prefixed, e.g. <samp>vpc:main</samp>)' +
      '<li>Clusters (comma-separated list, e.g. <samp>clusters:myapp-int, myapp-test</samp>)' +
      '<li>Server Group Name</li>' +
      '<li>Region</li>' +
      '<li>Account</li>' +
      '<li>Load Balancer Name</li>' +
      '<li>Instance ID</li>' +
      '</ul>' +
      '<p>You can search for multiple words or word fragments. For instance, to find all server groups in a prod stack with "canary" in the details' +
      ', enter <samp>prod canary</samp>.</p>' +
      '<p>To find a particular instance, enter the instance ID. Only the containing server group will be displayed, and the instance ' +
      'will be highlighted for you.</p>',
    'loadBalancer.search': 'Quickly filter the displayed load balancers by the following fields:' +
      '<ul>' +
      '<li>VPC (prefixed, e.g. <samp>vpc:main</samp>)' +
      '<li>Server Group Name</li>' +
      '<li>Load Balancer Name</li>' +
      '<li>Region</li>' +
      '<li>Account</li>' +
      '<li>Instance ID</li>' +
      '</ul>' +
      '<p>You can search for multiple words or word fragments. For instance, to find all load balancers in a prod stack with "canary" in the details' +
      ', enter <samp>prod canary</samp>.</p>',
    'securityGroup.search': 'Quickly filter the displayed security groups by the following fields:' +
      '<ul>' +
      '<li>VPC (prefixed, e.g. <samp>vpc:main</samp>)' +
      '<li>Security Group Name</li>' +
      '<li>Server Group Name</li>' +
      '<li>Load Balancer Name</li>' +
      '<li>Region</li>' +
      '<li>Account</li>' +
      '</ul>' +
      '<p>You can search for multiple words or word fragments. For instance, to find all security groups in a prod stack with "canary" in the details' +
      ', enter <samp>prod canary</samp>.</p>',
    'executions.search': 'Quickly filter the displayed executions by the following fields:' +
      '<ul>' +
      '<li>Name</li>' +
      '<li>Trigger</li>' +
      '<li>Context - server groups, bakery results, etc.</li>' +
      '</ul>',
    'gce.instance.customInstance.cores': '<ul><li>Above 1, vCPU count must be even.</li><li>Zones that support Haswell and Ivy Bridge processors can support custom machine types up to 32 vCPUs.</li><li>Zones that support Sandy Bridge processors can support up to 16 vCPUs.</li></ul>',
    'gce.instance.customInstance.memory': '<ul><li>Memory per vCPU must be between .9 GB and 6.5 GB.</li><li>Total memory must be a multiple of 256 MB.</li></ul>',
    'gce.instance.customMetadata.instance-template': 'The instance template used to configure this instance.',
    'gce.instance.customMetadata.load-balancer-names': 'This field is used to "remember" what load balancers this instance is associated with, even if it is deregistered.',
    'gce.instance.customMetadata.global-load-balancer-names': 'This field is used to "remember" what global load balancers this instance is associated with, even if it is deregistered.',
    'gce.instance.customMetadata.backend-service-names': 'This field is used to "remember" what backend services this instance is associated with, even if it is deregistered.',
    'gce.instance.customMetadata.load-balancing-policy': 'This field is used to "remember" the load balancing policy this instance is associated with, even if it is deregistered.',
    'gce.instance.customMetadata.startup-script': 'This script will run automatically on every boot.',
    'gce.instance.storage': '<p>Storage options can be fully-configured on the <b>Advanced Settings</b> tab. These are just default settings. Please be aware of your Local SSD quotas as you provision VMs.</p>',
    'gce.instance.storage.localSSD': '<p>Local SSD disks are always 375GB. All non shared-core instance types support attaching up to 4 Local SSD disks. Please be aware of your Local SSD quotas as you provision VMs.</p>',
    'gce.instance.serviceAccount': '<p>Service accounts authenticate applications running on your virtual machine instances to other Google Cloud Platform services. Valid values are either "default" or the full email address of a custom service account.</p>',
    'gce.instance.authScopes': '<p>Service account scopes specify which Google Cloud Platform APIs your instances can authenticate with, and define the level of access that your instances have with those services.</p>',
    'gce.instance.authScopes.cloud-platform': '<p>The instances in this server group have full API access to all Google Cloud services.</p>',
    'gce.instanceType.32core': '<p>32-core machine types are in Beta and are available only in Ivy Bridge and Haswell zones.</p><p>They are not available in these locations:<ul><li>us-central1-a</li><li>europe-west1-b</li><li>europe-west1 (when deploying regionally)</li></p>',
    'gce.loadBalancer.detail': '<p>(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.</p>',
    'gce.loadBalancer.advancedSettings.healthInterval': '<p>Configures the interval, in seconds, between load balancer health checks.</p><p>Default: <b>10</b></p>',
    'gce.loadBalancer.advancedSettings.healthyThreshold': '<p>Configures the number of healthy observations before reinstituting an instance into the load balancer’s traffic rotation.</p><p>Default: <b>10</b></p>',
    'gce.loadBalancer.advancedSettings.unhealthyThreshold': '<p>Configures the number of unhealthy observations before deservicing an instance from the load balancer.</p><p>Default: <b>2</b></p>',
    'gce.loadBalancer.healthCheck': '(Optional) <b>Health Checks</b> use HTTP requests to determine if a VM instance is healthy.',
    'gce.loadBalancer.portRange': '(Optional) Only packets addressed to ports in the specified <b>Port Range</b> will be forwarded. If left empty, all ports are forwarded. Must be a single port number or two port numbers separated by a dash. Each port number must be between 1 and 65535, inclusive. For example: 5000-5999.',
    'gce.serverGroup.scalingPolicy.coolDownPeriodSec': 'How long to wait before collecting information from a new instance. This should be at least the time it takes to initialize the instance.',
    'gce.serverGroup.scalingPolicy.cpuUtilization' : 'Autoscaler adds or removes instances to maintain this CPU usage on each instance.',
    'gce.serverGroup.scalingPolicy.loadBalancingUtilization' : 'Autoscaler adds or removes instances to maintain this usage of load-balancing capacity.',
    'gce.serverGroup.scalingPolicy.customMetricUtilizations' : 'Autoscaler adds or removes instances to maintain this usage for custom metric.',
    'gce.serverGroup.imageName': '(Required) <b>Image</b> is the Google Compute Engine image. Images are restricted to the account selected.',
    'gce.serverGroup.capacity': 'The number of instances that the instance group manager will attempt to maintain. Deleting or abandoning instances will affect this number, as will resizing the group.',
    'gce.serverGroup.customMetadata': '<b>Custom Metadata</b> will be propagated to the instances in this server group. This is useful for passing in arbitrary values that can be queried by your code on the instance.',
    'gce.serverGroup.customMetadata.load-balancer-names': 'This field is used to "remember" what load balancers this server group is associated with, even if the instances are deregistered.',
    'gce.serverGroup.customMetadata.global-load-balancer-names': 'This field is used to "remember" what global load balancers this server group is associated with, even if the instances are deregistered.',
    'gce.serverGroup.customMetadata.backend-service-names': 'This field is used to "remember" what backend services this server group is associated with, even if the instances are deregistered.',
    'gce.serverGroup.customMetadata.load-balancing-policy': 'This field is used to "remember" the load balancing policy this server group is configured with, even if the server group is deregistered from the load balancer. This allows us to re-enable the server group with the same load balancing policy specified on creation.',
    'gce.serverGroup.customMetadata.startup-script': 'This script will run automatically on every boot.',
    'gce.serverGroup.preemptibility': 'A preemptible VM costs much less, but lasts only 24 hours. It can be terminated sooner due to system demands.',
    'gce.serverGroup.automaticRestart': 'Compute Engine can automatically restart VM instances if they are terminated for non-user-initiated reasons (maintenance event, hardware failure, software failure, etc.).',
    'gce.serverGroup.onHostMaintenance': 'When Compute Engine performs periodic infrastructure maintenance it can migrate your VM instances to other hardware without downtime.',
    'gce.serverGroup.securityGroups.implicit': 'Firewall rules with no target tags defined will permit incoming connections that match the ingress rules to all instances in the network.',
    'gce.serverGroup.autoscaling.targetCPUUsage': 'Autoscaling adds or removes VMs in the group to maintain this level of CPU usage on each VM.',
    'gce.serverGroup.autoscaling.targetHTTPLoadBalancingUsage': 'Autoscaling adds or removes VMs in the group to maintain this usage of load-balancing capacity. This value is a percentage of the \'Maximum rate\' setting of the load balancer this group is used by.',
    'gce.serverGroup.autoscaling.targetMetric': 'Autoscaling adds or removes VMs in the group to maintain these target levels.',
    'gce.serverGroup.autoscaling.minVMs': 'The least number of VM instances the group will contain, even if the target is not met.',
    'gce.serverGroup.autoscaling.maxVMs': 'The largest number of VM instances allowed, even if the target is exceeded.',
    'gce.serverGroup.autoscaling.cooldown': 'How long to wait before collecting information from a new instance. This should be at least the time it takes to initialize the instance. To find the minimum, create an instance from the same image and note how long it takes to start.',
    'gce.serverGroup.autoHealing': 'VMs in the group are recreated as needed. You can use a health check to recreate a VM if the health check finds the VM unresponsive. If you do not select a health check, VMs are recreated only when stopped.',
    'gce.serverGroup.initialDelaySec': 'The time to allow an instance to boot and applications to fully start before the first health check.',
    'gce.serverGroup.subnet': 'Subnetworks allow you to regionally segment the network IP space into prefixes (subnets) and control which prefix a VM instance\'s internal IP address is allocated from. There are several types of GCE networks:' +
      '<ul>' +
      '<li><b>Legacy (non-subnet) Network</b>: IP address allocation occurs at the global network level. This means the network address space spans across all regions.</li>' +
      '<li><b>Auto Subnet Network</b>: Server groups will be automatically assigned to the specified region\'s subnet.</li>' +
      '<li><b>Custom Subnet Network</b>: A subnet must be selected for the server group. If no subnets have been created for the specified region, you will not be able to provision the server group.</li>' +
      '</ul>',
    'gce.serverGroup.loadBalancingPolicy.balancingMode': 'Tells the load balancer when the backend is at capacity. If a backend is at capacity, new requests are routed to the nearest region that can handle requests. The balancing mode can be based on CPU utilization or requests per second (RPS).',
    'gce.serverGroup.loadBalancingPolicy.maxRatePerInstance': 'The maximum number of requests per second that can be sent to the backend instance group. Input must be a number greater than zero.',
    'gce.serverGroup.loadBalancingPolicy.maxUtilization': 'The maximum CPU utilization allowed for the backend. CPU utilization is calculated by averaging CPU use across all instances in the backend instance group. Input must be a number between 0 and 100.',
    'gce.serverGroup.loadBalancingPolicy.capacityScaler': `
      An additional control to manage your maximum CPU utilization or RPS.
      If you want your instances to operate at a max 80% CPU utilization, set your balancing mode to 80% max CPU utilization and your capacity to 100%.
      If you want to cut instance utilization by half, set your balancing mode to 80% max CPU utilization and your capacity to 50%. Input must be a number between 0 and 100.`,
    'gce.serverGroup.loadBalancingPolicy.listeningPort': 'A load balancer sends traffic to an instance group through a named port. Input must be a port number (i.e., between 1 and 65535).',
    'gce.serverGroup.traffic': 'Registers the server group with any associated load balancers. These registrations are used by Spinnaker to determine if the server group is enabled.',
    'titus.serverGroup.traffic': 'Enables the "inService" property, which is used by Spinnaker and ' +
    ' discovery services to determine if the server group is enabled.',
    'pipeline.config.optionalStage': '' +
      '<p>When this option is enabled, stage will only execute when the supplied expression evaluates true.</p>' +
      '<p>The expression <em>does not</em> need to be wrapped in ${ and }.</p>',
    'pipeline.config.checkPreconditions.failPipeline': '' +
      '<p><strong>Checked</strong> - the overall pipeline will fail whenever this precondition is false.</p>' +
      '<p><strong>Unchecked</strong> - the overall pipeline will continue executing but this particular branch will stop.</p>',
    'pipeline.config.checkPreconditions.expectedSize': 'Number of server groups in the selected cluster',
    'pipeline.config.checkPreconditions.expression': '<p>Value must evaluate to "true".</p>' +
      '<p>Use of the <b>Spring Expression Language</b> allows for complex evaluations.</p>',
    'pipeline.config.deploy.template': '<p>Select an existing cluster to use as a template for this deployment, and we\'ll pre-fill ' +
      'the configuration based on the newest server group in the cluster.</p>' +
      '<p>If you want to start from scratch, select "None".</p>' +
      '<p>You can always edit the cluster configuration after you\'ve created it.</p>',

    'loadBalancer.advancedSettings.healthTimeout': '<p>Configures the timeout, in seconds, for reaching the healthCheck target.</p><p> Default: <b>5</b></p>',
    'loadBalancer.advancedSettings.healthInterval': '<p>Configures the interval, in seconds, between ELB health checks.</p><p>Default: <b>10</b></p>',
    'loadBalancer.advancedSettings.healthyThreshold': '<p>Configures the number of healthy observations before reinstituting an instance into the ELB’s traffic rotation.</p><p>Default: <b>10</b></p>',
    'loadBalancer.advancedSettings.unhealthyThreshold': '<p>Configures the number of unhealthy observations before deservicing an instance from the ELB.</p><p>Default: <b>2</b></p>',
    'pipeline.config.resizeAsg.action': '<p>Configures the resize action for the target server group.<ul>' +
      '<li><b>Scale Up</b> increases the size of the target server group by an incremental or percentage amount</li>' +
      '<li><b>Scale Down</b> decreases the size of the target server group by an incremental or percentage amount</li>' +
      '<li><b>Scale to Cluster Size</b> increases the size of the target server group to match the largest server group in the cluster, optionally with an incremental or percentage additional capacity. Additional capacity will not exceed the existing maximum size.</li>' +
      '<li><b>Scale to Exact Size</b> adjusts the size of the target server group to match the provided capacity</li>' +
      '</ul></p>',
    'pipeline.config.resizeAsg.cluster': '<p>Configures the cluster upon which this resize operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.modifyScalingProcess.cluster': '<p>Configures the cluster upon which this modify scaling process operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.enableAsg.cluster': '<p>Configures the cluster upon which this enable operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.disableAsg.cluster': '<p>Configures the cluster upon which this disable operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.destroyAsg.cluster': '<p>Configures the cluster upon which this destroy operation will act. The <em>target</em> specifies what server group to resolve for the operation.</p>',
    'pipeline.config.quickPatchAsg.cluster': '<p>Configures the cluster upon which this quick patch operation will act.</p>',
    'pipeline.config.quickPatchAsg.package': '<p>The name of the package you want installed (without any version identifiers).</p>',
    'pipeline.config.quickPatchAsg.baseOs': '<p>The operating system running on the target instances.</p>',
    'pipeline.config.quickPatchAsg.rollingPatch': '<p>Patch one instance at a time vs. all at once.</p>',
    'pipeline.config.quickPatchAsg.skipUpToDate': '<p>Skip instances which already have the requested version.</p>',
    'pipeline.config.jenkins.propertyFile': '<p>(Optional) Configures the name to the Jenkins artifact file used to pass in properties to later stages in the Spinnaker pipeline.</p>',
    'pipeline.config.bake.package': '<p>The name of the package you want installed (without any version identifiers).</p>' +
      '<p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>' +
      '<p>If there are multiple packages (space separated), then they will be installed in the order they are entered.</p>',
    'pipeline.config.bake.baseAmi': '<p>(Optional) ami-????????</p>',
    'pipeline.config.bake.amiSuffix': '<p>(Optional) String of date in format YYYYMMDDHHmm, default is calculated from timestamp,</p>',
    'pipeline.config.bake.enhancedNetworking': '<p>(Optional) Enable enhanced networking (sr-iov) support for image (requires hvm and trusty base_os).</p>',
    'pipeline.config.bake.amiName': '<p>(Optional) Default = $package-$arch-$ami_suffix-$store_type</p>',
    'pipeline.config.bake.templateFileName': '<p>(Optional) The explicit packer template to use, instead of resolving one from rosco\'s configuration.</p>',
    'pipeline.config.bake.extendedAttributes': '<p>(Optional) Any additional attributes that you want to pass onto rosco, which will be injected into your packer runtime variables.</p>',
    'pipeline.config.gce.bake.baseImage': '<p>(Optional) A GCE image name. For example: ubuntu-1204-precise-v20150910.</p>',
    'pipeline.config.manualJudgment.instructions': '<p>(Optional) Instructions are shown to the user when making a manual judgment.</p><p>May contain HTML.</p>',
    'pipeline.config.manualJudgment.failPipeline': '' +
      '<p><strong>Checked</strong> - the overall pipeline will fail whenever the manual judgment is negative.</p>' +
      '<p><strong>Unchecked</strong> - the overall pipeline will continue executing but this particular branch will stop.</p>',
    'pipeline.config.manualJudgment.propagateAuthentication': '' +
    '<p><strong>Checked</strong> - the pipeline will continue with the permissions of the approver.</p>' +
    '<p><strong>Unchecked</strong> - the pipeline will continue with it\'s current permissions.</p>',
    'pipeline.config.manualJudgment.judgmentInputs': '<p>(Optional) Entries populate a dropdown displayed when ' +
      'performing a manual judgment.</p>' +
      '<p>The selected value can be used in a subsequent <strong>Check Preconditions</strong> stage to determine branching.</p>' +
      '<p>For example, if the user selects "rollback" from this list of options, that branch can be activated by using the ' +
      'expression: ' +
      '<samp class="small">execution.stages[n].context.judgmentInput=="rollback"</samp></p>',
    'pipeline.config.jenkins.haltPipelineOnFailure': '' +
    'Immediately halts execution of all running stages and fails the entire execution.',
    'pipeline.config.jenkins.haltBranchOnFailure': '' +
    'Prevents any stages that depend on this stage from running, but allows other branches of the pipeline to run.',
    'pipeline.config.jenkins.ignoreFailure': '' +
    'Continues execution of dowstream stages, marking this stages as failed/continuing.',
    'pipeline.config.jenkins.markUnstableAsSuccessful.true': 'If Jenkins reports the build status as UNSTABLE, ' +
      'Spinnaker will mark the stage as SUCCEEDED and continue execution of the pipeline.',
    'pipeline.config.jenkins.markUnstableAsSuccessful.false': 'If Jenkins reports the build status as UNSTABLE, ' +
      'Spinnaker will mark the stage as FAILED; subsequent execution will be determined based on the configuration of the ' +
      '<b>If build fails</b> option for this stage.',
    'pipeline.config.failPipeline': '' +
    '<p><strong>Checked</strong> - the overall pipeline will fail whenever the stage fails.</p>' +
    '<p><strong>Unchecked</strong> - the overall pipeline will continue executing but this particular branch will stop.</p>',
    'pipeline.config.canary.clusterPairs': '' +
      '<p>A <em>cluster pair</em> is used to create a baseline and canary cluster.</p>' +
      '<p>The version currently deployed in the baseline cluster will be used to create a new baseline server group, while the version created in the previous bake or Find AMI stage will be deployed into the canary.</p>',

    'pipeline.config.canary.resultStrategy': '' +
      '<p>The result stategy is used to determine how to roll up a score if multiple clusters are participating in the canary.</p>' +
      '<p>The <em>lowest</em> strategy means that the cluster with the lowest score is used as the rolled up score</p>' +
      '<p>The <em>average</em> strategy takes the average of all the canary scores</p>',

    'pipeline.config.canary.delayBeforeAnalysis': '<p>The number of minutes to wait before generating an initial canary score.</p>',

    'pipeline.config.canary.notificationHours': '<p>Hours at which to send a notification (comma separated)</p>',

    'pipeline.config.canary.canaryInterval': '<p>The frequency in minutes at which a canary score is generated.</p>',

    'pipeline.config.canary.successfulScore': '<p>Minimum score the canary must achieve to be considered successful.</p>',
    'pipeline.config.canary.unhealthyScore': '<p>Lowest score the canary can attain before it is aborted and disabled as a failure.</p>',
    'pipeline.config.canary.scaleUpCapacity': '<p>Desired capacity after canary and control clusters are scaled up</p>',
    'pipeline.config.canary.scaleUpDelay': '<p>Minutes to delay before initiating canary scale up</p>',
    'pipeline.config.canary.baselineVersion': '<p>The Canary stage will inspect the specified cluster to determine which version to deploy as the baseline in each cluster pair.</p>',
    'pipeline.config.canary.lookback':'<p>By default ACA will look at the entire duration of the canary for its analysis. Setting a look-back duration limits the number of minutes that the canary will use for it\'s analysis report.<br> <b>Useful for long running canaries that span multiple days.</b></p>',
    'pipeline.config.canary.continueOnUnhealthy':'<p>Continue the pipeline if the ACA comes back as <b>UNHEALTHY</b></p>',
    'pipeline.config.canary.watchers': '<p>Comma separated list of emails to receive notifications of canary events.</p>',
    'pipeline.config.canary.useGlobalDataset': '<p>Uses the global atlas dataset instead of the region specific dataset for ACA</p>',

    'pipeline.config.cron.expression': '<strong>Format (Year is optional)</strong><p><samp>Seconds  Minutes  Hour  DayOfMonth  Month  DayOfWeek  (Year)</samp></p>' +
    '<p><strong>Example: every 30 minutes</strong></p><samp>0 0/30 * * * ?</samp>' +
    '<p><strong>Example: every Monday at 10 am</strong></p><samp>0 0 10 ? * 2</samp>' +
    '<p><strong>Note:</strong> values for "DayOfWeek" are 1-7, where Sunday is 1, Monday is 2, etc. You can also use MON,TUE,WED, etc.',

    'cluster.description': '<p>A cluster is a collection of server groups with the same name (stack + detail) in the same account.</p>',
    'pipeline.config.findAmi.cluster': 'The cluster to look at when selecting the image to use in this pipeline.',
    'pipeline.config.findAmi.imageNamePattern': 'A regex used to match the name of the image. Must result in exactly one match to succeed. Empty is treated as match any.',
    'pipeline.config.dependsOn': 'Declares which stages must be run <em>before</em> this stage begins.',
    'pipeline.config.fastProperty.rollback': 'Enables the Fast Property to be rolled back to it previous state when the pipeline completes.',
    'pipeline.config.parallel.cancel.queue': '<p>If concurrent pipeline execution is disabled, then the pipelines that are in the waiting queue will get canceled by default. <br><br>Check this box if you want to keep them in the queue.</p>',
    'pipeline.config.timeout': '<p>Allows you to override the amount of time the stage can run before failing.</p> ' +
    '<p><b>Note:</b> this is not the overall time the stage has, but rather the time for specific tasks.</p>',
    'pipeline.config.timeout.bake': '<p>For the Bake stage, the timeout will apply to both the "Create Bake" and "Monitor Bake" tasks.</p>',
    'pipeline.config.timeout.deploy': '<p>For the Deploy stage, the timeout will apply to both the "Monitor Deploy" and "Wait For Up Instances" tasks.</p>',
    'pipeline.config.timeout.jenkins': '<p>For the Jenkins stage, the timeout will apply to both the "Wait For Jenkins Job Start" and "Monitor Jenkins Job" tasks.</p>',
    'pipeline.config.script.repoUrl': '<p>Path to the repo hosting the scripts in Stash. (e.g. <samp>CDL/mimir-scripts</samp>). Leave empty to use the default.</p>',
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
    'serverGroupCapacity.useSourceCapacityTrue':  '<p>Spinnaker will use the current capacity of the existing server group when deploying a new server group.</p>' +
      '<p>This setting is intended to support a server group with auto-scaling enabled, where the bounds and desired capacity are controlled by an external process.</p>' +
      '<p>In the event that there is no existing server group, the deploy will fail.</p>',
    'serverGroupCapacity.useSourceCapacityFalse': '<p>The specified capacity is used regardless of the presence or size of an existing server group.</p>',
    'strategy.redblack.scaleDown': '<p>Resizes the target server group to zero instances before disabling it.</p>' +
      '<p>Select this if you wish to retain the launch configuration for the old server group without running any instances.</p>',
    'strategy.redblack.maxRemainingAsgs': '<p><b>Optional</b>: indicates the maximum number of server groups that will remain in this cluster - including the newly created one.</p>' +
      '<p>If you wish to destroy all server groups except the newly created one, select "Highlander" as the strategy.</p>' +
      '<p><strong>Minimum value:</strong> 2</p>',
    'strategy.rollingPush.relaunchAll': '<p>Incrementally terminates each instance in the server group, waiting for a new one to come up before terminating the next one.</p>',
    'strategy.rollingPush.totalRelaunches': '<p>Total number of instances to terminate and relaunch.</p>',
    'strategy.rollingPush.concurrentRelaunches': '<p>Number of instances to terminate and relaunch at a time.</p>',
    'strategy.rollingPush.order': '<p>Determines the order in which instances will be terminated. ' +
      '<ul><li><b>Oldest</b> will terminate the oldest instances first</li>' +
      '<li><b>Newest</b> will terminate those most recently launched.</li></ul></p>',

    'loadBalancers.filter.serverGroups': '<p>Displays all server groups configured to use the load balancer.</p>' +
      '<p>If the server group is configured to <em>not</em> add new instances to the load balancer, it will be grayed out.</p>',
    'loadBalancers.filter.instances': '<p>Displays all instances in the context of their parent server group. The color of the instance icon ' +
      'indicates <em>only its health in relation to the load balancer</em>. That is, if the load balancer health check reports the instance ' +
      'as healthy, the instance will appear green - even if other health indicators (Discovery, other load balancers, etc.) report the instance ' +
      'as unhealthy.</p>' +
      '<p>A red icon indicates the instance is failing the health check for the load balancer.</p>' +
      '<p>A gray icon indicates the instance is currently detached from the load balancer.</p>',
    'loadBalancers.filter.onlyUnhealthy': '<p>Filters the list of load balancers and server groups (if enabled) ' +
      'to only show load balancers with instances failing the health check for the load balancer.</p>',
    'project.cluster.stack': '<p>(Optional field)</p><p>Filters displayed clusters by stack.</p><p>Enter <samp>*</samp> to include all stacks; leave blank to omit any clusters with a stack.</p>',
    'project.cluster.detail': '<p>(Optional field)</p><p>Filters displayed clusters by detail.</p><p>Enter <samp>*</samp> to include all details; leave blank to omit any clusters with a detail.</p>',
    'instanceType.storageOverridden': '<p>These storage settings have been cloned from the base server group and differ from the default settings for this instance type.</p>',
    'instanceType.unavailable': '<p>This instance type is not available for the selected configuration.</p>',
    'fastProperty.canary.strategy.rolloutList': '<p>A comma separated list of numbers or percentages of instance canary against.</p>',
    'execution.forceRebake': '<p>By default, the bakery will <b>not</b> create a new image if the contents of the package have not changed; ' +
      'instead, it will return the previously baked image.</p>' +
      '<p>Select this option to force the bakery to create a new image, regardless of whether or not the selected package exists.</p>',
    'kubernetes.serverGroup.stack': '(Optional) One of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
    'kubernetes.serverGroup.detail': '(Optional) A string of free-form alphanumeric characters and hyphens to describe any other variables.',
    'kubernetes.serverGroup.containers': '(Required) Select at least one image to run in this server group (pod). ' +
      'If multiple images are selected, they will be colocated and replicated equally.',
    'kubernetes.job.parallelism': '(Required) The number of concurrent pods to run.',
    'kubernetes.job.completions': '(Required) The number of sucessful completions required for the job to be considered a success.',
    'kubernetes.job.deadlineSeconds': '(Optional) The number of seconds until the job is considered a failure.',
    'kubernetes.containers.image': 'The image selected under Basic Settings whose container is to be configured.',
    'kubernetes.containers.registry': 'The registry the selected image will be pulled from.',
    'kubernetes.containers.command': 'The list of commands which to overwrite the docker ENTRYPOINT array.',
    'kubernetes.containers.name': '(Required) The name of the container associated with the above image. Used for resource identification',
    'kubernetes.containers.cpu': '(Optional) The relative CPU shares to allocate this container. If set, it is multiplied by 1024, then ' +
      'passed to Docker as the --cpu-shares flag. Otherwise the default of 1 (1024) is used',
    'kubernetes.containers.memory': '(Optional) The relative memory in megabytes to allocate this container. If set, it is converted to an integer ' +
      'and passed to Docker as the --memory flag',
    'kubernetes.containers.requests': '(Optional) This is used for scheduling. It assures that this container will always be scheduled on a machine ' +
      'with at least this much of the resource available.',
    'kubernetes.containers.ports.name': '(Optional) A name for this port. Can be found using DNS lookup if specified.',
    'kubernetes.containers.ports.containerPort': '(Required) The port to expose on this container.',
    'kubernetes.containers.ports.hostPort': '(Optional) The port to expose on <b>Host IP</b>. Most containers do not need this',
    'kubernetes.containers.ports.hostIp': '(Optional) The IP to bind the external port to. Most containers do not need this.',
    'kubernetes.containers.ports.protocol': '(Required) The protocol for this port.',
    'kubernetes.containers.limits': '(Optional) This provides a hard limit on this resource for the given container.',
    'kubernetes.containers.probes.type': '<p><b>HTTP</b> Hit the probe at the specified port and path.</p>' +
      '<p><b>EXEC</b> Execute the specified commands on the container.</p>' +
      '<p><b>TCP</b> Connect to the container at the specified port.</p>',
    'kubernetes.containers.probes.initialDelay': 'How long to wait after startup before running this probe.',
    'kubernetes.containers.probes.timeout': 'How long to wait on the result of this probe.',
    'kubernetes.containers.probes.period': 'How long between probe executions.',
    'kubernetes.containers.probes.successThreshold': 'How many executions need to succeed before the probe is declared healthy.',
    'kubernetes.containers.probes.failureThreshold': 'How many executions need to fail before the probe is declared unhealthy.',
    'kubernetes.containers.volumemounts.name': 'The <b>Volume Source</b> configured above to claim.',
    'kubernetes.containers.volumemounts.mountPath': 'The directory to mount the specified <b>Volume Source</b> to.',
    'kubernetes.namespace': 'The namespace you have configured with the above selected account. This will often be referred to as <b>Region</b> in Spinnaker.',
    'kubernetes.loadBalancer.detail': '(Optional) A string of free-form alphanumeric characters; by convention, we recommend using "frontend".',
    'kubernetes.loadBalancer.stack': '(Optional) One of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.' ,
    'kubernetes.service.ports.name': '(Optional) A name for this port. Can be found using DNS lookup if specified.',
    'kubernetes.service.ports.port': 'The port this service will expose to resources internal to the cluster.',
    'kubernetes.service.ports.nodePort': '(Optional) A port to open on every node in the cluster. This allows you to receive external traffic without ' +
      'having to provision a cloud load balancer. <b>Type</b> in <b>Advanced Settings</b> cannot be set to <b>ClusterIP</b> for this to work.',
    'kubernetes.service.ports.targetPort': '(Optional) The port to forward incoming traffic to for pods associated with this load balancer.',
    'kubernetes.service.ports.protocol': 'The protocol this port listens to.',
    'kubernetes.service.type': '<b>ClusterIP</b> means this is an internal load balancer only. <b>LoadBalancer</b> provisions a cloud load balancer if possible ' +
      'at address <b>Load Balancer IP</b>. <b>NodePort</b> means this load balancer forwards traffic from ports with <b>Node Port</b> specified.',
    'kubernetes.service.sessionAffinity': '<b>None</b> means incoming connections are not associated with the pods they are routed to. <b>ClientIP</b> ' +
      'associates connections with pods by incoming IP address.',
    'kubernetes.service.clusterIp': '(Optional) If specified, and available, this internal IP address will be the internal endpoint for this load balancer.' +
      'If not specified, one will be assigned.',
    'kubernetes.service.loadBalancerIp': 'If specified, and available, this external IP address will be the external endpoint for this load balancer ' +
      'when <b>Type</b> is set to <b>LoadBalancer</b>.',
    'kubernetes.service.externalIps': 'IP addresses for which nodes in the cluster also accept traffic. This is not managed by Kubernetes and the ' +
      'responsibility of the user to configure.',
    'kubernetes.pod.volume': '<p>A storage volume to be mounted and shared by containers in this pod. The lifecycle depends on the volume type selected.</p>' +
      '<p><b>EMPTYDIR</b>: A transient volume tied to the lifecycle of this pod.</p>' +
      '<p><b>HOSTPATH</b>: A directory on the host node. Most pods do not need this.</p>' +
      '<p><b>PERSISTENTVOLUMECLAIM</b>: An already created persistent volume claim to be bound by this pod.</p>' +
      '<p><b>SECRET</b>: An already created kubernetes secret to be mounted in this pod.</p>',
    'kubernetes.pod.emptydir.medium': 'The type of storage medium used by this volume type.' +
      '<p><b>DEFAULT</b>: Depends on the storage mechanism backing this pod\'s Kubernetes installation.</p>' +
      '<p><b>MEMORY</b>: A tmpfs (RAM-backed filesystem). Very fast, but usage counts against the memory resource limit, and contents are lost on reboot.</p>',
    'kubernetes.pod.volume.persistentvolumeclaim.claim': 'The name of the underlying persistent volume claim to request.',
    'kubernetes.pod.volume.hostpath.path': 'The path on the host node\'s filesystem to mount.',
    'kubernetes.pod.volume.secret.secretName': 'The name of the secret to mount.',
    'kubernetes.ingress.backend.port': 'The port for the specified load balancer.',
    'kubernetes.ingress.backend.service': 'The load balancer (service) traffic not matching the below rules will be routed to.',
    'kubernetes.ingress.rules.service': 'The load balancer (service) traffic matching this rule will be routed to.',
    'kubernetes.ingress.rules.host': 'The fully qualified domain name of a network host. Any traffic routed to this host matches this rule. May not be an IP address, or contain port information.',
    'kubernetes.ingress.rules.path': 'POSIX regex (IEE Std 1003.1) matched against the path of an incoming request.',
    'kubernetes.ingress.rules.port': 'The port on the specifed load balancer to route traffic to.',
    'user.verification': 'Typing into this verification field is annoying! But it serves as a reminder that you are ' +
    'changing something in an account deemed important, and prevents you from accidentally changing something ' +
    'when you meant to click on the "Cancel" button.',
    'azure.securityGroup.ingress.description': 'Friendly description of the rule you want to enable (limit 80 chars.)',
    'azure.securityGroup.ingress.priority': 'Rules are processed in priority order; the lower the number, the higher the priority.  We recommend leaving gaps between rules - 100, 200, 300, etc. - so that it\'s easier to add new rules without having to edit existing rules.  There are several default rules that can be overridden with priority (65000, 65001 and 65500).  For more information visit http://portal.azure.com.' ,
    'azure.securityGroup.ingress.source': 'The source filter can be Any, an IP address range or a default tag(\'Internet\', \'VirtualNetwork\', \AzureLoadBalancer\').  It specifies the incoming traffic from a specific source IP address range (CIDR format) that will be allowed or denied by this rule.',
    'azure.securityGroup.ingress.sourcePortRange': 'The source port range can be a single port, such as 80, or a port range, such as 1024-65535.  This specifies from which ports incoming traffic will be allowed or denied by this rule.  Provide an asterisk (*) to allow traffic from clients connecting from any port.',
    'azure.securityGroup.ingress.destination': 'The destination filter can be Any, an IP address range or a default tag(\'Internet\', \'VirtualNetwork\', \AzureLoadBalancer\').  It specifies the outgoing traffic from a specific destination IP address range (CIDR format) that will be allowed or denied by this rule.',
    'azure.securityGroup.ingress.destinationPortRange': 'The destination port range can be a single port, such as 80, or a port range, such as 1024-65535.  This specifies from which destination ports traffic will be allowed or denied by this rule.  Provide an asterisk (*) to allow traffic from clients connecting from any port.',
    'azure.securityGroup.ingress.direction': 'Specifies whether the rule is for inbound or outbound traffic.',
    'azure.securityGroup.ingress.actions': 'To adjust the priority of a rule, move it up or down in the list of rules.  Rules at the top of the list have the highest priority.',
    'azure.serverGroup.imageName': '(Required) <b>Image</b> is the deployable Azure Machine Image.',
    'azure.serverGroup.stack': '(Required) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
    'azure.serverGroup.detail': '(Required) <b>Detail</b> is a naming component to help distinguish specifics of the server group.',
    'openstack.loadBalancer.detail': '(Optional) A string of free-form alphanumeric characters; by convention, we recommend using "frontend".',
    'openstack.loadBalancer.stack': '(Optional) One of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.' ,
    'openstack.loadBalancer.subnet': 'The subnet where the instances for this load balancer reside.',
    'openstack.loadBalancer.protocol': 'The protocol for the traffic to be load balanced. Currently, only HTTP and HTTPS are supported.',
    'openstack.loadBalancer.network': 'The network containing the floating IP pool from which this load balancer will obtain and bind to a floating IP.',
    'openstack.loadBalancer.port': 'The TCP port on which this load balancer will listen.',
    'openstack.loadBalancer.targetPort': 'The TCP port on instances associated with this load balancer to which traffic is sent.',
    'openstack.loadBalancer.distribution': 'The method by which traffic is distributed to the instances.<dl><dt>Least Connections</dt><dd>Sends the request to the instance with the fewest active connections.</dd><dt>Round Robin</dt><dd>Evenly spreads requests across instances.</dd><dt>Source IP</dt><dd>Attempts to deliver requests from the same IP to the same instance.</dd></dl>',
    'openstack.loadBalancer.healthCheck.timeout': '<p>Configures the timeout, in seconds, for obtaining the healthCheck status. This value must be less than the interval.</p><p> Default: <b>1</b></p>',
    'openstack.loadBalancer.healthCheck.delay': '<p>The interval, in seconds, between health checks.</p><p>Default: <b>10</b></p>',
    'openstack.loadBalancer.healthCheck.maxRetries': '<p>The number of retries before declaring an instance as failed and removing it from the pool.</p><p>Default: <b>2</b></p>',
    'openstack.loadBalancer.healthCheck.statusCodes': 'A list of HTTP status codes that will be considered a successful response.',
  });
