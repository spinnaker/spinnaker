"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var angular_1 = require("angular");
var KubernetesScaleManifestSettingsFormComponent = /** @class */ (function () {
    function KubernetesScaleManifestSettingsFormComponent() {
        this.bindings = { settings: '=' };
        this.controllerAs = 'ctrl';
        this.template = "\n    <div class=\"form-horizontal\">\n      <div class=\"form-group form-inline\">\n        <div class=\"col-md-3 sm-label-right\">\n          Replicas\n        </div>\n        <div class=\"col-md-4\">\n          <div class=\"input-group\">\n            <input type=\"number\"\n                  class=\"form-control input-sm highlight-pristine\"\n                  ng-model=\"ctrl.settings.replicas\"\n                  min=\"0\"/>\n            <span class=\"input-group-addon\">replica<span ng-if=\"ctrl.settings.replicas !== 1\">s</span></span>\n          </div>\n        </div>\n      </div>\n    </div>\n  ";
    }
    return KubernetesScaleManifestSettingsFormComponent;
}());
exports.KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM = 'spinnaker.kubernetes.v2.kubernetes.manifest.scale.settingsForm.component';
angular_1.module(exports.KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM, [])
    .component('kubernetesScaleManifestSettingsForm', new KubernetesScaleManifestSettingsFormComponent());
