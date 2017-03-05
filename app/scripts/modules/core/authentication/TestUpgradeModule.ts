import {module} from 'angular';

import {TestBed} from '@angular/core/testing';
import {Injector} from '@angular/core';

module('TEST_UPGRADE_MODULE', []).factory('$$angularInjector', ['$injector', function ($injector: ng.auto.IInjectorService) {
  TestBed.configureTestingModule({
    providers: [
      {provide: '$injector', useValue: $injector}
    ]
  });
  return TestBed.get(Injector);
}]);
export const TEST_UPGRADE_MODULE = 'TEST_UPGRADE_MODULE';
