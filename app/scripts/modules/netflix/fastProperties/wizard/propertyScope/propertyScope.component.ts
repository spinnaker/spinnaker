import { IComponentController, IComponentOptions, module } from 'angular';

import { V2ModalWizardService } from '@spinnaker/core';

import { PropertyCommand } from '../../domain/propertyCommand.model';
import { Scope } from '../../domain/scope.domain';
import { FAST_PROPERTY_READ_SERVICE } from '../../fastProperty.read.service';
import { FAST_PROPERTY_SCOPE_SEARCH_COMPONENT } from '../../scope/fastPropertyScopeSearch.component';

export class FastPropertyScopeComponentController implements IComponentController {

  public isEditing = false;
  public impactCount: string;
  public impactLoading: boolean;
  public selectedScope: any;
  public command: PropertyCommand;

  constructor(private v2modalWizardService: V2ModalWizardService) {}

  public selectScope(scopeOption: Scope) {
    this.selectedScope = scopeOption.copy();
    this.command.scopes.push(this.selectedScope);
    this.v2modalWizardService.markComplete('scope');
  }

  public toggleEditScope(scopeIndex: number): void {
    const scope: Scope = this.command.scopes[scopeIndex];
    const isEditing = scope.isEditing;
    scope.isEditing = !isEditing;
  }

  public removeScope(scopeIndex: number): void {
    this.command.scopes.splice(scopeIndex, 1);
    if (this.command.scopes.length === 0) {
      this.v2modalWizardService.markIncomplete('scope');
    }
  }
}

class FastPropertyScopeComponent implements IComponentOptions {
  public templateUrl: string = require('./propertyScope.component.html');
  public controller: any = FastPropertyScopeComponentController;
  public bindings: any = {
    command: '='
  };
}

export const FAST_PROPERTY_SCOPE_COMPONENT = 'spinnaker.netflix.fastProperty.scope.component';

module(FAST_PROPERTY_SCOPE_COMPONENT, [
  FAST_PROPERTY_READ_SERVICE,
  FAST_PROPERTY_SCOPE_SEARCH_COMPONENT
])
  .component('fastPropertyScope', new FastPropertyScopeComponent());
