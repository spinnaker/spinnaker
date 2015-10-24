'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.help.contents', [])
  .constant('helpContents', {
    'application.platformHealthOnly': 'When this option is enabled, instance status as reported by the cloud provider will be considered sufficient to ' +
      'determine task completion. When this option is disabled, tasks will normally need health status reported by some other health provider (e.g. a ' +
      'load balancer or discovery service) to determine task completion.',
    'application.showPlatformHealthOverride': 'When this option is enabled, users will be able to toggle the option above on a task-by-task basis.',
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
    'aws.serverGroup.detail': '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables',
    'aws.serverGroup.imageName': '(Required) <b>Image</b> is the deployable Amazon Machine Image. Images are restricted to the account and region selected.',
    'aws.serverGroup.allImages': 'Search for an image that does not match the name of your application.',
    'aws.serverGroup.filterImages': 'Select from a pre-filtered list of images matching the name of your application.',
    'aws.serverGroup.strategy': 'The deployment strategy tells Spinnaker what to do with the previous version of the server group.',
    'aws.securityGroup.vpc': '<p>The VPC to which this security group will apply.</p>' +
      '<p>If you wish to use VPC but are unsure which VPC to use, the most common one is "Main".</p>' +
      '<p>If you do not wish to use VPC, select "None".</p>',
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
    'gce.instance.customMetadata.instance-template': 'The instance template used to configure this instance.',
    'gce.instance.customMetadata.load-balancer-names': 'This field is used to "remember" what load balancers this instance is associated with, even if it is deregistered.',
    'gce.instance.customMetadata.startup-script': 'This script will run automatically on every boot.',
    'gce.instanceType.32core': '<p>32-core machine types are in Beta and are available only in Ivy Bridge and Haswell zones. Attempting to provision a 32-core machine in an unsupported zone will result in a <b>machine type not found</b> error message.</p>',
    'gce.loadBalancer.advancedSettings.healthInterval': '<p>Configures the interval, in seconds, between load balancer health checks.</p><p>Default: <b>10</b></p>',
    'gce.loadBalancer.advancedSettings.healthyThreshold': '<p>Configures the number of healthy observations before reinstituting an instance into the load balancer’s traffic rotation.</p><p>Default: <b>10</b></p>',
    'gce.loadBalancer.advancedSettings.unhealthyThreshold': '<p>Configures the number of unhealthy observations before deservicing an instance from the load balancer.</p><p>Default: <b>2</b></p>',
    'gce.loadBalancer.healthCheck': '(Optional) <b>Health Checks</b> use HTTP requests to determine if a VM instance is healthy.',
    'gce.loadBalancer.portRange': '(Optional) Only packets addressed to ports in the specified <b>Port Range</b> will be forwarded. If left empty, all ports are forwarded. Must be a single port number or two port numbers separated by a dash. Each port number must be between 1 and 65535, inclusive. For example: 5000-5999.',
    'gce.serverGroup.capacity': 'The number of instances that the instance group manager will attempt to maintain. Deleting or abandoning instances will affect this number, as will resizing the group.',
    'gce.serverGroup.customMetadata.load-balancer-names': 'This field is used to "remember" what load balancers this server group is associated with, even if the instances are deregistered.',
    'gce.serverGroup.customMetadata.startup-script': 'This script will run automatically on every boot.',
    'gce.serverGroup.securityGroups.implicit': 'Firewall rules with no target tags defined will permit incoming connections that match the ingress rules to all instances in the network.',
    'pipeline.config.checkPreconditions.failPipeline': '' +
      '<p><strong>Checked</strong> - the overall pipeline will fail whenever this precondition is false.</p>' +
      '<p><strong>Unchecked</strong> - the overall pipeline will continue executing but this particular branch will stop.</p>',
    'pipeline.config.deploy.template': '<p>Select an existing cluster to use as a template for this deployment, and we\'ll pre-fill ' +
      'the configuration based on the newest server group in the cluster.</p>' +
      '<p>If you want to start from scratch, select "None".</p>' +
      '<p>You can always edit the cluster configuration after you\'ve created it.</p>',

    'loadBalancer.advancedSettings.healthTimeout': '<p>Configures the timeout, in seconds, for reaching the healthCheck target.</p><p> Default: <b>5</b></p>',
    'loadBalancer.advancedSettings.healthInterval': '<p>Configures the interval, in seconds, between ELB health checks.</p><p>Default: <b>10</b></p>',
    'loadBalancer.advancedSettings.healthyThreshold': '<p>Configures the number of healthy observations before reinstituting an instance into the ELB’s traffic rotation.</p><p>Default: <b>10</b></p>',
    'loadBalancer.advancedSettings.unhealthyThreshold': '<p>Configures the number of unhealthy observations before deservicing an instance from the ELB.</p><p>Default: <b>2</b></p>',
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
      '<p>If your build produces a deb file named "myapp_1.27-h343", you would want to enter "myapp" here.</p>',
    'pipeline.config.bake.baseAmi': '<p>(Optional) ami-????????</p>',
    'pipeline.config.bake.amiSuffix': '<p>(Optional) String of date in format YYYYMMDDHHmm, default is calculated from timestamp,</p>',
    'pipeline.config.bake.enhancedNetworking': '<p>(Optional) Enable enhanced networking (sr-iov) support for image (requires hvm and trusty base_os).</p>',
    'pipeline.config.bake.amiName': '<p>(Optional) Default = $package-$arch-$ami_suffix-$store_type</p>',
    'pipeline.config.manualJudgment.instructions': '<p>(Optional) Instructions are shown to the user when making a manual judgment.</p><p>May contain HTML.</p>',
    'pipeline.config.manualJudgment.failPipeline': '' +
      '<p><strong>Checked</strong> - the overall pipeline will fail whenever the manual judgment is negative.</p>' +
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

    'pipeline.config.cron.expression': '<strong>Format (Year is optional)</strong><p><samp>Seconds  Minutes  Hour  DayOfMonth  Month  DayOfWeek  (Year)</samp></p>' +
    '<p><strong>Example: every 30 minutes</strong></p><samp>0 0/30 * * * ?</samp>',

    'cluster.description': '<p>A cluster is a collection of server groups with the same name (stack + detail) in the same account.</p>',

    'pipeline.config.findAmi.cluster': 'The cluster to look at when selecting the AMI to use in this pipeline.',
    'pipeline.config.dependsOn': 'Declares which stages must be run <em>before</em> this stage begins.',
    'pipeline.config.parallel.execution': '<p>Enabling parallel stage execution allows you to run stages only after dependent ' +
      'stages have completed.</p><p>By configuring a pipeline this way, you can reduce the time it takes to run.</p>',
    'pipeline.config.timeout': '<p>Allows you to override the amount of time the stage can run before failing.</p> ' +
    '<p><b>Note:</b> this is not the overall time the stage has, but rather the time for specific tasks.</p>',
    'pipeline.config.timeout.bake': '<p>For the Bake stage, the timeout will apply to both the "Create Bake" and "Monitor Bake" tasks.</p>',
    'pipeline.config.timeout.deploy': '<p>For the Deploy stage, the timeout will apply to both the "Monitor Deploy" and "Wait For Up Instances" tasks.</p>',
    'pipeline.config.timeout.jenkins': '<p>For the Jenkins stage, the timeout will apply to both the "Wait For Jenkins Job Start" and "Monitor Jenkins Job" tasks.</p>',
    'pipeline.config.script.path': '<p>Path to the folder hosting the scripts in Stash. (e.g. <samp>groovy</samp>, <samp>python</samp> or <samp>shell</samp>)</p>',
    'pipeline.config.script.command': '<p>Executable script and parameters. (e.g. <samp>script.py --ami-id ${deploymentDetails[0].ami}</samp> ) </p>',
    'pipeline.config.script.image': '<p>(Optional) image passed down to script execution as IMAGE_ID</p>',
    'pipeline.config.script.account': '<p>(Optional) account passed down to script execution as ENV_PARAM</p>',
    'pipeline.config.script.region': '<p>(Optional) region passed down to script execution as REGION_PARAM</p>',
    'pipeline.config.script.cluster': '<p>(Optional) cluster passed down to script execution as CLUSTER_PARAM</p>',
    'pipeline.config.script.cmc': '<p>(Optional) cmc passed down to script execution as CMC</p>',
    'pipeline.config.script.propertyFile': '<p>(Optional) The name to the properties file produced by the script execution to be used by later stages of the Spinnaker pipeline. </p>',
    'serverGroupCapacity.useSourceCapacityTrue':  '<p>Spinnaker will use the current capacity of the existing server group when deploying a new server group.</p>' +
      '<p>This setting is intended to support a server group with auto-scaling enabled, where the bounds and desired capacity are controlled by an external process.</p>' +
      '<p>In the event that there is no existing server group, the deploy will fail.</p>',
    'serverGroupCapacity.useSourceCapacityFalse': '<p>The specified capacity is used regardless of the presence or size of an existing server group.</p>',
    'strategy.redblack.scaleDown': '<p>Resizes the target server group to zero instances before disabling it.</p>' +
      '<p>Select this if you wish to retain the launch configuration for the old server group without running any instances.</p>',
    'strategy.redblack.maxRemainingAsgs': '<p>Indicates the maximum number of server groups that will remain in this cluster - including the newly created one.</p>' +
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
  }).name;
