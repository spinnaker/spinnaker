import {module} from 'angular';
import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from 'core/help/helpContents.registry';

const helpContents: any[] = [
  {
    key: 'trafficGuard.region',
    contents: '<p>Required; you can select the wildcard (*) to include all regions.</p>'
  },
  {
    key: 'trafficGuard.stack',
    contents: `<p>Optional; you can use the wildcard (*) to include all stacks (including no stack). 
               To apply the guard <em>only</em> to a cluster without a stack, leave this field blank.</p>`
  },
  {
    key: 'trafficGuard.detail',
    contents: `<p>Optional; you can use the wildcard (*) to include all stacks (including no detail). 
               To apply the guard <em>only</em> to a cluster without a detail, leave this field blank.</p>`
  },
];

export const TRAFFIC_GUARD_CONFIG_HELP = 'spinnaker.core.application.config.trafficGuard.help.contents';
module(TRAFFIC_GUARD_CONFIG_HELP, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    helpContents.forEach((entry: any) => helpContentsRegistry.register(entry.key, entry.contents));
  });
