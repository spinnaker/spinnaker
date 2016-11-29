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
    ];

    helpContents.forEach((entry) => helpContentsRegistry.register(entry.key, entry.value));
  });
