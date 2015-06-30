'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.directives', [
  require('./gist.directive.js'),
  require('./help.directive.js'),
  require('./modalWizard.js'),
])
.directive(require('./accountLabelColor.directive.js'))
.directive(require('./accountSelectField.js'))
.directive(require('./accountTag.js'))
.directive(require('./autoScroll.directive.js'))
.directive(require('./autofocus.js'))
.directive(require('./availabilityZoneSelector.js'))
.directive(require('./checklist.directive.js'))
.directive(require('./collapsibleSection.js'))
.directive(require('./dynamicOverlay.js'))
.directive(require('./insightmenu.js'))
.directive(require('./instanceList.directive.js'))
.directive(require('./instanceListBody.directive.js'))
.directive(require('./instances.js'))
.directive(require('./katoError.js'))
.directive(require('./katoProgress.js'))
.directive(require('./loadBalancerAvailabilityZoneSelector.js'))
.directive(require('./modalClose.js'))
.directive(require('./modalPage.js'))
.directive(require('./multiPageModal.js'))
.directive(require('./panelProgress.js'))
.directive(require('./regionSelectField.directive.js'))
.directive(require('./serverGroup.js'))
.directive(require('./sorttoggle.js'))
.directive(require('./stateactive.js'))
.directive(require('./submitButton.js'))
.directive(require('./subnetSelectField.js'))
.directive(require('./visible.js'))
.directive(require('./wizardPage.js'));
