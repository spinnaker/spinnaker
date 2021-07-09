import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'aws.associateElasticIp.elasticIp': `<p>(Optional) <b>Elastic IP</b> is an IP address that Spinnaker will associate with this cluster.</p>
     <p>If specified, this elastic IP must exist and not already be attached to an instance or cluster.</p>
     <p>If left blank, Spinnaker will make a selection from the list of available elastic IPs in the provided account and region.</p>`,
  'aws.associateElasticIp.type': `<p><b>Type</b> of elastic IP to associate:'
      <ul>
        <li><b>standard</b> is usable in EC2 Classic</li>
        <li><b>vpc</b> is usable in VPC</li>
      </ul>`,
  'aws.serverGroup.subnet': `The subnet selection determines the VPC in which your server group will run. Options vary by account and region; the most common ones are:
      <ul>
        <li><b>None (EC2 Classic)</b>: instances will not run in a VPC</li>
        <li><b>internal</b> instances will be restricted to internal clients (i.e. require VPN access)</li>
        <li><b>external</b> instances will be publicly accessible and running in VPC</li>
      </ul>`,
  'aws.loadBalancer.subnet': `The subnet selection determines the VPC in which your load balancer will run.<br/>
     This also restricts the server groups which can be serviced by the load balancer.
     Options vary by account and region; the most common ones are:
     <ul>
      <li><b>None (EC2 Classic)</b>: the load balancer will not run in a VPC</li>
      <li><b>internal</b> access to the load balancer will be restricted to internal clients (i.e. require VPN access)</li>
      <li><b>external</b> the load balancer will be publicly accessible and running in VPC</li>
    </ul>`,
  'aws.loadBalancer.detail': `<p>(Optional) <b>Detail</b> is a string of free-form alphanumeric characters; by convention, we recommend using "frontend".</p>
     <p>However, if your stack name needs to be longer (load balancer names are limited to 32 characters), consider changing it to "elb", or omit it altogether.</p>`,
  'aws.loadBalancer.internal':
    'Controls the load balancer scheme, <strong>not</strong> the subnet. By default, load balancers are created with a DNS name that resolves to public IP addresses. Specify internal to create a load balancer with a DNS name that resolves to private IP addresses.',
  'aws.loadBalancer.stack':
    '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'aws.loadBalancer.name':
    '<p>The load balancer name is formed by combining the application name, the <b>Stack</b> field, and the <b>Detail</b> field.</p>',
  'aws.loadBalancer.targetGroups':
    'Add a target group if you want to associate this with an Application Load Balancer (ALB) or Network Load Balancer (NLB)',
  'aws.loadBalancer.loadBalancers':
    'And a load balancer directly if you created a Classic Load Balancer (a classic load balancer does not have target groups)',
  'aws.loadBalancer.ruleCondition.host': `<p>You can specify a single host name (for example, <em>my.example.com</em>). A host name is case insensitive, can be up to 128 characters in length, and can contain any of the following characters. Note that you can include up to three wildcard characters.</p>
      <ul>
        <li>A-Z, a-z, 0-9</li>
        <li>- .</li>
        <li>* (matches 0 or more characters)</li>
        <li>? (matches exactly 1 character)</li>
      </ul>
     <p>Note that <strong>*.example.com</strong> will match <strong>test.example.com</strong> but won't match <strong>example.com</strong>.</p>`,
  'aws.loadBalancer.ruleCondition.path': `<p>You can specify a single path pattern (for example, <em>/img/*</em>). A path pattern is case sensitive, can be up to 128 characters in length, and can contain any of the following characters. Note that you can include up to three wildcard characters.</p>
      <ul>
        <li>A-Z, a-z, 0-9</li>
        <li>_ - . $ / ~ " ' @ : +</li>
        <li>& (using &amp;amp;)</li>
        <li>* (matches 0 or more characters)</li>
        <li>? (matches exactly 1 character)</li>
      </ul>
      <p>Note that the path pattern is used to route requests but does not alter them. For example, if a rule has a path pattern of <em>/img/*</em>, the rule would forward a request for <em>/img/picture.jpg</em> to the specified target group as a request for <em>/img/picture.jpg</em>.</p>`,
  'aws.loadBalancer.oidcAuthentication': 'Authentication requires a configured OIDC client.',
  'aws.serverGroup.stack':
    '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'aws.serverGroup.detail':
    '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.',
  'aws.serverGroup.imageName':
    '(Required) <b>Image</b> is the deployable Amazon Machine Image. Images are restricted to the account and region selected.',
  'aws.serverGroup.spotMaxPrice':
    'The maximum price per unit hour to pay for a Spot instance. By default (empty), Amazon EC2 Auto Scaling uses the On-Demand price as the maximum Spot price',
  'aws.serverGroup.spotAllocationStrategy': `<p>Indicates how to allocate instances across Spot Instance pools.</p>
      <ul>
        <li><b>capacity-optimized (recommended)</b>: Instances launched using Spot pools that are optimally chosen based on the available Spot capacity.</li>
        <li><b>capacity-optimized-prioritized</b>: Instances launched using the priority on a best-effort basis to determine which launch template override to use first, but AWS optimizes for capacity first.</li>
        <li><b>lowest-price</b>: Instances launched using Spot pools with the lowest price, and evenly allocated across the number of Spot pools specified</li>
      </ul>`,
  'aws.serverGroup.spotInstancePoolCount': `Number of lowest priced Spot Instance pools to diversify across. Only applicable for strategy 'lowest-price'.`,
  'aws.serverGroup.odAllocationStrategy': `The only strategy / default is 'prioritized'. The order of instance types in the list of launch template overrides is used to determine which instance type to use first when fulfilling On-Demand capacity.`,
  'aws.serverGroup.odBase': `Minimum amount of the Auto Scaling Group's capacity that must be fulfilled by On-Demand Instances. This base portion is provisioned first as the group scales.`,
  'aws.serverGroup.odPercentAboveBase': `Percentages of On-Demand and Spot instances for additional capacity beyond OnDemandBaseCapacity.`,
  'aws.serverGroup.instanceTypeWeight': `The number of capacity units gives the instance type a proportional weight to other instance types. When specified, weights count towards desired capacity.`,
  'aws.serverGroup.instanceTypes': `Specify up to 20 instance types.`,
  'aws.serverGroup.multipleInstanceTypes': `Instance types a server group can launch, first (highest priority) to last (lowest priority).`,
  'aws.serverGroup.unlimitedCpuCredits': `<p>CPU credits can be configured with 2 modes:</p><br/>
      <ul>
        <li><b>Unlimited (i.e. Unlimited On)</b>: Can sustain high CPU utilization for any period of time whenever required.<br/>
            If the average CPU usage over a rolling 24-hour period exceeds the baseline, charges for surplus credits will apply.</li>
        <li><b>Standard (i.e. Unlimited Off)</b>: <b>Default mode in Spinnaker.</b> Suited to workloads with an average CPU utilization that is consistently below the baseline CPU utilization of the instance.<br/>
            To burst above the baseline, the instance spends credits that it has accrued in its CPU credit balance.</li>
      </ul>`,
  'aws.serverGroup.capacityRebalance': `Enabling <b>Capacity Rebalance</b> allows EC2 Auto Scaling to proactively replace Spot Instances that have received a rebalance recommendation, <b>before</b> it is interrupted by AWS EC2.`,
  'aws.serverGroup.legacyUdf': `<p>(Optional) <b>User Data Format</b> allows overriding of the format used when generating user data during deployment. The default format used is configured
      in the application's attributes, editable via the 'Config' tab.</p>
      <p><b>Default</b> will use the value from the application's configuration.</p>
      <p><b>Modern</b> will use the modern template.</p>
      <p><b>Legacy</b> will use the legacy (<b>deprecated</b>) template.</p>
      <p>This option is intended to allow testing migration from legacy to modern before configuring it for the entire application. If unsure, pick <b>Default</b>.</p>`,
  'aws.serverGroup.base64UserData': '(Optional) <b>UserData</b> is a base64 encoded string.',
  'aws.serverGroup.enabledMetrics':
    '(Optional) <b>Enabled Metrics</b> are the Auto Scaling Group metrics to enable on this group. Existing metrics are not modified.',
  'aws.serverGroup.imdsv2':
    "(Recommended) <b>IMDSv2</b> helps mitigate AWS credential theft from the exploitation of SSRF vulnerabilities in web applications. All modern AWS SDKs support IMDSv2 and it should not be disabled unless you're using a legacy SDK.",
  'aws.serverGroup.instanceMonitoring':
    '(Optional) <b>Instance Monitoring</b> whether to enable detailed monitoring of instances. Group metrics must be disabled to update an ASG with Instance Monitoring set to false.',
  'aws.serverGroup.tags': '(Optional) <b>Tags</b> are propagated to the instances in this cluster.',
  'aws.serverGroup.allImages': 'Search for an image that does not match the name of your application.',
  'aws.serverGroup.filterImages': 'Select from a pre-filtered list of images matching the name of your application.',
  'aws.serverGroup.traffic': `<p>Enables the "AddToLoadBalancer" scaling process, which is used by Spinnaker and discovery services to determine if the server group is enabled.</p>
     <p>Will be automatically enabled when any non "custom" deployment strategy is selected.</p>`,
  'aws.securityGroup.vpc': `
    <p>The VPC to which this {{firewall}} will apply.</p>
    <p>If you wish to use VPC but are unsure which VPC to use, the most common one is "Main".</p>
    <p>If you do not wish to use VPC, select "None".</p>`,
  'aws.securityGroup.name':
    '<p>The {{firewall}} name is formed by combining the application name, the <b>Stack</b> field, and the <b>Detail</b> field.</p>',
  'aws.securityGroup.cross.account.ingress.help': '<p>Accounts that are excluded will not show up in this list</p>',
  'aws.scalingPolicy.search.restricted': `<p>Resets dimensions to "AutoScalingGroupName: {name of the ASG}" and provides
        a simpler, combined input for the namespace and metric name fields.</p>`,
  'aws.scalingPolicy.search.all': `
    <p>Allows you to edit the dimensions and namespace to find a specific metric for this alarm.</p>`,
  'aws.blockDeviceMappings.useSource': `
    <p>Spinnaker will use the block device mappings of the existing server group when deploying a new server group.</p>
    <p>In the event that there is no existing server group, the
        <a target="_blank" href="https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/clouddriver/aws/deploy/InstanceTypeUtils.java">defaults</a>
        for the selected instance type will be used.</p>`,
  'aws.blockDeviceMappings.useAMI':
    '<p>Spinnaker will use the block device mappings from the selected AMI when deploying a new server group.</p>',
  'aws.blockDeviceMappings.useDefaults':
    '<p>Spinnaker will use the <a target="_blank" href="https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/clouddriver/aws/deploy/InstanceTypeUtils.java">default block device mappings</a> for the selected instance type when deploying a new server group.</p>',
  'aws.targetGroup.protocol':
    'The protocol to use for routing traffic to the targets. Cannot be edited after being saved; if you want to use a different protocol, create a new target group, save the load balancer, move your targets, and then delete this target group.',
  'aws.targetGroup.targetType':
    'Determines how targets are specified. Only set to ip if you need to attach individual ips. Cannot be edited after being saved; if you want to use a different target type, create a new target group, save the load balancer, move your targets, and then delete this target group.',
  'aws.targetGroup.port':
    'The port on which the targets receive traffic. Cannot be edited after being saved; if you want to use a different port, create a new target group, save the load balancer, move your targets, and then delete this target group.',
  'aws.targetGroup.attributes.deregistrationDelay':
    'The amount of time for the load balancer to wait before changing the state of a deregistering target from draining to unused. The range is 0-3600 seconds. The default value is 300 seconds.',
  'aws.targetGroup.attributes.stickinessEnabled': ' Indicates whether sticky sessions are enabled.',
  'aws.targetGroup.attributes.deregistrationDelayConnectionTermination':
    'If enabled, your Network Load Balancer will terminate active connections when deregistration delay is reached.',
  'aws.targetGroup.attributes.preserveClientIp':
    'If enabled, your Network Load Balancer will preserve client IP addresses to the target.',
  'aws.targetGroup.attributes.stickinessType':
    'The type of sticky sessions. The only current possible value is <code>lb_cookie</code>.',
  'aws.targetGroup.attributes.stickinessDuration':
    'The time period, in seconds, during which requests from a client should be routed to the same target. After this time period expires, the load balancer-generated cookie is considered stale. The range is 1 second to 1 week (604800 seconds). The default value is 1 day (86400 seconds).',
  'aws.targetGroup.attributes.healthCheckPort.trafficPort':
    'The port the load balancer uses when performing health checks on targets. The default is <b>traffic-port</b>, which is the port on which each target receives traffic from the load balancer.',
  'aws.targetGroup.healthCheckProtocol': 'TCP health checks only support 10s and 30s intervals',
  'aws.targetGroup.healthCheckTimeout':
    'Target groups with TCP or TLS protocol must have a 6s timeout for HTTP health checks or a 10s timeout for HTTPS/TLS health checks.',
  'aws.targetGroup.nlbHealthcheckThreshold':
    'The healthy and unhealthy threshold for NLBs must be equal. This represents the number of successful and failed healthchecks required for healthy and unhealthy targets, respectively.',
  'aws.serverGroup.capacityConstraint': `
      <p>Ensures that the capacity of this server group has not changed in the background (i.e. due to autoscaling activity).</p>
      <p>If the capacity has changed, this resize operation will be rejected.</p>`,
  'aws.tagImage.consideredStages': `Limit which previous stages will be considered when locating AMI's to tag. If left unchecked, AMI's generated by any upstream stage will be tagged.`,
  'aws.loadBalancer.redirect.host': `The hostname. This component is not percent-encoded. The hostname can contain <code>#{host}</code>.`,
  'aws.loadBalancer.redirect.path': `The absolute path, starting with the leading "/". This component is not percent-encoded. The path can contain <code>#{host}</code>, <code>#{path}</code>, and <code>#{port}</code>.`,
  'aws.loadBalancer.redirect.port': `The port. You can specify a value from 1 to 65535 or <code>#{port}</code>.`,
  'aws.loadBalancer.redirect.protocol': `The protocol. You can specify HTTP, HTTPS, or <code>#{protocol}</code>. You can redirect HTTP to HTTP, HTTP to HTTPS, and HTTPS to HTTPS. You cannot redirect HTTPS to HTTP.`,
  'aws.loadBalancer.redirect.query': `The query parameters, URL-encoded when necessary, but not percent-encoded. Do not include the leading "?", as it is automatically added. You can specify any of the reserved keywords.`,
  'aws.loadBalancer.redirect.statusCode': `The HTTP redirect code. The redirect is either permanent (HTTP 301) or temporary (HTTP 302).`,
  'aws.loadBalancer.redirect': `<p>A URI consists of the following components: protocol://hostname:port/path?query. You must modify at least one of the following components to avoid a redirect loop: protocol, hostname, port, or path. Any components that you do not modify retain their original values.

        <p>You can reuse URI components using the following reserved keywords:

            <ul><li>#{protocol}
            <li>#{host}
            <li>#{port}
            <li>#{path} (the leading "/" is removed)
            <li>#{query}
        </ul>
        <p>For example, you can change the path to "/new/#{path}", the hostname to "example.#{host}", or the query to "#{query}&value=xyz".`,
  'aws.cloudformation.source': `
      <p>Where the template file content is read from.</p>
      <p>
        <b>text:</b> The template is supplied statically to the pipeline from the below text-box.
      </p>
      <p>
        <b>artifact:</b> The template is read from an artifact supplied/created upstream. The expected artifact must be referenced here, and will be bound at runtime.
      </p>
  `,
  'aws.cloudformation.expectedArtifact': `The artifact that is to be applied to this stage. The artifact should represent a valid cloudformation template.`,
  'aws.function.name': `Enter a name that describes the purpose of your function. Function name will be prefixed with the application name.`,
  'aws.function.runtime': `Choose the language to use to write your function`,
  'aws.function.s3key': `The Amazon S3 key of the deployment package`,
  'aws.function.handler': `The name of the method within your code that Lambda calls to execute your function. The format includes the file name. It can also include namespaces and other qualifiers, depending on the runtime.`,
  'aws.function.s3bucket': `An Amazon S3 bucket in the same AWS Region as your function. The bucket can be in a different AWS account.`,
  'aws.function.execution.role': `Lambda will create an execution role with permission to upload logs to Amazon CloudWatch Logs. You can also choose an existing role that defines the permissions of your function.`,
  'aws.function.env.vars': `You can define environment variables as key-value pairs that are accessible from your function code. These are useful to store configuration settings without the need to change function code`,
  'aws.function.tags': `You can use tags to group and filter your functions. A tag consists of a case-sensitive key-value pair`,
  'aws.functionBasicSettings.memorySize': `Your function is allocated CPU proportional to the memory configured.`,
  'aws.functionBasicSettings.timeout': `The amount of time that Lambda allows a function to run before stopping it. The default is 3 seconds. The maximum allowed value is 900 seconds.`,
  'aws.function.publish': `Set to true to publish the first version of the function during creation.`,
  'aws.function.deadletterqueue': `A dead letter queue configuration that specifies the queue or topic where Lambda sends asynchronous events when they fail processing. (SNS or SQS)`,
  'aws.function.tracingConfig.mode': `The function's AWS X-Ray tracing configuration.`,
  'aws.function.kmsKeyArn': `The ARN of the AWS Key Management Service (AWS KMS) key that's used to encrypt your function's environment variables. If it's not provided, AWS Lambda uses a default service key.`,
  'aws.cloudformation.changeSet.options': `<p>Action to take when the created ChangeSet contains a replacement.</p>
        <p>
          <b>ask:</b> Execution will be put on hold asking for user feedback.
        </p>
        <p>
          <b>skip it:</b> ChangeSet will not be executed and stage will continue.
        </p>
        <p>
          <b>execute it</b> ChangeSet will be executed.
        </p>
        <p>
          <b>fail stage</b> ChangeSet will not be executed and the stage will fail.
        </p>`,
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
