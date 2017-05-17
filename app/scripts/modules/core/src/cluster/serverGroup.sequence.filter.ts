import {module} from 'angular';

import {NAMING_SERVICE, NamingService} from 'core/naming/naming.service';

export function serverGroupSequenceFilter(namingService: NamingService) {
  return function (input: string): string {
    if (!input) {
      return null;
    }
    return namingService.getSequence(input) || 'n/a';
  };
}

export const SERVER_GROUP_SEQUENCE_FILTER = 'spinnaker.core.serverGroup.sequence.filter';
module(SERVER_GROUP_SEQUENCE_FILTER, [NAMING_SERVICE])
  .filter('serverGroupSequence', serverGroupSequenceFilter);
