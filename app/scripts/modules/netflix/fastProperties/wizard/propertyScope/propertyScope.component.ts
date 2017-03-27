import { module, IComponentController, IComponentOptions} from 'angular';
import { FAST_PROPERTY_SCOPE_SEARCH_COMPONENT } from '../../scope/fastPropertyScopeSearch.component';
import { FAST_PROPERTY_READ_SERVICE } from '../../fastProperty.read.service';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Scope} from '../../domain/scope.domain';

export class FastPropertyScopeComponentController implements IComponentController {
  public isEditing = false;
  public impactCount: string;
  public impactLoading: boolean;
  public selectedScope: any;
  public command: PropertyCommand;

  public selectScope(scopeOption: Scope) {
    this.selectedScope = scopeOption.copy();
    this.command.scopes.push(this.selectedScope);
  }

  public toggleEditScope(scopeIndex: number): void {
    let scope: Scope = this.command.scopes[scopeIndex];
    let isEditing = scope.isEditing;
    scope.isEditing = !isEditing;
  }

  public removeScope(scopeIndex: number): void {
    this.command.scopes.splice(scopeIndex, 1);
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
  require('core/search/searchResult/searchResult.directive'),
  FAST_PROPERTY_READ_SERVICE,
  FAST_PROPERTY_SCOPE_SEARCH_COMPONENT
])
  .component('fastPropertyScope', new FastPropertyScopeComponent());
