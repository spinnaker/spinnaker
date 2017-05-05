import {module} from 'angular';

import { UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../wizard/updateFastPropertyWizard.controller';
import { DELETE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../wizard/deleteFastPropertyWizard.controller';
import { CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER } from '../wizard/cloneFastPropertyToNewScopeWizard.controller';
import { FAST_PROPERTY_READ_SERVICE } from '../fastProperty.read.service';
import {Application} from 'core/application/application.model';
import {Property} from '../domain/property.domain';


export class FastPropertyDetailsController {

  public property: Property;

  constructor(private $uibModal: any,
              private fastPropertyReader: any,
              private fastProperty: any,
              private app: Application) {
    'ngInject';
    this.getProperty();
  }


  public extractEnvFromId(propertyId: string) {
    const list = propertyId.split('|');
    return list[2] || 'prod';
  }

  public getProperty(environment?: string) {
    const env = environment || this.extractEnvFromId(this.fastProperty.propertyId);
    this.fastPropertyReader.getPropByIdAndEnv(this.fastProperty.propertyId, env)
      .then((results: any) => {
        this.property = results.property;
      })
      .catch(() => {
        const otherEnv = env === 'prod' ? 'test' : 'prod';
        this.getProperty(otherEnv);
      });
  }

  public editFastProperty(property: Property) {
    this.$uibModal.open({
      templateUrl: require('../wizard/updateFastPropertyWizard.html'),
      controller: 'updateFastPropertyWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Update Fast Property',
        property: () => property,
        applicationName: () => this.app ? this.app.name : 'spinnakerfp'
      }
    });
  };

  public delete(property: Property): void {
    this.$uibModal.open({
      templateUrl: require('../wizard/deleteFastPropertyWizard.html'),
      controller: 'deleteFastPropertyWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Delete Fast Property',
        property: () => property,
        applicationName: () => this.app ? this.app.name : 'spinnakerfp'
      }
    });
  };

  public cloneToNewScope(property: Property): void {
    this.$uibModal.open({
      templateUrl: require('../wizard/cloneFastPropertyToNewScopeWizard.html'),
      controller: 'cloneFastPropertyToNewScopeWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Clone with new scope',
        property: () => property,
        applicationName: () => this.app ? this.app.name : 'spinnakerfp'
      }
    });
  }
}

export const FAST_PROPERTY_DETAILS_CONTROLLER = 'spinnaker.netflix.globalFastProperties.details.controller';

module(FAST_PROPERTY_DETAILS_CONTROLLER, [
  FAST_PROPERTY_READ_SERVICE,
  require('../fastProperty.write.service'),
  UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER,
  DELETE_FAST_PROPERTY_WIZARD_CONTROLLER,
  CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER
])
  .controller('FastPropertiesDetailsController', FastPropertyDetailsController);

