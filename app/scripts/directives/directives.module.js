'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.directives', [
  require('./gist.directive.js'),
  require('./help.directive.js'),
  require('./modalWizard.js'),
  require('./accountLabelColor.directive.js'),
  require('utils/scrollTriggerService.js'),
  require('utils/lodash.js'),
])
//.directive('accountLabelColor', require('./accountLabelColor.directive.js'))
.directive('accountSelectField', require('./accountSelectField.directive.js'))
.directive('accountTag', require('./accountTag.directive.js'))
.directive('autoFocus', require('./autofocus.directive.js'))
.directive('availabilityZoneSelector', require('./availabilityZoneSelector.directive.js'))
.directive('checklist', require('./checklist.directive.js'))
.directive('collapsibleSection', require('./collapsibleSection.directive.js'))
.directive('modalOverlay', require('./dynamicOverlay.directive.js'))
.directive('insightMenu', require('./insightmenu.directive.js'))
.directive('instanceList', require('./instanceList.directive.js'))
.directive('instanceListBody', require('./instanceListBody.directive.js'))
.directive('instances', require('./instances.directive.js'))
.directive('loadBalancerAvailabilityZoneSelector', require('./loadBalancerAvailabilityZoneSelector.directive.js'))
.directive('modalClose', require('./modalClose.directive.js'))
.directive('modalPage', require('./modalPage.directive.js'))
.directive('multiPageModal', require('./multiPageModal.directive.js'))
.directive('panelProgress', require('./panelProgress.directive.js'))
.directive('regionSelectField', require('./regionSelectField.directive.js'))
.directive('serverGroup', require('./serverGroup.directive.js'))
.directive('sortToggle', require('./sorttoggle.directive.js'))
.directive('stateActive', require('./stateactive.directive.js'))
.directive('submitButton', require('./submitButton.directive.js'))
.directive('subnetSelectField', require('./subnetSelectField.directive.js'))
.directive('isVisible', require('./visible.js'))
.directive('wizardPage', require('./wizardPage.directive.js'))
.directive('gceRegionSelectField', require('./gce/gceRegionSelectField.directive.js'))
.directive('gceZoneSelectField', require('./gce/gceZoneSelectField.directive.js'))
.name;
