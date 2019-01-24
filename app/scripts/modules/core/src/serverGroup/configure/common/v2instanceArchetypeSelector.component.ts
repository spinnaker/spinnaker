import { module, IComponentController, IScope, IComponentOptions } from 'angular';
import { includes } from 'lodash';

import { InfrastructureCaches } from 'core/cache';
import { CloudProviderRegistry } from 'core/cloudProvider';
import { ModalWizard } from 'core/modal/wizard/ModalWizard';
import { InstanceTypeService, IInstanceTypeCategory } from 'core/instance';
import { ServerGroupConfigurationService } from './serverGroupConfiguration.service';
import { IServerGroupCommand } from './serverGroupCommandBuilder.service';

class V2InstanceArchetypeSelectorController implements IComponentController {
  private command: IServerGroupCommand;
  public refreshing = false;
  public refreshTime = 0;
  public getInstanceBuilderTemplate: any;
  public onProfileChanged: any;
  public onTypeChanged: any;

  public constructor(
    public $scope: IScope,
    private instanceTypeService: InstanceTypeService,
    private serverGroupConfigurationService: ServerGroupConfigurationService,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    const { $scope } = this;
    this.instanceTypeService.getCategories(this.command.selectedProvider).then(categories => {
      $scope.instanceProfiles = categories;
      if ($scope.instanceProfiles.length % 3 === 0) {
        $scope.columns = 3;
      }
      if ($scope.instanceProfiles.length % 4 === 0) {
        $scope.columns = 4;
      }
      if ($scope.instanceProfiles.length % 5 === 0 || $scope.instanceProfiles.length === 7) {
        $scope.columns = 5;
      }
      this.selectInstanceType(this.command.viewState.instanceProfile);
    });

    if (this.command.region && this.command.instanceType && !this.command.viewState.instanceProfile) {
      this.selectInstanceType('custom');
    }
    // if there are no instance types in the cache, try to reload them
    this.instanceTypeService.getAllTypesByRegion(this.command.selectedProvider).then(results => {
      if (!results || !Object.keys(results).length) {
        this.refreshInstanceTypes();
      }
    });

    this.setInstanceTypeRefreshTime();

    this.getInstanceBuilderTemplate = CloudProviderRegistry.getValue.bind(
      CloudProviderRegistry,
      this.command.cloudProvider,
      'instance.customInstanceBuilderTemplateUrl',
    );

    $scope.$watch('$ctrl.command.instanceType', () => this.updateInstanceType());
  }

  private selectInstanceType = (type: string) => {
    const { $scope } = this;
    if ($scope.selectedInstanceProfile && $scope.selectedInstanceProfile.type === type) {
      type = null;
      $scope.selectedInstanceProfile = null;
    }
    this.command.viewState.instanceProfile = type;
    this.onProfileChanged && this.onProfileChanged(type);
    $scope.instanceProfiles.forEach((profile: IInstanceTypeCategory) => {
      if (profile.type === type) {
        $scope.selectedInstanceProfile = profile;
        const current = this.command.instanceType;
        if (current && !includes(['custom', 'buildCustom'], profile.type)) {
          const found = profile.families.some(family =>
            family.instanceTypes.some(instanceType => instanceType.name === current && !instanceType.unavailable),
          );
          if (!found) {
            this.command.instanceType = null;
          }
        }
      }
    });
  };

  private updateInstanceType = () => {
    if (ModalWizard.renderedPages.length > 0) {
      if (this.command.instanceType) {
        ModalWizard.markComplete('instance-type');
      } else {
        ModalWizard.markIncomplete('instance-type');
      }
    }
  };

  public updateInstanceTypeDetails = () => {
    this.instanceTypeService
      .getInstanceTypeDetails(this.command.selectedProvider, this.command.instanceType)
      .then(instanceTypeDetails => {
        this.command.viewState.instanceTypeDetails = instanceTypeDetails;
      });

    this.onTypeChanged && this.onTypeChanged(this.command.instanceType);
  };

  private setInstanceTypeRefreshTime = () => {
    this.refreshTime = InfrastructureCaches.get('instanceTypes').getStats().ageMax;
  };

  public refreshInstanceTypes = () => {
    this.refreshing = true;
    this.serverGroupConfigurationService.refreshInstanceTypes(this.command.selectedProvider, this.command).then(() => {
      this.setInstanceTypeRefreshTime();
      this.refreshing = false;
    });
  };
}

export class V2InstanceArchetypeSelector implements IComponentOptions {
  public bindings: any = {
    command: '<',
    onProfileChanged: '=',
    onTypeChanged: '=',
  };

  public controller: any = V2InstanceArchetypeSelectorController;
  public controllerAs = 'instanceArchetypeCtrl';
  public templateUrl = require('./v2instanceArchetype.directive.html');
}

export const V2_INSTANCE_ARCHETYPE_SELECTOR = 'spinnaker.core.serverGroup.configure.common.v2instanceArchetypeSelector';
module(V2_INSTANCE_ARCHETYPE_SELECTOR, [
  require('./costFactor').name,
  require('core/presentation/isVisible/isVisible.directive').name,
]).component('v2InstanceArchetypeSelector', new V2InstanceArchetypeSelector());
