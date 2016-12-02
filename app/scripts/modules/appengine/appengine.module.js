import {module} from 'angular';

import {APPENGINE_CACHE_CONFIGURER} from './cache/cacheConfigurer.service';
import {APPENGINE_CLONE_SERVER_GROUP_CTRL} from './serverGroup/configure/wizard/cloneServerGroup.controller';
import {APPENGINE_HELP_CONTENTS_REGISTRY} from './helpContents/appengineHelpContents';
import {APPENGINE_LOAD_BALANCER_TRANSFORMER} from './loadBalancer/transformer';
import {APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL} from './serverGroup/configure/wizard/basicSettings.controller';
import {APPENGINE_SERVER_GROUP_COMMAND_BUILDER} from './serverGroup/configure/serverGroupCommandBuilder.service';
import {APPENGINE_SERVER_GROUP_DETAILS_CONTROLLER} from './serverGroup/details/appengine.details.controller';
import {APPENGINE_SERVER_GROUP_TRANSFORMER} from './serverGroup/transformer';

let templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const APPENGINE_MODULE = 'spinnaker.appengine';

module(APPENGINE_MODULE, [
    APPENGINE_CACHE_CONFIGURER,
    APPENGINE_CLONE_SERVER_GROUP_CTRL,
    APPENGINE_HELP_CONTENTS_REGISTRY,
    APPENGINE_LOAD_BALANCER_TRANSFORMER,
    APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL,
    APPENGINE_SERVER_GROUP_COMMAND_BUILDER,
    APPENGINE_SERVER_GROUP_DETAILS_CONTROLLER,
    APPENGINE_SERVER_GROUP_TRANSFORMER,
  ])
  .config((cloudProviderRegistryProvider) => {
    cloudProviderRegistryProvider.registerProvider('appengine', {
      name: 'App Engine',
      cache: {
        configurer: 'appengineCacheConfigurer',
      },
      serverGroup: {
        transformer: 'appengineServerGroupTransformer',
        detailsController: 'appengineServerGroupDetailsCtrl',
        detailsTemplateUrl: require('./serverGroup/details/details.html'),
        commandBuilder: 'appengineServerGroupCommandBuilder',
        cloneServerGroupController: 'appengineCloneServerGroupCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      },
      loadBalancer: {
        transformer: 'appengineLoadBalancerTransformer',
      },
    });
  });
