import { module, IComponentController, IComponentOptions } from 'angular';

import { PropertyCommand } from '../../domain/propertyCommand.model';
import { Property } from '../../domain/property.domain';
import { AUTHENTICATION_SERVICE, AuthenticationService, IUser } from 'core/authentication/authentication.service';
import { V2ModalWizardService, V2_MODAL_WIZARD_SERVICE } from 'core/modal/wizard/v2modalWizard.service';

export class FastPropertyDetailsComponentController implements IComponentController {
  public isEditing: boolean;
  public isDeleting: boolean;
  public disableAll: boolean;
  public command: PropertyCommand;

  public getValueRowCount(inputValue: string): number {
    return inputValue ? inputValue.split(/\n/).length : 1;
  };

  constructor(private authenticationService: AuthenticationService, private v2modalWizardService: V2ModalWizardService) {
    'ngInject';
  }

  public $onInit() {
    // If the property has an existing id then we want to preserve it's stage for rollback
    if (this.command && this.command.property) {
      this.v2modalWizardService.markComplete('details');
      if (this.command.property.propertyId) {
        this.command.originalProperty = Property.copy(this.command.property);
      }

      const user: IUser = this.authenticationService.getAuthenticatedUser();
      this.command.property.email = user.name;
    }
  }

  public setCompletionState(): void {
    if (this.command.property.key) {
      this.v2modalWizardService.markComplete('details');
    } else {
      this.v2modalWizardService.markIncomplete('details');
    }
  }
}

class FastPropertyDetailsComponent implements IComponentOptions {
  public templateUrl: string = require('./propertyDetails.component.html');
  public controller: any = FastPropertyDetailsComponentController;
  public bindings: any = {
    command: '=',
    isEditing: '=',
    isDeleting: '=',
    disableAll: '='
  };
}

export const FAST_PROPERTY_DETAILS_COMPONENT = 'spinnaker.netflix.fastProperties.details.component';

module(FAST_PROPERTY_DETAILS_COMPONENT, [
  AUTHENTICATION_SERVICE, V2_MODAL_WIZARD_SERVICE,
])
  .component('fastPropertyDetails', new FastPropertyDetailsComponent());
