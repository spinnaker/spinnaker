import { module } from 'angular';
import { FAST_PROPERTY_SCOPE_SEARCH_COMPONENT } from '../../scope/fastPropertyScopeSearch.component';
import { FAST_PROPERTY_READ_SERVICE } from '../../fastProperty.read.service';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Scope} from '../../domain/scope.domain';

export class FastPropertyScopeComponentController implements ng.IComponentController {
  public isEditing = false;
  public impactCount: string;
  public impactLoading: boolean;
  public selectedScope: any;
  public command: PropertyCommand;

  public selectScope(scopeOption: Scope) {
    this.selectedScope = scopeOption.copy();
    this.command.scope = this.selectedScope;
  }

  public toggleEditScope() {
    this.isEditing = !this.isEditing;

  }
}

class FastPropertyScopeComponent implements ng.IComponentOptions {
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
