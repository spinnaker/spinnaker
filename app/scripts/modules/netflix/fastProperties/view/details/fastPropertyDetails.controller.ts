import { module } from 'angular';
import { StateService } from 'angular-ui-router';
import { Subscription } from 'rxjs/Subscription';
import { IModalService } from 'angular-ui-bootstrap';

import { UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../../wizard/updateFastPropertyWizard.controller';
import { DELETE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../../wizard/deleteFastPropertyWizard.controller';
import { CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER } from '../../wizard/cloneFastPropertyToNewScopeWizard.controller';
import { FAST_PROPERTY_READ_SERVICE, FastPropertyReaderService } from '../../fastProperty.read.service';
import { FAST_PROPERTY_HISTORY_COMPONENT } from '../history/fastPropertyHistory.component';
import { Application } from 'core/application/application.model';
import { Property } from '../../domain/property.domain';
import { stateEvents } from 'core/state.events';
import { fastPropertyTtl } from 'core/utils/timeFormatters';

export class FastPropertyDetailsController {

  public property: Property;
  private locationChangeSuccessSubscription: Subscription;
  private dataRefreshUnsubscribe: () => void;
  public propertyNotFound = false;
  public propertyExpires: string;

  constructor(private $uibModal: IModalService,
              private fastPropertyReader: FastPropertyReaderService,
              private $state: StateService,
              private app: Application) {
    'ngInject';
    this.getProperty();
    this.locationChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => this.getProperty());
    if (app && app.getDataSource('properties')) {
      this.dataRefreshUnsubscribe = app.getDataSource('properties').onRefresh(null, () => this.getProperty());
    }
  }
  public $onDestroy(): void {
    if (this.dataRefreshUnsubscribe) {
      this.dataRefreshUnsubscribe();
    }
    if (this.locationChangeSuccessSubscription) {
      this.locationChangeSuccessSubscription.unsubscribe();
    }
  }

  public close(): void {
    this.$state.go('.', {propertyId: null});
    this.propertyNotFound = false;
    this.property = null;
  }


  public extractEnvFromId(propertyId: string) {
    const list = propertyId.split('|');
    return list[2] || 'prod';
  }

  public getProperty(environment?: string, retry = true) {
    const propertyId = this.$state.params.propertyId;
    if (!propertyId) {
      this.property = null;
      return;
    }
    const env = environment || this.extractEnvFromId(propertyId);
    this.fastPropertyReader.getPropByIdAndEnv(propertyId, env)
      .then((results: Property) => {
        this.property = results;
        this.propertyNotFound = false;
        if (this.property.ttl && this.property.ts) {
          this.propertyExpires = fastPropertyTtl(this.property.ts, this.property.ttl);
        } else {
          this.propertyExpires = null;
        }
      })
      .catch(() => {
        const otherEnv = env === 'prod' ? 'test' : 'prod';
        if (retry) {
          this.getProperty(otherEnv, false);
        } else {
          this.property = null;
          this.propertyNotFound = true;
        }
      });
  }

  public editFastProperty(property: Property) {
    this.$uibModal.open({
      templateUrl: require('../../wizard/updateFastPropertyWizard.html'),
      controller: 'updateFastPropertyWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Update Fast Property',
        property: () => property,
        application: () => this.app
      }
    });
  };

  public delete(property: Property): void {
    this.$uibModal.open({
      templateUrl: require('../../wizard/deleteFastPropertyWizard.html'),
      controller: 'deleteFastPropertyWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Delete Fast Property',
        property: () => property,
        application: () => this.app
      }
    });
  };

  public cloneToNewScope(property: Property): void {
    this.$uibModal.open({
      templateUrl: require('../../wizard/cloneFastPropertyToNewScopeWizard.html'),
      controller: 'cloneFastPropertyToNewScopeWizardController',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Clone with new scope',
        property: () => property,
        application: () => this.app
      }
    });
  }

  public showHistory(): void {
    this.$uibModal.open({
      templateUrl: require('../history/history.modal.html'),
      controller: function (property: Property) { this.property = property; },
      controllerAs: 'ctrl',
      bindToController: true,
      size: 'lg modal-fullscreen',
      resolve: {
        property: () => this.property,
      },
    });
  }
}

export const FAST_PROPERTY_DETAILS_CONTROLLER = 'spinnaker.netflix.globalFastProperties.details.controller';

module(FAST_PROPERTY_DETAILS_CONTROLLER, [
  FAST_PROPERTY_READ_SERVICE,
  UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER,
  DELETE_FAST_PROPERTY_WIZARD_CONTROLLER,
  CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER,
  FAST_PROPERTY_HISTORY_COMPONENT,
])
  .controller('FastPropertiesDetailsController', FastPropertyDetailsController);
