import { module, IComponentOptions, IComponentController } from 'angular';
import { FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE } from '../../scope/fastPropertyScopeSearchCategory.service';
import { FAST_PROPERTY_READ_SERVICE } from '../../fastProperty.read.service';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Scope} from '../../domain/scope.domain';
import {IImpactCounts} from '../../domain/impactCounts.interface';

export class FastPropertyScopeReadOnlyComponentController implements IComponentController {
  public applicationList: any = ['deck', 'mahe'];
  public isEditing = false;
  public impactCount: string;
  public impactLoading: boolean;
  public selectedScope: any;
  public command: PropertyCommand;

  constructor(fastPropertyScopeSearchCategoryService: any) {
    'ngInject';
    fastPropertyScopeSearchCategoryService.getImpactForScope(this.command.scopes[0])
      .then((counts: IImpactCounts) => {
        this.command.scopes[0].instanceCounts = counts;
        return this.command.scopes;
      });
  }

  public selectScope(scopeOption: Scope) {
    this.selectedScope = scopeOption;
    this.command.scopes = [scopeOption];
  }

}

class FastPropertyScopeReadOnlyComponent implements IComponentOptions {
  public templateUrl: string = require('./propertyScopeReadOnly.component.html');
  public controller: any = FastPropertyScopeReadOnlyComponentController;
  public bindings: any = {
    command: '='
  };
}

export const FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT = 'spinnaker.netflix.fastProperty.scope.readOnly.component';

module(FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT, [
  FAST_PROPERTY_READ_SERVICE,
  FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE
])
  .component('fastPropertyScopeReadOnly', new FastPropertyScopeReadOnlyComponent());
