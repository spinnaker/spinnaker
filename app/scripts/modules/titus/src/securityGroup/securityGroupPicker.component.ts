import { module } from 'angular';
import * as _ from 'lodash';
import { Subject } from 'rxjs';

import {
  CACHE_INITIALIZER_SERVICE,
  IAggregatedAccounts,
  ISecurityGroup,
  SECURITY_GROUP_READER,
  FirewallLabels,
} from '@spinnaker/core';

class SecurityGroupPickerController implements ng.IComponentController {
  public securityGroups: any;
  public availableGroups: ISecurityGroup[];
  public credentials: IAggregatedAccounts;
  public command: any;
  public groupsToEdit: string[];
  public removedGroups: string[];
  public availableSecurityGroups: any[];
  public groupsRemoved: Subject<string[]>;
  public hideLabel: boolean;
  public amazonAccount: string;
  public loaded = false;
  public firewallsLabel: string;

  public $onInit(): void {
    this.firewallsLabel = FirewallLabels.get('firewalls');
  }
}

class SecurityGroupPickerComponent implements ng.IComponentOptions {
  public bindings: any = {
    command: '<',
    groupsRemoved: '<',
    removedGroups: '<',
    groupsToEdit: '=',
    hideLabel: '<',
    amazonAccount: '<',
  };
  public controller: any = SecurityGroupPickerController;
  public template = `
    <div class="clearfix" ng-if="$ctrl.loaded">
      <server-group-security-groups-removed removed="$ctrl.removedGroups"></server-group-security-groups-removed>
      <server-group-security-group-selector command="$ctrl.command" hide-label="$ctrl.hideLabel"
          groups-to-edit="$ctrl.groupsToEdit"
          refresh="$ctrl.refreshSecurityGroups()"
          help-key="titus.deploy.securityGroups"
          available-groups="$ctrl.availableGroups"></server-group-security-group-selector>

      <div class="small" ng-class="{ 'col-md-9': !$ctrl.hideLabel, 'col-md-offset-3': !$ctrl.hideLabel }" ng-if="$ctrl.amazonAccount && $ctrl.command.credentials !== undefined">
        Uses {{$ctrl.firewallsLabel}} from the Amazon account <account-tag account="$ctrl.amazonAccount" pad="right"></account-tag>
      </div>
    </div>
`;
}

export const TITUS_SECURITY_GROUP_PICKER = 'spinnaker.titus.securityGroup.picker.component';
module(TITUS_SECURITY_GROUP_PICKER, [SECURITY_GROUP_READER, CACHE_INITIALIZER_SERVICE]).component(
  'titusSecurityGroupPicker',
  new SecurityGroupPickerComponent(),
);
