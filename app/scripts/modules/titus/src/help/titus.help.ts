import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'titus.deploy.runtimeLimitSecs': '<p>Maximum amount of time (in seconds) a batch job is allowed to run</p>',
  'titus.deploy.retries': '<p>Number of times to retry this job</p>',
  'titus.deploy.propertyFile':
    '<p>(Optional) Configures the name to the file used to pass in properties to later stages in the Spinnaker pipeline. The file must be saved into the /logs directory during execution</p>',
  'titus.deploy.iamProfile': 'AWS IAM instance profile to assign to this service',
  'titus.deploy.capacityGroup':
    'Used by Titus to ensure capacity guarantees, defaults to the application name if not provided',
  'titus.deploy.command':
    '<p>(Optional) Defines one or more custom commands. If multiple commands are defined, they must be comma delimited with no spaces: <b>cmd1,cmd2</b></p>',
  'titus.deploy.entrypoint':
    '<p>(Optional) Defines one or more entrypoints. If multiple entrypoints are defined, they must be comma delimited with no spaces: <b>entry1,entry2</b></p>',
  'titus.deploy.migrationPolicy': 'Defines how the tasks for this job will be migrated during an infrastructure change',
  'titus.deploy.network': 'Amount of networking bandwidth to allocate in Mbps',
  'titus.deploy.gpu': 'Number of GPUs to use. WARNING: only use if directed by Titus team, otherwise leave at 0',
  'titus.deploy.softConstraints':
    'Soft constraints are enforced on a best efforts basis. For example, if tasks can’t be perfectly balanced across zones, the best available balance is achieved without keeping the tasks pending for execution.',
  'titus.deploy.hardConstraints':
    'Constraints must be met and tasks will not be launched if constraint can’t be perfectly met',
  'titus.deploy.efs':
    'if completed, allows you to specify an EFS volume to attach to each Task that gets created for the Job',
  'titus.deploy.mountPoint':
    '(Required) A valid directory to mount the volume, e.g, <samp>/efs</samp>. Invalid locations are <samp>/</samp>, <samp>/data</samp>, and <samp>/logs</samp> as these are reserved directories.',
  'titus.deploy.efsId': '(Required) The EFS file system ID, e.g. <samp> fs-0208c74b</samp>.',
  'titus.deploy.efsRelativeMountPoint': '(Optional) Relative dir within FS ID, e.g. <samp>/dirInVol</samp>',
  'titus.job.waitForCompletion':
    'if unchecked, marks the stage as successful right away without waiting for the job to complete',
  'titus.bake.fromGitTrigger': 'If checked, gets git details from the specified git trigger.',
  'titus.bake.repositoryUrl':
    'Url to the git repository containing the code to create the Docker image from, <samp>ssh://git@stash.corp.netflix.com:7999/SPKR/orca.git</samp> or <samp>ssh://git@github.com/spinnaker/orca.git</samp>',
  'titus.bake.repositoryHash': '(Optional) The hashcode to the git commit for the image',
  'titus.bake.repositoryBranch': '(Optional) The branch in git to build the image from',
  'titus.bake.repositoryDirectory':
    '(Optional) If specified, will build the image from the Dockerfile contained in this directory. Default to project root.',
  'titus.bake.imageOrganization':
    '(Optional) The organization to which this image belongs to, e.g. <samp>spinnaker</samp> for <samp>spinnaker/igor</samp>Defaults to none.',
  'titus.bake.imageName':
    '(Optional) The name for the image, e.g. <samp>igor</samp> for <samp>spinnaker/igor</samp>Defaults to [git project name].[git repo name].',
  'titus.bake.tags':
    '(Optional) Comma separated. By default, the <samp>latest</samp> tag is updated. Adds additional tags to label this image <samp>1.0.0-unstable,1.0.0-rc1</samp>',
  'titus.bake.buildParameters':
    '(Optional) Build time variables to be passed to the Docker image. These are the set of values passed to --build-args in the command line.',
  'titus.serverGroup.subnet': `The subnet selection determines the VPC in which your container will run. Options vary by account and region. The most common are: 
    <ul>
      <li><b>titus</b>: instances will be restricted to internal clients with their own NAT gateways</li>
      <li><b>internal</b> instances will be restricted to internal clients (i.e. require VPN access)</li>
      <li><b>external</b> instances will be publicly accessible and running in VPC</li>
    </ul>
  `,
  'titus.serverGroup.traffic': `
      <p>Enables the "inService" scaling process, which is used by Spinnaker and discovery services to determine if the server group is enabled.</p>
      <p>Will be automatically enabled when any non "custom" deployment strategy is selected.</p>`,
  'titus.deploy.imageId':
    'This value has been manually overridden. To edit this value, please update the <b><i>imageId</i></b> attribute in the stage JSON. Spinnaker expects this to follow the <b><i>name:tag</i></b> format, or the <b><i>imageName</i></b> directly from Jenkins',
  'titus.deploy.securityGroups':
    'AWS Security Groups to assign to this service. Security groups are set only if <samp>Allocate IP?</samp> has been selected and are assigned to the Titus AWS Elastic Network Interface.',
  'titus.job.capacityGroup': 'Capacity Group will default to application name if not specified.',
  'titus.job.securityGroups': 'AWS Security Groups to assign to this job',
  'titus.autoscaling.cooldown': `
      <p>The amount of time, in seconds, after a scaling activity completes where previous trigger-related scaling
        activities can influence future scaling events.</p>
      <p>For scale out policies, while the cooldown period is in effect, the capacity that has been added by the
        previous scale out event that initiated the cooldown is calculated as part of the desired capacity for the next
        scale out. The intention is to continuously (but not excessively) scale out. For example, an alarm triggers a
        step scaling policy to scale out an Amazon ECS service by 2 tasks, the scaling activity completes successfully,
        and a cooldown period of 5 minutes starts. During the Cooldown period, if the alarm triggers the same policy
        again but at a more aggressive step adjustment to scale out the service by 3 tasks, the 2 tasks that were added
        in the previous scale out event are considered part of that capacity and only 1 additional task is added to the desired count.</p>
      <p>For scale in policies, the cooldown period is used to block subsequent scale in requests until it has expired.
        The intention is to scale in conservatively to protect your application's availability. However, if another
        alarm triggers a scale out policy during the cooldown period after a scale in, Application Auto Scaling scales
        out your scalable target immediately.</p>
  `,
  'titus.autoscaling.scaleIn.cooldown': `
    <p>The amount of time, in seconds, after a scale in activity completes before another scale in activity can start.</p>
    <p>The cooldown period is used to block subsequent scale in requests until it has expired. The intention is to scale
      in conservatively to protect your application's availability. However, if another alarm triggers a scale out policy
      during the cooldown period after a scale in, Application Auto Scaling scales out your scalable target immediately.</p>
  `,
  'titus.autoscaling.scaleOut.cooldown': `
    <p>The amount of time, in seconds, after a scale out activity completes before another scale out activity can start.</p>
    <p>While the cooldown period is in effect, the capacity that has been added by the previous scale out event that
      initiated the cooldown is calculated as part of the desired capacity for the next scale out. The intention is to
      continuously (but not excessively) scale out.</p>
  `,
  'titus.disruptionbudget.description': `
    <p>
      The Job Disruption Budget is part of the job descriptor, and defines the behavior of how containers of the
      job can be relocated.
      <a href="http://manuals.test.netflix.net/view/titus-docs/mkdocs/master/disruption_budget/" target="_blank">
        Read the full documentation
      </a>
    </p>
  `,
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
