import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './helpContents/appengineHelpContents';
import { AppengineInstanceDetails } from './instance/details/AppengineInstanceDetails';
import { AppengineCreateLoadBalancerModal } from './loadBalancer/configure/AppengineCreateLoadBalancerModal';
import {
  AppengineLoadBalancerActions,
  AppengineLoadBalancerDetailsSection,
  useAppengineLoadBalancerDetails,
} from './loadBalancer/details';
import { AppengineLoadBalancerTransformer } from './loadBalancer/transformer';
import logo from './logo/appengine.logo.png';
import './pipeline/stages/deployAppengineConfig/deployAppengineConfigStage';
import './pipeline/stages/destroyAsg/appengineDestroyAsgStage';
import './pipeline/stages/disableAsg/appengineDisableAsgStage';
import './pipeline/stages/editLoadBalancer/appengineEditLoadBalancerStage';
import './pipeline/stages/enableAsg/appengineEnableAsgStage';
import './pipeline/stages/shrinkCluster/appengineShrinkClusterStage';
import './pipeline/stages/startServerGroup/appengineStartServerGroupStage';
import './pipeline/stages/stopServerGroup/appengineStopServerGroupStage';
import { AppengineServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AppengineCloneServerGroupModal } from './serverGroup/configure/wizard/AppengineCloneServerGroupModal';
import {
  AppengineServerGroupActions,
  appengineServerGroupDetailsGetter,
  AppengineServerGroupDetailsSection,
} from './serverGroup/details';
import { AppengineServerGroupTransformer } from './serverGroup/transformer';
import './validation/ApplicationNameValidator';

import './logo/appengine.logo.less';

CloudProviderRegistry.registerProvider('appengine', {
  name: 'App Engine',
  instance: {
    details: AppengineInstanceDetails,
  },
  serverGroup: {
    transformer: AppengineServerGroupTransformer,
    commandBuilder: AppengineServerGroupCommandBuilder,
    detailsActions: AppengineServerGroupActions,
    detailsGetter: appengineServerGroupDetailsGetter,
    detailsSections: [AppengineServerGroupDetailsSection],
    CloneServerGroupModal: AppengineCloneServerGroupModal,
    skipUpstreamStageCheck: true,
  },
  loadBalancer: {
    transformer: AppengineLoadBalancerTransformer,
    useDetailsHook: useAppengineLoadBalancerDetails,
    detailsActions: AppengineLoadBalancerActions,
    detailsSections: [AppengineLoadBalancerDetailsSection],
    CreateLoadBalancerModal: AppengineCreateLoadBalancerModal,
  },
  logo: {
    path: logo,
  },
});

DeploymentStrategyRegistry.registerProvider('appengine', ['custom']);
