import { module } from 'angular';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Property} from '../../domain/property.domain';
import {AUTHENTICATION_SERVICE, AuthenticationService, IUser} from 'core/authentication/authentication.service';

export class FastPropertyDetailsComponentController implements ng.IComponentController {
  public isEditing: boolean;
  public isDeleting: boolean;
  public command: PropertyCommand;

  public getValueRowCount(inputValue: string): number {
    return inputValue ? inputValue.split(/\n/).length : 1;
  };

  public constructor( authenticationService: AuthenticationService ) {
    // If the property has an existing id then we want to preserve it's stage for rollback
    if (this.command && this.command.property) {
      if (this.command.property.propertyId) {
        this.command.originalProperty = Property.copy(this.command.property);
      }

      let user: IUser = authenticationService.getAuthenticatedUser();
      this.command.property.email = user.name;
    }

  }
}

class FastPropertyDetailsComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./propertyDetails.component.html');
  public controller: any = FastPropertyDetailsComponentController;
  public bindings: any = {
    command: '=',
    isEditing: '=',
    isDeleting: '=',
  };
}

export const FAST_PROPERTY_DETAILS_COMPONENT = 'spinnaker.netflix.fastProperties.details.component';

module(FAST_PROPERTY_DETAILS_COMPONENT, [
  AUTHENTICATION_SERVICE
])
  .component('fastPropertyDetails', new FastPropertyDetailsComponent());
