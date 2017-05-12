import {module} from 'angular';

import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from 'core/help/helpContents.registry';

export const ORACLE_HELP_CONTENTS_REGISTRY = 'spinnaker.oracle.helpContents.registry';
module(ORACLE_HELP_CONTENTS_REGISTRY, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    const helpContents = [
      {
        key: 'oraclebmcs.serverGroup.stack',
        value: '(Optional) <b>Stack</b> Stack name'
      },
      {
        key: 'oraclebmcs.serverGroup.detail',
        value: '(Optional) <b>Detail</b> is a naming component to help distinguish specifics of the server group.'
      }
    ];

    helpContents.forEach((entry) => helpContentsRegistry.register(entry.key, entry.value));
  });
