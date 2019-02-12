import { module } from 'angular';

import { has } from 'lodash';

import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

import './base64.artifact.less';

export const DEFAULT_BASE64_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.defaultBase64';

const DOMBase64Errors: { [key: string]: string } = {
  5: 'The string to encode contains characters outside the latin1 range.',
};

module(DEFAULT_BASE64_ARTIFACT, []).config(() => {
  Registry.pipeline.registerArtifactKind({
    label: 'Base64',
    type: 'embedded/base64',
    description: 'An artifact that includes its referenced resource as part of its payload.',
    key: 'default.base64',
    isDefault: true,
    isMatch: false,
    controller: function(artifact: IArtifact) {
      'ngInject';
      this.artifact = artifact;
      this.artifact.type = 'embedded/base64';
      this.decoded = '';
      this.encodeDecodeError = '';

      this.convert = (fn: (s: string) => string, str: string): string => {
        this.encodeDecodeError = '';
        try {
          return fn(str);
        } catch (e) {
          if (has(DOMBase64Errors, e.code)) {
            this.encodeDecodeError = DOMBase64Errors[e.code];
          } else {
            this.encodeDecodeError = e.message;
          }
          return '';
        }
      };

      this.onContentChange = () => {
        const encoded = this.convert(btoa, this.decoded);
        if (!this.encodeDecodeError) {
          this.artifact.reference = encoded;
        }
      };

      if (this.artifact.reference) {
        const decoded = this.convert(atob, this.artifact.reference);
        if (!this.encodeDecodeError) {
          this.decoded = decoded;
        }
      }
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Name
    </label>
    <div class="col-md-8">
      <input type="text"
             placeholder="base64-artifact"
             class="form-control input-sm"
             ng-model="ctrl.artifact.name" />
    </div>
  </div>
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Contents
    </label>
    <div class="col-md-8">
      <textarea autocapitalize="none"
                autocomplete="off"
                rows="16"
                class="form-control code"
                ng-model="ctrl.decoded"
                ng-change="ctrl.onContentChange()">
      </textarea>
      <copy-to-clipboard
        class="copy-base64-reference"
        text="ctrl.artifact.reference"
        tool-tip="'Copy base64-encoded content to clipboard'"
        analytics-label="'Copy Base64 Artifact Content'"
      ></copy-to-clipboard>
    </div>
  </div>
  <div ng-if="ctrl.encodeDecodeError" class="form-group row">
    <div class="col-md-12 error-message">
      Error encoding/decoding artifact content: {{ ctrl.encodeDecodeError }}
    </div>
  </div>
</div>
`,
  });
});
