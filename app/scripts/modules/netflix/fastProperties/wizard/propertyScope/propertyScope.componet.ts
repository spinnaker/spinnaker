import { module } from 'angular';
import { FAST_PROPERTY_SCOPE_SEARCH_COMPONENT } from '../../scope/fastPropertyScopeSearch.component.ts';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Scope} from '../../domain/scope.domain';

export class FastPropertyScopeComponentController implements ng.IComponentController {
  public applicationList: any = ['deck', 'mahe'];
  public isEditing: boolean = false;
  public impactCount: string;
  public impactLoading: boolean;
  public selectedScope: any;
  public command: PropertyCommand;


  static get $inject() {
    return [
      'fastPropertyReader'
    ];
  }

  constructor( private fastPropertyReader: any ) {}

  public selectScope(scopeOption: Scope) {
    this.selectedScope = scopeOption;
    this.command.scope = scopeOption;
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
  require('../../fastProperty.read.service'),
  FAST_PROPERTY_SCOPE_SEARCH_COMPONENT
])
  .component('fastPropertyScope', new FastPropertyScopeComponent());
