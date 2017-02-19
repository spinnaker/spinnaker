import {Type, PlatformRef} from '@angular/core';
import {UpgradeModule} from '@angular/upgrade/static';

export function html(html: string): Element {
  // Don't return `body` itself, because using it as a `$rootElement` for ng1
  // will attach `$injector` to it and that will affect subsequent tests.
  const body = document.body;
  body.innerHTML = `<div>${html.trim()}</div>`;
  const div = document.body.firstChild as Element;

  if (div.childNodes.length === 1 && div.firstChild instanceof HTMLElement) {
    return div.firstChild;
  }

  return div;
}

export function bootstrap(platform: PlatformRef, Ng2Module: Type<{}>, element: Element, modules: string[]) {
  // We bootstrap the Angular module first; then when it is ready (async)
  // We bootstrap the AngularJS module on the bootstrap element
  return platform.bootstrapModule(Ng2Module).then(ref => {
    const upgrade = ref.injector.get(UpgradeModule) as UpgradeModule;
    upgrade.bootstrap(element, modules);
    return upgrade;
  });
}
