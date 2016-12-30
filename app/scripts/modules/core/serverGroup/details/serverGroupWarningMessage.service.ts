import {module} from 'angular';

import {ServerGroup} from 'core/domain';

export class ServerGroupWarningMessageService {

  static get $inject(): string[] {
    return ['$templateCache', '$interpolate'];
  }

  constructor(private $templateCache: ng.ITemplateCacheService,
              private $interpolate: ng.IInterpolateService) {}

  public getMessage(serverGroup: ServerGroup): string {
    const template: string = this.$templateCache.get<string>(require('./deleteLastServerGroupWarning.html'));
    return this.$interpolate(template)({deletingServerGroup: serverGroup});
  }
}

export const SERVER_GROUP_WARNING_MESSAGE_SERVICE = 'spinnaker.core.serverGroup.details.warningMessage.service';
module(SERVER_GROUP_WARNING_MESSAGE_SERVICE, [])
  .service('serverGroupWarningMessageService', ServerGroupWarningMessageService);
