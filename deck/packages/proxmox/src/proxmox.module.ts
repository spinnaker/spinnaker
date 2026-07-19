import { CloudProviderRegistry } from '@spinnaker/core';

import { ProxmoxServerGroupTransformer } from './ProxmoxServerGroupTransformer';
import { ProxmoxInstanceDetails } from './instance/details/ProxmoxInstanceDetails';
import {
  ProxmoxServerGroupActions,
  ProxmoxServerGroupInformationSection,
} from './serverGroup/details/ProxmoxServerGroupDetails';
import { proxmoxServerGroupDetailsGetter } from './serverGroup/details/proxmoxServerGroupDetailsGetter';

CloudProviderRegistry.registerProvider('proxmox', {
  name: 'Proxmox VE',
  serverGroup: {
    transformer: ProxmoxServerGroupTransformer,
    detailsGetter: proxmoxServerGroupDetailsGetter,
    detailsSections: [ProxmoxServerGroupInformationSection],
    detailsActions: ProxmoxServerGroupActions,
  },
  instance: {
    details: ProxmoxInstanceDetails,
  },
});
