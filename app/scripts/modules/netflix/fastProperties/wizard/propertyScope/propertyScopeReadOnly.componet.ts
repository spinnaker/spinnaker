import { module } from 'angular';
import { FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE } from '../../scope/fastPropertyScopeSearchCategory.service';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Scope} from '../../domain/scope.domain';
import {IImpactCounts} from '../../domain/impactCounts.interface';

export class FastPropertyScopeReadOnlyComponentController implements ng.IComponentController {
  public applicationList: any = ['deck', 'mahe'];
  public isEditing: boolean = false;
  public impactCount: string;
  public impactLoading: boolean;
  public selectedScope: any;
  public command: PropertyCommand;


  static get $inject() {
    return [
      'fastPropertyReader',
      'fastPropertyScopeSearchCategoryService',
    ];
  }

  constructor(
    private fastPropertyReader: any,
    private fastPropertyScopeSearchCategoryService: any ) {
    fastPropertyScopeSearchCategoryService.getImpactForScope(this.command.scope)
      .then((counts: IImpactCounts) => {
        this.command.scope.instanceCounts = counts;
        return this.command.scope;
      });

  }

  public selectScope(scopeOption: Scope) {
    this.selectedScope = scopeOption;
    this.command.scope = scopeOption;
  }

}

class FastPropertyScopeReadOnlyComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./propertyScopeReadOnly.component.html');
  public controller: any = FastPropertyScopeReadOnlyComponentController;
  public bindings: any = {
    command: '='
  };
}

export const FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT = 'spinnaker.netflix.fastProperty.scope.readOnly.component';

module(FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT, [
  require('../../../../core/search/searchResult/searchResult.directive'),
  require('../../fastProperty.read.service'),
  FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE
])
  .component('fastPropertyScopeReadOnly', new FastPropertyScopeReadOnlyComponent());
