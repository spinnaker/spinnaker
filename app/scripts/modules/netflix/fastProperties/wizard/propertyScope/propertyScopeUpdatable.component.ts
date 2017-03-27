import { module, IComponentController, IComponentOptions} from 'angular';
import { FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE, FastPropertyScopeCategoryService } from '../../scope/fastPropertyScopeSearchCategory.service';
import { FAST_PROPERTY_READ_SERVICE } from '../../fastProperty.read.service';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Scope} from '../../domain/scope.domain';
import {IImpactCounts} from '../../domain/impactCounts.interface';

export class FastPropertyScopeReadOnlyComponentController implements IComponentController {
  public isEditing = false;
  public impactCount: string;
  public selectedScope: any;
  public command: PropertyCommand;


  static get $inject() {
    return [
      'fastPropertyScopeSearchCategoryService',
    ];
  }

  constructor(fastPropertyScopeSearchCategoryService: FastPropertyScopeCategoryService) {
    fastPropertyScopeSearchCategoryService.getImpactForScope(this.command.originalScope)
      .then((counts: IImpactCounts) => {
        this.command.originalScope.instanceCounts = counts;
        return this.command.originalScope;
      });
  }

  public selectScope(scopeOption: Scope): void {
    this.selectedScope = scopeOption.copy();
    this.command.scopes = [this.selectedScope];
  }

  public toggleEditScope(): void {
    this.isEditing = !this.isEditing;
  }

  public removeNewScope(): void {
    this.selectedScope = null;
    this.command.scopes = [this.command.originalScope.copy()];
  }
}

class FastPropertyScopeReadOnlyComponent implements IComponentOptions {
  public templateUrl: string = require('./propertyScopeUpdatable.component.html');
  public controller: any = FastPropertyScopeReadOnlyComponentController;
  public bindings: any = {
    command: '='
  };
}

export const FAST_PROPERTY_SCOPE_UPDATABLE_COMPONENT = 'spinnaker.netflix.fastProperty.scope.updatable.component';

module(FAST_PROPERTY_SCOPE_UPDATABLE_COMPONENT, [
  require('core/search/searchResult/searchResult.directive'),
  FAST_PROPERTY_READ_SERVICE,
  FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE
])
  .component('fastPropertyScopeUpdatable', new FastPropertyScopeReadOnlyComponent());
