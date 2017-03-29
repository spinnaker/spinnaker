import {module} from 'angular';

export class CategoryButtonListComponentController implements ng.IComponentController {

  public scopeOption: any;
  public categoryName: string;
  public onSelectScope: Function;
  public showNoImpactListForCategory: any = {};

  public selectScope(scopeOption: any) {
    this.onSelectScope({scopeOption: scopeOption});
  }

  public noImpact(categoryScope: any) {
    return categoryScope.instanceCounts.up < 1;
  }

  public toggleNoImpactList(categoryName: string) {
    this.showNoImpactListForCategory[categoryName] = this.showNoImpactListForCategory[categoryName]
      ? !this.showNoImpactListForCategory[categoryName]
      : true;
  }

}

class CategoryButtonListComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./categoryButtonList.component.html');
  public controller: any = CategoryButtonListComponentController;
  public bindings: any = {
    scopeOptions: '=',
    categoryName: '<',
    onSelectScope: '&'
  };
}

export const CATEGORY_BUTTON_LIST_COMPONENT = 'spinnaker.netflix.fastProperty.categoryButtonList.component';

module(CATEGORY_BUTTON_LIST_COMPONENT, [])
  .component('categoryButtonListComponent', new CategoryButtonListComponent());
