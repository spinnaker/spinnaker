import { module, IComponentController, IComponentOptions} from 'angular';
import { IStateService } from 'angular-ui-router';

export class FastPropertyPodTableController implements IComponentController {

  static get $inject() {
    return [
      '$state'
    ];
  }

  constructor(private $state: IStateService) {}

  public showPropertyDetails(propertyId: string) {
    if (this.$state.current.name.includes('.data.properties')) {
      if (this.$state.current.name.includes('.data.properties.globalFastPropertyDetails')) {
        this.$state.go('^.globalFastPropertyDetails', {propertyId: propertyId}, {inherit: true});
      } else {
        this.$state.go('.globalFastPropertyDetails', {propertyId: propertyId}, {inherit: true});
      }
    }

    if (this.$state.current.name.includes('.application.propInsights')) {
      if (this.$state.current.name.includes('.application.propInsights.properties.propertyDetails')) {
        this.$state.go('^.propertyDetails', {propertyId: propertyId}, {inherit: true});
      } else {
        this.$state.go('.propertyDetails', {propertyId: propertyId}, {inherit: true});
      }
    }
  }
}

class FastPropertyPodTable implements IComponentOptions {
  public templateUrl: string = require('./fastPropertyPodTable.html');
  public controller: any = FastPropertyPodTableController;
  public bindings: any = {
    properties: '=',
    groupedBy: '=?'
  };
}

export const FAST_PROPERTY_POD_TABLE = 'spinnaker.netflix.globalFastProperty.podTable.component';

module(FAST_PROPERTY_POD_TABLE, [
  require('angular-ui-router'),
]).component('fastPropertyPodTable', new FastPropertyPodTable());
