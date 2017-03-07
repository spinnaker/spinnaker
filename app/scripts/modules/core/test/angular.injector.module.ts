import {Injector} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {module} from 'angular';

export const ANGULAR_INJECTOR_MODULE = 'ANGULAR_INJECTOR_MODULE';
module(ANGULAR_INJECTOR_MODULE, []).factory('$$angularInjector', ['$injector', function ($injector: ng.auto.IInjectorService) {
  TestBed.configureTestingModule({
    providers: [
      {provide: '$injector', useValue: $injector}
    ]
  });
  return TestBed.get(Injector);
}]);
