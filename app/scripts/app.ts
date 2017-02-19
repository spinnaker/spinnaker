// define TS-generated helper code once for the app rather than in every file
// this also requires `noEmitHelpers: true` to be set in tsconfig.json
// this can be removed when switching to TS 2.2
// https://github.com/ngParty/ts-helpers
import 'ts-helpers';
import * as angular from 'angular';
import {platformBrowserDynamic} from '@angular/platform-browser-dynamic';
import {UpgradeModule} from '@angular/upgrade/static';

import {DOWNGRADED_MODULE_NAMES, DOWNGRADED_COMPONENT_MODULE_NAMES, SpinnakerModule} from './app.module';
import {APPENGINE_MODULE} from './modules/appengine/appengine.module';

module.exports = angular.module('netflix.spinnaker', [
  require('./modules/netflix/netflix.module.js'),
  require('./modules/core/core.module.js'),
  require('./modules/amazon/aws.module.js'),
  require('./modules/google/gce.module.js'),
  require('./modules/cloudfoundry/cf.module.js'),
  require('./modules/titus/titus.module.js'),
  require('./modules/azure/azure.module.js'),
  require('./modules/kubernetes/kubernetes.module.js'),
  require('./modules/openstack/openstack.module.js'),
  require('./modules/docker/docker.module.js'),
  APPENGINE_MODULE,
  ...DOWNGRADED_MODULE_NAMES,
  ...DOWNGRADED_COMPONENT_MODULE_NAMES
]);

platformBrowserDynamic().bootstrapModule(SpinnakerModule).then(platformRef => {
  const upgrade = platformRef.injector.get(UpgradeModule) as UpgradeModule;
  upgrade.bootstrap(document.body, ['netflix.spinnaker']);
});
