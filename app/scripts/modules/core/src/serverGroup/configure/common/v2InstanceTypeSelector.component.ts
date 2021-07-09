import { IComponentController, IComponentOptions, IScope, module } from 'angular';
import { InstanceTypeService, IPreferredInstanceType } from '../../../instance';

import { IServerGroupCommand } from './serverGroupCommandBuilder.service';

import './instanceTypeSelector.directive.less';

class V2InstanceTypeSelectorController implements IComponentController {
  private command: IServerGroupCommand;
  private onTypeChanged: any;

  private instanceProfile: string;
  private instanceTypes: any;

  public static $inject = ['$scope', 'instanceTypeService'];
  constructor(private $scope: IScope, private instanceTypeService: InstanceTypeService) {}

  public $onInit(): void {
    this.instanceProfile = this.command.viewState.instanceProfile;
    this.instanceTypes =
      this.command.backingData && this.command.backingData.filtered
        ? this.command.backingData.filtered.instanceTypes
        : [];
    this.updateFamilies();
  }

  public $doCheck(): void {
    let updateProfiles = false;
    if (this.command.viewState.instanceProfile !== this.instanceProfile) {
      this.instanceProfile = this.command.viewState.instanceProfile;
      updateProfiles = true;
    }
    const hasFilteredBackingData = this.command.backingData && this.command.backingData.filtered;
    if (hasFilteredBackingData && this.command.backingData.filtered.instanceTypes !== this.instanceTypes) {
      this.instanceTypes = this.command.backingData.filtered.instanceTypes;
      updateProfiles = true;
    }
    if (updateProfiles) {
      this.updateFamilies();
    }
  }

  private updateFamilies() {
    let availableTypes: string[] = [];
    if (this.command.backingData && this.command.backingData.filtered) {
      availableTypes = this.command.backingData.filtered.instanceTypes || [];
    }
    this.instanceTypeService.getCategories(this.command.selectedProvider).then((categories) => {
      categories.forEach((profile) => {
        if (profile.type === this.command.viewState.instanceProfile) {
          if (!this.command.viewState.disableImageSelection) {
            profile.families.forEach((family) => {
              family.instanceTypes.forEach((instanceType) => {
                instanceType.unavailable = availableTypes.every((available) => available !== instanceType.name);
              });
            });
          }
          this.$scope.selectedInstanceProfile = profile;
        }
      });
    });
  }

  public selectInstanceType = (type: IPreferredInstanceType) => {
    if (type.unavailable) {
      return;
    }
    this.command.instanceType = type.name;
    if (this.command.viewState.dirty && this.command.viewState.dirty.instanceType) {
      delete this.command.viewState.dirty.instanceType;
    }

    this.instanceTypeService
      .getInstanceTypeDetails(this.command.selectedProvider, type.name)
      .then((instanceTypeDetails) => {
        this.command.viewState.instanceTypeDetails = instanceTypeDetails;
      });

    this.onTypeChanged && this.onTypeChanged(this.command.instanceType);
  };

  public getStorageDescription = (instanceType: IPreferredInstanceType) => {
    if (this.command.instanceType === instanceType.name && this.command.viewState.overriddenStorageDescription) {
      return this.command.viewState.overriddenStorageDescription;
    } else {
      return instanceType.storage.count + 'x' + instanceType.storage.size;
    }
  };

  public getStorageDescriptionHelpKey = (instanceType: IPreferredInstanceType) => {
    return this.command.instanceType === instanceType.name && this.command.viewState.overriddenStorageDescription
      ? 'instanceType.storageOverridden'
      : null;
  };
}

export const v2InstanceTypeSelector: IComponentOptions = {
  bindings: {
    command: '<',
    onTypeChanged: '=',
  },
  controller: V2InstanceTypeSelectorController,
  controllerAs: 'instanceTypeCtrl',
  templateUrl: require('./instanceTypeDirective.html'),
};

export const V2_INSTANCE_TYPE_SELECTOR = 'spinnaker.core.serverGroup.configure.common.v2instanceTypeSelector';
module(V2_INSTANCE_TYPE_SELECTOR, []).component('v2InstanceTypeSelector', v2InstanceTypeSelector);
