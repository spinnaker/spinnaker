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
    'Add a target group if you want to associate this with an Application Load Balancer (ALB)',
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
  'aws.serverGroup.legacyUdf': `<p>(Optional) <b>User Data Format</b> allows overriding of the format used when generating user data during deployment. The default format used is configured
      in the application's attributes, editable via the 'Config' tab.</p>
      <p><b>Default</b> will use the value from the application's configuration.</p>
      <p><b>Modern</b> will use the modern template.</p>
      <p><b>Legacy</b> will use the legacy (<b>deprecated</b>) template.</p>
      <p>This option is intended to allow testing migration from legacy to modern before configuring it for the entire application. If unsure, pick <b>Default</b>.</p>`,
  'aws.serverGroup.base64UserData': '(Optional) <b>UserData</b> is a base64 encoded string.',
  'aws.serverGroup.enabledMetrics':
    '(Optional) <b>Enabled Metrics</b> are the Auto Scaling Group metrics to enable on this group. Existing metrics are not modified.',
  'aws.serverGroup.instanceMonitoring':
    '(Optional) <b>Instance Monitoring</b> whether to enable detailed monitoring of instances. Group metrics must be disabled to update an ASG with Instance Monitoring set to false.',
  'aws.serverGroup.tags': '(Optional) <b>Tags</b> are propagated to the instances in this cluster.',
  'aws.serverGroup.allImages': 'Search for an image that does not match the name of your application.',
  'aws.serverGroup.filterImages': 'Select from a pre-filtered list of images matching the name of your application.',
  'aws.serverGroup.spotPrice': 'The maximum price to pay per hour per instance',
  'aws.serverGroup.traffic': `<p>Enables the "AddToLoadBalancer" scaling process, which is used by Spinnaker and discovery services to determine if the server group is enabled.</p>
     <p>Will be automatically enabled when any non "custom" deployment strategy is selected.</p>`,
  'aws.securityGroup.vpc': `
    <p>The VPC to which this {{firewall}} will apply.</p>
    <p>If you wish to use VPC but are unsure which VPC to use, the most common one is "Main".</p>
    <p>If you do not wish to use VPC, select "None".</p>`,
  'aws.securityGroup.name':
    '<p>The {{firewall}} name is formed by combining the application name, the <b>Stack</b> field, and the <b>Detail</b> field.</p>',
  'aws.scalingPolicy.search.restricted': `<p>Resets dimensions to "AutoScalingGroupName: {name of the ASG}" and provides
        a simpler, combined input for the namespace and metric name fields.</p>`,
  'aws.scalingPolicy.search.all': `
    <p>Allows you to edit the dimensions and namespace to find a specific metric for this alarm.</p>`,
  'aws.blockDeviceMappings.useSource': `
    <p>Spinnaker will use the block device mappings of the existing server group when deploying a new server group.</p>
    <p>In the event that there is no existing server group, the
        <a target="_blank" href="https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/clouddriver/aws/deploy/BlockDeviceConfig.groovy">defaults</a>
        for the selected instance type will be used.</p>`,
  'aws.blockDeviceMappings.useAMI':
    '<p>Spinnaker will use the block device mappings from the selected AMI when deploying a new server group.</p>',
  'aws.blockDeviceMappings.useDefaults':
    '<p>Spinnaker will use the <a target="_blank" href="https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/clouddriver/aws/deploy/BlockDeviceConfig.groovy">default block device mappings</a> for the selected instance type when deploying a new server group.</p>',
  'aws.targetGroup.protocol':
    'The protocol to use for routing traffic to the targets. Cannot be edited after being saved; if you want to use a different protocol, create a new target group, save the load balancer, move your targets, and then delete this target group.',
  'aws.targetGroup.targetType':
    'Determines how targets are specified. Only set to ip if you need to attach individual ips. Cannot be edited after being saved; if you want to use a different target type, create a new target group, save the load balancer, move your targets, and then delete this target group.',
  'aws.targetGroup.port':
    'The port on which the targets receive traffic. Cannot be edited after being saved; if you want to use a different port, create a new target group, save the load balancer, move your targets, and then delete this target group.',
  'aws.targetGroup.attributes.deregistrationDelay':
    'The amount of time for the load balancer to wait before changing the state of a deregistering target from draining to unused. The range is 0-3600 seconds. The default value is 300 seconds.',
  'aws.targetGroup.attributes.stickinessEnabled': ' Indicates whether sticky sessions are enabled.',
  'aws.targetGroup.attributes.stickinessType':
    'The type of sticky sessions. The only current possible value is <code>lb_cookie</code>.',
  'aws.targetGroup.attributes.stickinessDuration':
    'The time period, in seconds, during which requests from a client should be routed to the same target. After this time period expires, the load balancer-generated cookie is considered stale. The range is 1 second to 1 week (604800 seconds). The default value is 1 day (86400 seconds).',
  'aws.targetGroup.attributes.healthCheckPort.trafficPort':
    'The port the load balancer uses when performing health checks on targets. The default is <b>traffic-port</b>, which is the port on which each target receives traffic from the load balancer.',
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
};

Object.keys(helpContents).forEach(key => HelpContentsRegistry.register(key, helpContents[key]));
