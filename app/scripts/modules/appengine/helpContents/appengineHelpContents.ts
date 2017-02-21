import {module} from 'angular';

import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from 'core/help/helpContents.registry';

export const APPENGINE_HELP_CONTENTS_REGISTRY = 'spinnaker.appengine.helpContents.registry';
module(APPENGINE_HELP_CONTENTS_REGISTRY, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    let helpContents = [
      {
        key: 'appengine.serverGroup.repositoryUrl',
        value: `The full URL to the git repository containing the source files for this deployment,
                including protocol. For example, <b>https://github.com/spinnaker/deck.git<b/>`,
      },
      {
        key: 'appengine.serverGroup.gitCredentialType',
        value: `The credential type that will be used to access the git repository for this deployment. 
                You can configure these credentials alongside your App Engine credentials.`
      },
      {
        key: 'appengine.serverGroup.branch',
        value: 'The name of the branch in the above git repository to be used for this deployment.',
      },
      {
        key: 'appengine.serverGroup.configFilepaths',
        value: `Paths to App Engine application configuration files, starting from the git repository root.
                For example, <b>path/to/app.yaml</b> or <b>path/to/cron.yaml</b>.`,
      },
      {
        key: 'appengine.serverGroup.matchBranchOnRegex',
        value: `(Optional) A Jenkins trigger may produce details from multiple repositories and branches. 
                Spinnaker will use the regex specified here to help resolve a branch for the deployment.
                If Spinnaker cannot resolve exactly one branch from the trigger, this pipeline will fail.`,
      },
      {
        key: 'appengine.serverGroup.promote',
        value: 'If selected, the newly deployed server group will receive all traffic.',
      },
      {
        key: 'appengine.serverGroup.stopPreviousVersion',
        value: `If selected, the previously running server group in this server group\'s <b>service</b>
                (Spinnaker load balancer) will be stopped. This option will be respected only if this server group will
                be receiving all traffic and the previous server group is using manual scaling.`,
      },
      {
        key: 'appengine.loadBalancer.shardBy.cookie',
        value: 'Diversion based on a specially named cookie, "GOOGAPPUID." The cookie must be set by the application itself or no diversion will occur.'
      },
      {
        key: 'appengine.loadBalancer.shardBy.ip',
        value: 'Diversion based on applying the modulus operation to a fingerprint of the IP address.'
      },
      {
        key: 'appengine.loadBalancer.migrateTraffic',
        value: `If selected, traffic will be gradually shifted to a single version. For gradual traffic migration, 
                the target version must be located within instances that are configured for 
                both warmup requests and automatic scaling. You must specify the <b>shard by</b> field in this form. 
                Gradual traffic migration is not supported in the App Engine flexible environment.`
      },
      {
        key: 'appengine.loadBalancer.allocations',
        value: 'An allocation is the percent of traffic directed to a server group.'
      },
      {
        key: 'appengine.instance.availability',
        value: `
          An instance's <b>availability</b> is determined by its version (Spinnaker server group).
          <ul>
            <li>Manual scaling versions use resident instances</li>
            <li>Basic scaling versions use dynamic instances</li>
            <li>Auto scaling versions use dynamic instances - but if you specify a number, N, 
                of minimum idle instances, the first N instances will be resident, 
                and additional dynamic instances will be created as necessary.
            </li>
          </ul>`
      },
      {
        key: 'appengine.instance.averageLatency',
        value: 'Average latency over the last minute in milliseconds.'
      },
      {
        key: 'appengine.instance.vmStatus',
        value: 'Status of the virtual machine where this instance lives.'
      },
      {
        key: 'appengine.instance.qps',
        value: 'Average queries per second over the last minute.'
      },
      {
        key: 'appengine.instance.errors',
        value: 'Number of errors since this instance was started.'
      },
      {
        key: 'appengine.instance.requests',
        value: 'Number of requests since this instance was started.'
      }
    ];

    helpContents.forEach((entry) => helpContentsRegistry.register(entry.key, entry.value));
  });
