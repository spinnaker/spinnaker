import { module, IComponentOptions } from 'angular';

class KayentaStageConfigSection implements IComponentOptions {
  public transclude = true;
  public bindings = { title: '@' };
  public template = `
    <section>
      <h5>{{ $ctrl.title }}</h5>
      <div class="horizontal-rule"></div>
      <ng-transclude></ng-transclude>
    </section>
  `;
}

export const KAYENTA_STAGE_CONFIG_SECTION = 'spinnaker.kayenta.stageConfigSection';
module(KAYENTA_STAGE_CONFIG_SECTION, [])
  .component('kayentaStageConfigSection', new KayentaStageConfigSection());
