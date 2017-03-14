import {NgModule, Type} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {UpgradeModule, downgradeInjectable, downgradeComponent} from '@angular/upgrade/static';
declare let angular: any;

import {SPINNAKER_DOWNGRADES, SPINNAKER_COMPONENT_DOWNGRADES} from './modules';

const providers: Type<any>[] = [];
export const DOWNGRADED_MODULE_NAMES: string[] = [];
SPINNAKER_DOWNGRADES.forEach((item) => {
  DOWNGRADED_MODULE_NAMES.push(item.moduleName);
  providers.push(item.moduleClass);
  angular.module(item.moduleName, []).factory(item.injectionName, downgradeInjectable(item.moduleClass));
});

const declarations: Type<any>[] = [];
export const DOWNGRADED_COMPONENT_MODULE_NAMES: string[] = [];
SPINNAKER_COMPONENT_DOWNGRADES.forEach((item) => {
  DOWNGRADED_COMPONENT_MODULE_NAMES.push(item.moduleName);
  declarations.push(item.moduleClass);

  // ng2 AoT requires we specify the inputs/outputs for downgraded components because the metadata is lost at runtime
  const component: any = {
    component: item.moduleClass
  };
  if (item.inputs) {
    component.inputs = item.inputs;
  }
  if (item.outputs) {
    component.outputs = item.outputs;
  }

  angular.module(item.moduleName, []).directive(item.injectionName, downgradeComponent(component) as ng.IDirectiveFactory);
});

@NgModule({
  imports: [
    BrowserModule,
    UpgradeModule
  ],
  declarations: [
    ...declarations
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
