import { module, noop } from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_TARGETSHAPESELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.capacity.targetShape.directive';

module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_TARGETSHAPESELECTOR_DIRECTIVE, []).directive(
  'gceTargetShapeSelector',
  function () {
    return {
      restrict: 'E',
      templateUrl: require('./targetShapeSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: noop,
    };
  },
);
