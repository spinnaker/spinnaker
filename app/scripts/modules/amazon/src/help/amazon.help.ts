import {module} from 'angular';
import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from '@spinnaker/core';

const helpContents: {[key: string]: string} = {
  'aws.associateElasticIp.elasticIp':
    `<p>(Optional) <b>Elastic IP</b> is an IP address that Spinnaker will associate with this cluster.</p>
     <p>If specified, this elastic IP must exist and not already be attached to an instance or cluster.</p>
     <p>If left blank, Spinnaker will make a selection from the list of available elastic IPs in the provided account and region.</p>`,
  'aws.associateElasticIp.type':
    `<p><b>Type</b> of elastic IP to associate:'
      <ul>
        <li><b>standard</b> is usable in EC2 Classic</li>
        <li><b>vpc</b> is usable in VPC</li>
      </ul>`,
  'aws.serverGroup.subnet':
    `The subnet selection determines the VPC in which your server group will run. Options vary by account and region; the most common ones are:
      <ul>
        <li><b>None (EC2 Classic)</b>: instances will not run in a VPC</li>
        <li><b>internal</b> instances will be restricted to internal clients (i.e. require VPN access)</li>
        <li><b>external</b> instances will be publicly accessible and running in VPC</li>
      </ul>`,
  'aws.loadBalancer.subnet':
    `The subnet selection determines the VPC in which your load balancer will run.<br/>
     This also restricts the server groups which can be serviced by the load balancer.
     Options vary by account and region; the most common ones are:
     <ul>
      <li><b>None (EC2 Classic)</b>: the load balancer will not run in a VPC</li>
      <li><b>internal</b> access to the load balancer will be restricted to internal clients (i.e. require VPN access)</li>
      <li><b>external</b> the load balancer will be publicly accessible and running in VPC</li>
    </ul>`,
  'aws.loadBalancer.detail':
    `<p>(Optional) <b>Detail</b> is a string of free-form alphanumeric characters; by convention, we recommend using "frontend".</p>
     <p>However, if your stack name needs to be longer (load balancer names are limited to 32 characters), consider changing it to "elb", or omit it altogether.</p>`,
  'aws.loadBalancer.stack': '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'aws.loadBalancer.targetGroups': 'Add a target group if you want to associate this with an Application Load Balancer (ALB)',
  'aws.loadBalancer.loadBalancers': 'And a load balancer directly if you created a Classic Load Balancer (a classic load balancer does not have target groups)',
  'aws.serverGroup.stack': '(Optional) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'aws.serverGroup.detail': '(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.',
  'aws.serverGroup.imageName': '(Required) <b>Image</b> is the deployable Amazon Machine Image. Images are restricted to the account and region selected.',
  'aws.serverGroup.legacyUdf':
    `<p>(Optional) <b>User Data Format</b> allows overriding of the format used when generating user data during deployment. The default format used is configured
      in the application's attributes, editable via the 'Config' tab.</p>
      <p><b>Default</b> will use the value from the application's configuration.</p>
      <p><b>Modern</b> will use the modern template.</p>
      <p><b>Legacy</b> will use the legacy (<b>deprecated</b>) template.</p>
      <p>This option is intended to allow testing migration from legacy to modern before configuring it for the entire application. If unsure, pick <b>Default</b>.</p>`,
  'aws.serverGroup.base64UserData': '(Optional) <b>UserData</b> is a base64 encoded string.',
  'aws.serverGroup.enabledMetrics': '(Optional) <b>Enabled Metrics</b> are the Auto Scaling Group metrics to enable on this group. Existing metrics are not modified.',
  'aws.serverGroup.instanceMonitoring': '(Optional) <b>Instance Monitoring</b> whether to enable detailed monitoring of instances. Group metrics must be disabled to update an ASG with Instance Monitoring set to false.',
  'aws.serverGroup.tags': '(Optional) <b>Tags</b> are propagated to the instances in this cluster.',
  'aws.serverGroup.allImages': 'Search for an image that does not match the name of your application.',
  'aws.serverGroup.filterImages': 'Select from a pre-filtered list of images matching the name of your application.',
  'aws.serverGroup.traffic':
    `<p>Enables the "AddToLoadBalancer" scaling process, which is used by Spinnaker and discovery services to determine if the server group is enabled.</p>
     <p>Will be automatically enabled when any non "custom" deployment strategy is selected.</p>`,
  'aws.securityGroup.vpc': `
    <p>The VPC to which this security group will apply.</p>
    <p>If you wish to use VPC but are unsure which VPC to use, the most common one is "Main".</p>
    <p>If you do not wish to use VPC, select "None".</p>`,
  'aws.scalingPolicy.search.restricted':
    `<p>Resets dimensions to "AutoScalingGroupName: {name of the ASG}" and provides
        a simpler, combined input for the namespace and metric name fields.</p>`,
  'aws.scalingPolicy.search.all': `
    <p>Allows you to edit the dimensions and namespace to find a specific metric for this alarm.</p>`,
  'aws.blockDeviceMappings.useSource': `
    <p>Spinnaker will use the block device mappings of the existing server group when deploying a new server group.</p>
    <p>In the event that there is no existing server group, the
        <a target="_blank" href="https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/clouddriver/aws/deploy/BlockDeviceConfig.groovy">defaults</a>
        for the selected instance type will be used.</p>`,
  'aws.blockDeviceMappings.useAMI': '<p>Spinnaker will use the block device mappings from the selected AMI when deploying a new server group.</p>',
  'aws.blockDeviceMappings.useDefaults': '<p>Spinnaker will use the <a target="_blank" href="https://github.com/spinnaker/clouddriver/blob/master/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/clouddriver/aws/deploy/BlockDeviceConfig.groovy">default block device mappings</a> for the selected instance type when deploying a new server group.</p>',
  'aws.targetGroup.attributes.deregistrationDelay': 'The amount of time for Elastic Load Balancing to wait before changing the state of a deregistering target from draining to unused. The range is 0-3600 seconds. The default value is 300 seconds.',
  'aws.targetGroup.attributes.stickinessEnabled': ' Indicates whether sticky sessions are enabled.',
  'aws.targetGroup.attributes.stickinessType': 'The type of sticky sessions. The only current possible value is <code>lb_cookie</code>.',
  'aws.targetGroup.attributes.stickinessDuration': 'The time period, in seconds, during which requests from a client should be routed to the same target. After this time period expires, the load balancer-generated cookie is considered stale. The range is 1 second to 1 week (604800 seconds). The default value is 1 day (86400 seconds).'
};

export const AMAZON_HELP = 'spinnaker.amazon.help.contents';
module(AMAZON_HELP, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    Object.keys(helpContents).forEach(key => helpContentsRegistry.register(key, helpContents[key]));
  });
