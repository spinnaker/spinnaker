import {NgModule, destroyPlatform} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {platformBrowserDynamic} from '@angular/platform-browser-dynamic';
import {UpgradeModule, downgradeInjectable} from '@angular/upgrade/static';
import * as angular from 'angular';

import {bootstrap, html} from 'core/utils/test/helpers';
import {DUMMY_NG2_SERVICE, DUMMY_DOWNGRADE, DummyNg2Service} from './dummyNg2.service';
import {DUMMY_NG1_SERVICE, DummyNg1Service} from './dummyNg1.service';

describe('test the angular 1 dummy service', () => {

  beforeEach(() => destroyPlatform());
  afterEach(() => destroyPlatform());

  it('should run all the tests, checking that the messages are right', (done) => {

    // TODO: ANG, once we get a pattern established, module creation should be extracted to helpers.js
    @NgModule({
      imports: [BrowserModule, UpgradeModule],
      providers: [
        DummyNg2Service
      ]
    })
    class TestModule {
      ngDoBootstrap() {}
    }

    // TODO: ANG, once we get a pattern established, downgraded module creation should be extracted to helpers.js
    const mod: any =
      angular.module(DUMMY_NG2_SERVICE, []).factory(DUMMY_DOWNGRADE.injectionName, downgradeInjectable(DummyNg2Service));
    bootstrap(platformBrowserDynamic(), TestModule, html('<div>'), [mod.name, DUMMY_NG1_SERVICE])
      .then((upgrade) => {
        const injector: any = upgrade.$injector;
        const dummyNg2Service: DummyNg2Service = injector.get(DUMMY_DOWNGRADE.injectionName) as DummyNg2Service;
        expect(dummyNg2Service).toBeDefined();
        expect(dummyNg2Service.getMessage()).toBe('Dummy NG2 Service');

        const dummyNg1Service: DummyNg1Service = injector.get('dummyNg1Service') as DummyNg1Service;
        expect(dummyNg1Service).toBeDefined();
        expect(dummyNg1Service.getMessage()).toBe('Dummy NG1 Service');
        expect(dummyNg1Service.getInjectedMessage()).toBe('Dummy NG2 Service');
        done();
      });
  });
});
