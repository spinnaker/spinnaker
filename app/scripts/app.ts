///<reference path="../../node_modules/zone.js/dist/zone.js.d.ts"/>

// define TS-generated helper code once for the app rather than in every file
// this also requires `noEmitHelpers: true` to be set in tsconfig.json
// this can be removed when switching to TS 2.2
// https://github.com/ngParty/ts-helpers
import 'ts-helpers';
import * as angular from 'angular';
import {platformBrowserDynamic} from '@angular/platform-browser-dynamic';

import {DOWNGRADED_MODULE_NAMES, DOWNGRADED_COMPONENT_MODULE_NAMES, SpinnakerModule} from './app.module';
import {NETFLIX_MODULE} from './modules/netflix/netflix.module';
import {APPENGINE_MODULE} from './modules/appengine/appengine.module';
import {AUTHENTICATION_SERVICE} from './modules/core/authentication/authentication.service';

module.exports = angular.module('netflix.spinnaker', [
  NETFLIX_MODULE,
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
  AUTHENTICATION_SERVICE,
  ...DOWNGRADED_MODULE_NAMES,
  ...DOWNGRADED_COMPONENT_MODULE_NAMES
]);

const events: any = {
  'addEventListener:scroll': true,
  'addEventListener:mouseenter': true,
  'addEventListener:mouseleave' : true,
  'addEventListener:mousemove': true,
};

// const twilightZone = Zone.current.fork(Zone['wtfZoneSpec']).fork({
const twilightZone = Zone.current.fork({
  name: 'twilight',
  onScheduleTask : function (parentZoneDelegate: ZoneDelegate, currentZone: Zone, targetZone: Zone, task: Task): Task {
    let cancel = false;
    if (task.type === 'eventTask') {
      const parts = task.source.split('.');
      if (events[parts[1]]) {
        cancel = true;
      }
    } else if (task.type === 'macroTask') {
      if (task.source === 'requestAnimationFrame') {
        cancel = true;
      }
    }
    if (cancel) {
      // console.log("Escaping NgZone", task.source);
      task.cancelScheduleRequest();
      return currentZone.parent.scheduleTask(task);
    }
    return parentZoneDelegate.scheduleTask(targetZone, task);
  }
});

twilightZone.run(() => {
  platformBrowserDynamic().bootstrapModule(SpinnakerModule);
});
