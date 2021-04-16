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
    "<p>Checking this box will have the previous server group's capacity overwrite the new <i>min</i>, <i>max</i> and <i>desired capacity</i> parameters if a previous server group exists.</p>",
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
  'ecs.dockerimage': 'Docker image for your container, such as nginx:latest',
  'ecs.dockerimagecredentials':
    '<p>The AWS Secrets Manager secret that contains private registry credentials.</p><p>Define credentials only for private registries other than Amazon ECR.</p>',
  'ecs.placementConstraints':
    '<p>Rules for task placement.  See <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-constraints.html" target="_blank">AWS documentation</a> for more details. </p>',
  'ecs.placementConstraintType':
    '<p>To ensure that each task in a particular group is running on a different container instance, use distinctInstance. To restrict the selection to a group of valid candidates, use memberOf. </p>',
  'ecs.placementConstraintExpression':
    '<p>A cluster query language expression to apply to memberOf constraints.  See <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-query-language.html" target="_blank">AWS documentation</a> for more details.</p>',
  'ecs.placementStrategy':
    '<p>The strategy the container scheduler will be using.  See <a href="http://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-strategies.html" target="_blank">AWS documentation</a> for more details. </p><p>You should at least balance across availability zones</p><p>Custom placement strategies have not been implemented yet.</p>',
  'ecs.platformVersion': '<p>Defaults to the latest platform version.</p>',
  'ecs.capacity.copySourceScalingPoliciesAndActions':
    '<p>Copy Application Autoscaling policies and their associated alarms from the previous ECS service.</p>',
  'ecs.launchtype': '<p>Launch service tasks on your own EC2 instances or on Fargate.</p>',
  'ecs.healthgraceperiod':
    '<p>How long a container will be kept alive despite the load balancer health checks, in seconds.</p>',
  'ecs.publicip': '<p>Assign a public IP address to each task.</p>',
  'ecs.networkMode':
    '<p>awsvpc is the only networking mode that allows you to use Elastic Network Interfaces (ENI).  The default value converts to Bridge on Linux, and NAT on Windows.</p>',
  'ecs.subnet':
    '<p>The subnet group(s) on which your server group will be deployed. All subnet groups selected must exist within the same VPC.</p>',
  'ecs.securityGroups': '<p>The security group(s) name(s) your containers are deployed with.</p>',
  'ecs.dockerLabels':
    '<p>Additional labels applied to your Docker container.  This metadata can be used to identify your containers, or in conjunction with logging options.  Maps directly to the <a href="https://docs.docker.com/engine/reference/commandline/run/#set-metadata-on-container--l---label---label-file"><b>--label</b> Docker flag</a>.</p> <p>Spinnaker will automatically add the spinnaker.servergroup, spinnaker.stack, spinnaker.detail labels for non-null values.</p>',
  'ecs.logDriver':
    '<p>The container\'s logging driver.  This directly maps to the <a href="https://docs.docker.com/config/containers/logging/configure/#configure-the-default-logging-driver"><b>--log-driver</b> Docker flag.</a></p>',
  'ecs.logOptions':
    '<p>A map of log options.  This directly maps with the <a href="https://docs.docker.com/config/containers/logging/log_tags/"><b>--log-opt</b> Docker flag  </a></p>',
  'ecs.taskDefinition':
    '<p>The source of the ECS Task Definition. Task Definition contents can either be entering manually via input fields or from a selected JSON file artifact. Artifact file contents should be structured as an ECS "RegisterTaskDefinition" request.</p>',
  'ecs.taskDefinitionArtifact': '<p>The artifact containing the ECS Task Definition.</p>',
  'ecs.containerMappings':
    '<p>The list of expected containers within the Task Definition and which container image they should use. Containers in the Task Definition which are not specified here will be registered as they appear in the artifact.</p>',
  'ecs.containerMappingName':
    '<p>The name of the container. Name should match the <a href="https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ContainerDefinition.html#ECS-Type-ContainerDefinition-name"><b>containerDefinition.name</b></a> field as it appears in the Task Definition.</p>',
  'ecs.containerMappingImage': '<p>The container image the named container should run.</p>',
  'ecs.targetGroupMappings':
    '<p>The list of target groups through which the ECS service will receive load balancer traffic. Each target group is mapped to a container name and port within the Task Definition to specify which container should be registered to the target group.</p>',
  'ecs.loadBalancedContainer':
    '<p>The container in the Task Definition that should receive traffic from the load balancer. Required if a load balancer target group has been specified.</p>',
  'ecs.tags': '<p>The tags to apply to the task definition and the service',
  'ecs.environmentVariables':
    '<p>The environment variable(s) your container are deployed with. SERVER_GROUP, CLOUD_STACK and CLOUD_DETAIL environment variables are used during deployment to identify the task and cannot be set here.</p>',
  'ecs.serviceDiscovery': '<p>The AWS Cloud Map service discovery registries to assign to this service</p>',
  'ecs.serviceDiscoveryRegistry': '<p>The AWS Cloud Map service to use for service discovery registration</p>',
  'ecs.serviceDiscoveryContainerPort':
    '<p>The port to be used for your service discovery service. Required only for services using bridge or host network mode, and for services using awsvpc network mode and a type SRV DNS record',
  'ecs.serviceDiscoveryContainerName':
    '<p>The container name value, already specified in the task definition, to be used for your service discovery service.</p>',
  'ecs.computeOptions':
    '<p>Specify either a <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/launch_types.html" target="_blank">launch type</a> (default) or <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-capacity-providers.html" target="_blank">capacity providers</a> for running your ECS service.</p>',
  'ecs.capacityProviderStrategy':
    '<p>A capacity provider strategy gives you control over how your tasks use one or more capacity providers. See <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-capacity-providers.html#capacity-providers-concepts" target="_blank">AWS documentation</a> for more details. </p>',
  'ecs.capacityProviderName': '<p>The short name of the capacity provider.</p>',
  'ecs.capacityProviderBase':
    '<p>Designates how many tasks, at a minimum, to run on the specified capacity provider. Only one capacity provider in a capacity provider strategy can have a <em>base</em> defined.</p>',
  'ecs.capacityProviderWeight':
    '<p>Designates the relative percentage of the total number of tasks launched that should use the specified capacity provider.</p>',
  'ecs.evaluateExpression':
    '<p>Whether to evaluate <a href="https://spinnaker.io/guides/user/pipeline/expressions/" target="_blank"><b>pipeline expressions</b></a> within the task definition artifact in this stage. Checking this box let\'s you evaluate your task definition artifact coming from external sources.(e.g. GitHub) </p>',
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
