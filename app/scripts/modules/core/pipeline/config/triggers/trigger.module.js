'use strict';

const angular = require('angular');

import {RUN_AS_USER_SELECTOR_COMPONENT} from './runAsUserSelector.component';
import {TRAVIS_TRIGGER} from './travis/travisTrigger.module';
import {GIT_TRIGGER} from './git/git.trigger';

module.exports = angular.module('spinnaker.core.pipeline.config.trigger', [
    require('../stages/stage.module.js'),
    require('./cron/cronTrigger.module.js'),
    require('./docker/dockerTrigger.module.js'),
    GIT_TRIGGER,
    require('./jenkins/jenkinsTrigger.module.js'),
    TRAVIS_TRIGGER,
    require('./pipeline/pipelineTrigger.module.js'),
    require('./trigger.directive.js'),
    require('./triggers.directive.js'),
    RUN_AS_USER_SELECTOR_COMPONENT,
  ]);
