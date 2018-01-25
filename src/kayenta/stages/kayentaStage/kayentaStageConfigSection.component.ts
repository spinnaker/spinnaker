import { module, IComponentOptions } from 'angular';

class KayentaStageConfigSection implements IComponentOptions {
  public transclude = {
    sectionHeader: '?sectionHeader',
  };
  public bindings = { title: '@' };
  public template = `
    <section>
      <ul class="list-inline">
        <li><h5>{{ $ctrl.title }}</h5></li>
        <li><span ng-transclude="sectionHeader"></span></li>
      </ul>
      <div class="horizontal-rule"></div>
      <ng-transclude></ng-transclude>
    </section>
  `;
}

export const KAYENTA_STAGE_CONFIG_SECTION = 'spinnaker.kayenta.stageConfigSection';
module(KAYENTA_STAGE_CONFIG_SECTION, [])
  .component('kayentaStageConfigSection', new KayentaStageConfigSection());
