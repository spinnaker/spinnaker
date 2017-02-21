import {Type} from '@angular/core';

export interface IDowngradeItem {
  moduleName: string;
  injectionName: string;
  moduleClass: Type<any>;
  inputs?: string[];
  outputs?: string[];
}
