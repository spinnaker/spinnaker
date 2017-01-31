import {module} from 'angular';

export class CategoryButtonListComponentController implements ng.IComponentController {

  public scopeOption: any;
  public categoryName: string;
  public onSelectScope: Function;

  public selectScope(scopeOption: any) {
    this.onSelectScope({scopeOption: scopeOption});
  }

}

class CategoryButtonListComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./categoryButtonList.component.html');
  public controller: any = CategoryButtonListComponentController;
  public bindings: any = {
    scopeOption: '=',
    categoryName: '<',
    onSelectScope: '&'
  };
}

export const CATEGORY_BUTTON_LIST_COMPONENT = 'spinnaker.netflix.fastProperty.categoryButtonList.component';

module(CATEGORY_BUTTON_LIST_COMPONENT, [])
  .component('categoryButtonListComponent', new CategoryButtonListComponent());
