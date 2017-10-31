import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry } from '@spinnaker/core';

import '../logo/kubernetes.logo.less';
import { KUBERNETES_MANIFEST_COMMAND_BUILDER } from './manifest/manifestCommandBuilder.service';
import { KUBERNETES_MANIFEST_BASIC_SETTINGS } from './manifest/wizard/basicSettings.component';
import { KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/serverGroupCommandBuilder.service';
import { KUBERNETES_MANIFEST_CTRL } from './manifest/wizard/manifestWizard.controller';
import { KUBERNETES_MANIFEST_ENTRY } from './manifest/wizard/manifestEntry.component';
import { KUBERNETES_V2_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroupTransformer.service';
import { KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL } from './serverGroup/details/details.controller';
import { KUBERNETES_V2_SERVER_GROUP_RESIZE_CTRL } from './serverGroup/details/resize/resize.controller';

// load all templates into the $templateCache
const templates = require.context('kubernetes', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const KUBERNETES_V2_MODULE = 'spinnaker.kubernetes.v2';

module(KUBERNETES_V2_MODULE, [
  CLOUD_PROVIDER_REGISTRY,
  KUBERNETES_V2_SERVER_GROUP_COMMAND_BUILDER,
  KUBERNETES_V2_SERVER_GROUP_TRANSFORMER,
  KUBERNETES_V2_SERVER_GROUP_DETAILS_CTRL,
  KUBERNETES_V2_SERVER_GROUP_RESIZE_CTRL,
  KUBERNETES_MANIFEST_BASIC_SETTINGS,
  KUBERNETES_MANIFEST_COMMAND_BUILDER,
  KUBERNETES_MANIFEST_CTRL,
  KUBERNETES_MANIFEST_ENTRY,
]).config((cloudProviderRegistryProvider: CloudProviderRegistry) => {
    cloudProviderRegistryProvider.registerProvider('kubernetes', {
      name: 'Kubernetes',
      providerVersion: 'v2',
      logo: {
        path: require('../logo/kubernetes.icon.svg'),
      },
      serverGroup: {
        commandBuilder: 'kubernetesV2ServerGroupCommandBuilder',
        cloneServerGroupController: 'kubernetesManifestWizardCtrl',
        cloneServerGroupTemplateUrl: require('./manifest/wizard/manifestWizard.html'),
        transformer: 'kubernetesV2ServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/details.html'),
        detailsController: 'kubernetesV2ServerGroupDetailsCtrl',
      }
    });
  });
