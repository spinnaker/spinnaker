'use strict';

const angular = require('angular');
import { react2angular } from 'react2angular';
import { TaskProgressBar } from './TaskProgressBar';

module.exports = angular
  .module('spinnaker.core.task.progressBar.directive', [])
  .component('taskProgressBar', react2angular(TaskProgressBar, ['task']));
