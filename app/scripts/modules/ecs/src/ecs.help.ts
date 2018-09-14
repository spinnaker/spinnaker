import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'ecs.loadBalancer.targetGroup':
    '<p>A <em>target group</em> is attached to an application / network load balancer and is a target for load balancer traffic.</p>  <p> You need to create both the application load balancer and target groups prior to being able to use them in your pipeline.</p>',
  'ecs.serverGroup.clusterName':
    '<p>The name of the ECS cluster (group of underlying EC2 hosts) onto which your application will be deployed.</p><p>Note that suggestions here are dependent on the selected account and region combination.</p>',
  'ecs.stage.findImageByTags.labelOrSha':
    "<p>As of now, only Amazon's ECR is supported as a source docker repository.</p>",
  'ecs.serverGroup.stack':
    '<p>An environment variable available within your container, and on which you should base your application configuration at runtime.</p>  <p>Typical values for this parameter are <i>staging</i>, <i>prod</i>, etc.  Keep this parameter short!</p>',
  'ecs.serverGroup.detail':
    '<p>An environment variable available within your container, and on which you should base your application configuration at runtime.</p>  <p>Typical values for this parameter are <i>app</i>, <i>worker</i>, <i>migrator</i>, etc.  Keep this parameter short!</p>',
  'ecs.capacity.overwrite':
    "<p>Checking this box will have the previous server group's capacity overwrite the new <i>desired containers</i> parameter if a previous server group exists.</p>",
  'ecs.capacity.desired': '<p>The starting number of containers, before any autoscaling happens.</p>',
  'ecs.capacity.minimum':
    '<p>The minimum number of containers you can reach as a result of autoscaling.</p> <p>Typically, this represents the bare minimum you can afford to run without impacting your capacity to meet your SLA (Service Level Agreement) objectives</p>',
  'ecs.capacity.maximum': '<p>The maximal number of containers you can reach as a result of autoscaling.</p>',
  'ecs.capacity.reserved.computeUnits':
    '<p>The assured minimal amount of computing capacity your container will be able to use.  1024 units are equal to 1 AWS virtual CPU</p> <p>If other containers on your underlying host are not using their reserved compute capacity, this container will be able to use it.</p>',
  'ecs.capacity.reserved.memory':
    '<p>The maximal amount of memory that your container can use, in megabytes.  Exceeding this amount may result in termination of your container.</p><p>1024 mb = 1 gb</p>',
  'ecs.loadbalancing.targetPort': '<p>The port on which your application is listening for incoming traffic</p>',
  'ecs.iamrole':
    '<p>The IAM role that your container (task, in AWS wording) will inherit.  </p><p>Define a role only if your application needs to access AWS APIs</p>',
  'ecs.placementStrategy':
    '<p>The strategy the container scheduler will be using.  See <a href="http://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-strategies.html" target="_blank">AWS documentation</a> for more details. </p><p>You should at least balance across availability zones</p><p>Custom placement strategies have not been implemented yet.</p>',
  'ecs.capacity.autoscalingPolicies':
    '<p>A predefined MetricAlarm and Autoscaling policy with an Action must exist.</p><p>There is a delay in MetricAlarm recognizing the Autoscaling policy.</p>',
  'ecs.launchtype': '<p>Launch service tasks on your own EC2 instances or on Fargate.</p>',
  'ecs.healthgraceperiod':
    '<p>How long a container will be kept alive despite the load balancer health checks, in seconds.</p>',
  'ecs.publicip': '<p>Assign a public IP address to each task.</p>',
  'ecs.networkMode':
    '<p>awsvpc is the only networking mode that allows you to use Elastic Network Interfaces (ENI).  The default value converts to Bridge on Linux, and NAT on Windows.</p>',
  'ecs.subnet': '<p>The subnet group on which your server group will be deployed.</p>',
  'ecs.securityGroups': '<p>The security group(s) name(s) your containers are deployed with.</p>',
  'ecs.dockerLabels':
    '<p>Additional labels applied to your Docker container.  This metadata can be used to identify your containers, or in conjunction with logging options.  Maps directly to the <a href="https://docs.docker.com/engine/reference/commandline/run/#set-metadata-on-container--l---label---label-file"><b>--label</b> Docker flag</a>.</p> <p>Spinnaker will automatically add the spinnaker.servergroup, spinnaker.stack, spinnaker.detail labels for non-null values.</p>',
  'ecs.logDriver':
    '<p>The container\'s logging driver.  This directly maps to the <a href="https://docs.docker.com/config/containers/logging/configure/#configure-the-default-logging-driver"><b>--log-driver</b> Docker flag.</a></p>',
  'ecs.logOptions':
    '<p>A map of log options.  This directly maps with the <a href="https://docs.docker.com/config/containers/logging/log_tags/"><b>--log-opt</b> Docker flag  </a></p>',
};

Object.keys(helpContents).forEach(key => HelpContentsRegistry.register(key, helpContents[key]));
