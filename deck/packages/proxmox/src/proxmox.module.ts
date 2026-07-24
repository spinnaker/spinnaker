import { CloudProviderRegistry } from '@spinnaker/core';

import { ProxmoxServerGroupTransformer } from './ProxmoxServerGroupTransformer';
import { ProxmoxInstanceDetails } from './instance/details/ProxmoxInstanceDetails';
import './pipeline/stages/bake/proxmoxBakeStage';
import './pipeline/stages/proxmoxServerGroupStages';
import { ProxmoxServerGroupCommandBuilder } from './serverGroup/configure/proxmoxServerGroupCommandBuilder';
import { ProxmoxCloneServerGroupModal } from './serverGroup/configure/wizard/ProxmoxCloneServerGroupModal';
import {
  ProxmoxServerGroupActions,
  ProxmoxServerGroupInformationSection,
} from './serverGroup/details/ProxmoxServerGroupDetails';
import { proxmoxServerGroupDetailsGetter } from './serverGroup/details/proxmoxServerGroupDetailsGetter';

import './logo/proxmox.logo.less';

CloudProviderRegistry.registerProvider('proxmox', {
  name: 'Proxmox VE',
  serverGroup: {
    transformer: ProxmoxServerGroupTransformer,
    detailsGetter: proxmoxServerGroupDetailsGetter,
    detailsSections: [ProxmoxServerGroupInformationSection],
    detailsActions: ProxmoxServerGroupActions,
    CloneServerGroupModal: ProxmoxCloneServerGroupModal,
    commandBuilder: ProxmoxServerGroupCommandBuilder,
  },
  instance: {
    details: ProxmoxInstanceDetails,
  },
});
