'use strict';

import _ from 'lodash';

const PROVIDER = 'oracle';

export class OracleServerGroupTransformer {
  normalizeServerGroup(serverGroup) {
    return Promise.resolve(serverGroup);
  }

  convertServerGroupCommandToDeployConfiguration(base) {
    const command = _.defaults({ backingData: [], viewState: [] }, base);
    command.cloudProvider = PROVIDER;
    return command;
  }
}
