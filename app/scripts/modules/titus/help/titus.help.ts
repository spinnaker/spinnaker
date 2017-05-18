import {module} from 'angular';
import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from '@spinnaker/core';

const helpContents: {[key: string]: string} = {
  'titus.deploy.runtimeLimitSecs': '<p>Maximum amount of time (in seconds) a batch job is allowed to run</p>',
  'titus.deploy.retries': '<p>Number of times to retry this job</p>',
  'titus.deploy.propertyFile': '<p>(Optional) Configures the name to the file used to pass in properties to later stages in the Spinnaker pipeline. The file must be saved into the /logs directory during execution</p>',
  'titus.deploy.iamProfile': 'AWS IAM instance profile to assign to this service',
  'titus.deploy.capacityGroup': 'Used by Titus to ensure capacity guarantees, defaults to the application name if not provided',
  'titus.deploy.network': 'Amount of networking bandwidth to allocate in Mbps',
  'titus.deploy.allocateIP': 'If selected, specifies an IP to be allocated for each of your job’s containers',
  'titus.deploy.softConstraints': 'Soft constraints are enforced on a best efforts basis. For example, if tasks can’t be perfectly balanced across zones, the best available balance is achieved without keeping the tasks pending for execution.',
  'titus.deploy.hardConstraints': 'Constraints must be met and tasks will not be launched if constraint can’t be perfectly met',
  'titus.deploy.efs': 'if completed, allows you to specify an EFS volume to attach to each Task that gets created for the Job',
  'titus.deploy.mountPoint': '(Required) A valid directory to mount the volume, e.g, <samp>/efs</samp>. Invalid locations are <samp>/</samp>, <samp>/data</samp>, and <samp>/logs</samp> as these are reserved directories.',
  'titus.deploy.efsId': '(Required) The EFS file system ID, e.g. <samp> fs-0208c74b</samp>.',
  'titus.job.waitForCompletion': 'if unchecked, marks the stage as successful right away without waiting for the job to complete',
  'titus.bake.fromGitTrigger': 'If checked, gets git details from the specified git trigger. The pipeline will fail when ran manually',
  'titus.bake.repositoryUrl': 'Url to the git repository containing the code to create the Docker image from, <samp>ssh://git@stash.corp.netflix.com:7999/SPKR/orca.git</samp> or <samp>ssh://git@github.com/spinnaker/orca.git</samp>',
  'titus.bake.repositoryHash': '(Optional) The hashcode to the git commit for the image',
  'titus.bake.repositoryBranch': '(Optional) The branch in git to build the image from',
  'titus.bake.repositoryDirectory': '(Optional) If specified, will build the image from the Dockerfile contained in this directory. Default to project root.',
  'titus.bake.imageOrganization': '(Optional) The organization to which this image belongs to, e.g. <samp>spinnaker</samp> for <samp>spinnaker/igor</samp>Defaults to none.',
  'titus.bake.imageName': '(Optional) The name for the image, e.g. <samp>igor</samp> for <samp>spinnaker/igor</samp>Defaults to [git project name].[git repo name].',
  'titus.bake.tags': '(Optional) Comma separated. By default, the <samp>latest</samp> tag is updated. Adds additional tags to label this image <samp>1.0.0-unstable,1.0.0-rc1</samp>',
  'titus.bake.buildParameters': '(Optional) Build time variables to be passed to the Docker image. These are the set of values passed to --build-args in the command line.',
  'titus.serverGroup.traffic': `
      <p>Enables the "inService" scaling process, which is used by Spinnaker and discovery services to determine if the server group is enabled.</p>
      <p>Will be automatically enabled when any non "custom" deployment strategy is selected.</p>`,
  'titus.deploy.securityGroups': 'AWS Security Groups to assign to this service. Security groups are set only if <samp>Allocate IP?</samp> has been selected and are assigned to the Titus AWS Elastic Network Interface.',
  'titus.job.securityGroups': 'AWS Security Groups to assign to this job',

};

export const TITUS_HELP = 'spinnaker.titus.help.contents';
module(TITUS_HELP, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    Object.keys(helpContents).forEach(key => helpContentsRegistry.register(key, helpContents[key]));
  });
