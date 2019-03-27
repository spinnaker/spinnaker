import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ConfigBinLink } from './ConfigBinLink';

export const CONFIG_BIN_LINK_COMPONENT = 'spinnaker.titus.serverGroup.scalingPolicy.configBin.link';
module(CONFIG_BIN_LINK_COMPONENT, []).component(
  'configBinLink',
  react2angular(ConfigBinLink, [
    'application',
    'config',
    'clusterName',
    'awsAccountId',
    'region',
    'env',
    'configUpdated',
    'cannedMetrics',
  ]),
);
