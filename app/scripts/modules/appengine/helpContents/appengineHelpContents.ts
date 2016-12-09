import {module} from 'angular';

import registryModule, {HelpContentsRegistry} from 'core/help/helpContents.registry';

export const APPENGINE_HELP_CONTENTS_REGISTRY = 'spinnaker.appengine.helpContents.registry';

module(APPENGINE_HELP_CONTENTS_REGISTRY, [
    registryModule
  ])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    let helpContents = [
      {
        key: 'appengine.serverGroup.repositoryUrl',
        value: `The full URL to the git repository containing the source files for this deployment,
                including protocol. For example, <b>https://github.com/spinnaker/deck.git<b/>`,
      },
      {
        key: 'appengine.serverGroup.branch',
        value: 'The name of the branch in the above git repository to be used for this deployment.',
      },
      {
        key: 'appengine.serverGroup.appYamlPath',
        value: 'The path to the app.yaml file within the git repository. For example, <b>path/to/app.yaml</b>',
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
        value: `If selected, traffic will be gradually shifted from one version to another single version.
                By default, traffic is shifted immediately. For gradual traffic migration, 
                the target version must be located within instances that are configured for 
                both warmup requests and automatic scaling. You must specify the <b>shard by</b> field in this form. 
                Gradual traffic migration is not supported in the App Engine flexible environment.`
      },
      {
        key: 'appengine.loadBalancer.allocations',
        value: 'An allocation is the percent of traffic directed to a server group.'
      }
    ];

    helpContents.forEach((entry) => helpContentsRegistry.register(entry.key, entry.value));
  });
