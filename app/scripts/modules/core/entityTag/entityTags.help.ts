import {module} from 'angular';
import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from 'core/help/helpContents.registry';

const helpContents: any[] = [
  {
    key: 'entityTags.serverGroup.alert',
    contents: `<p>Alerts indicate an issue with a server group. When present, an alert icon 
      <i class="fa fa-exclamation-triangle"></i> will be displayed in the clusters view next to the server group.</p>`
  },
  {
    key: 'entityTags.serverGroup.notice',
    contents: `<p>Notices provide additional context for a server group. When present, an info icon 
      <i class="fa fa-info-circle"></i> will be displayed in the clusters view next to the server group.</p>`
  }
  ];

export const ENTITY_TAGS_HELP = 'spinnaker.core.entityTag.help';
module(ENTITY_TAGS_HELP, [HELP_CONTENTS_REGISTRY])
  .run((helpContentsRegistry: HelpContentsRegistry) => {
    helpContents.forEach((entry: any) => helpContentsRegistry.register(entry.key, entry.contents));
});
