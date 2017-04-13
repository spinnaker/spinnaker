import {NgModule, Type} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {UpgradeModule, downgradeInjectable} from '@angular/upgrade/static';
declare let angular: any;

import {SPINNAKER_DOWNGRADES, SPINNAKER_DIRECTIVE_UPGRADES} from './modules';

const providers: Type<any>[] = [];
export const DOWNGRADED_MODULE_NAMES: string[] = [];
SPINNAKER_DOWNGRADES.forEach((item) => {
  DOWNGRADED_MODULE_NAMES.push(item.moduleName);
  providers.push(item.moduleClass);
  angular.module(item.moduleName, []).factory(item.injectionName, downgradeInjectable(item.moduleClass));
});

const declarations: Type<any>[] = [];

@NgModule({
  imports: [
    BrowserModule,
    UpgradeModule
  ],
  declarations: [
    ...declarations,
    ...SPINNAKER_DIRECTIVE_UPGRADES
  ],
  entryComponents: [
    ...declarations
  ],
  providers: [
    ...providers
  ]
})
export class SpinnakerModule {

  constructor(private upgrade: UpgradeModule) {}

  public ngDoBootstrap() {
    this.upgrade.bootstrap(document.body, ['netflix.spinnaker']);
  }
}

// @NgModule({
//   imports: [
//     BrowserModule,
//     UpgradeModule
//     // , AuthenticationModule
//   ],
//   // declarations: [
//   //   ...declarations
//   // ],
//   // entryComponents: [
//   //   ...declarations
//   // ],
//   providers: [
//      AuthenticationService
//   ]
// })
// export class SpinnakerModuleNoBoostrap {
// }

// @NgModule({
//   providers: [
//     AuthenticationService
//   ]
// })
// export class AuthenticationModule {}
