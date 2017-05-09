import {module} from 'angular';
import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from 'core/help/helpContents.registry';

const helpContents: any[] = [
  {
    key: 'fastProperty.ttl',
    contents: `<p>Sets an expiration date on the property.</p>
               <p>Leave blank or set to "0" to <b>not</b> set an expiration date.</p>`
  },
];

export const FAST_PROPERTIES_HELP = 'spinnaker.netflix.fastProperties.help';
module(FAST_PROPERTIES_HELP, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    helpContents.forEach((entry: any) => helpContentsRegistry.register(entry.key, entry.contents));
  });
