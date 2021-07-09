import { IComponentOptions, IController, IInterpolateService, ISCEService, module } from 'angular';
import DOMPurify from 'dompurify';
import { get, partition } from 'lodash';

interface IAnnotationsMap {
  [key: string]: string;
}

interface ICustomSection {
  title: string;
  key: string;
  isHtml: boolean;
  content: string;
}

interface ICustomSectionMap {
  [key: string]: ICustomSection[];
}

class KubernetesAnnotationCustomSections implements IController {
  private resource: any;
  public manifest: any;
  public customSections: ICustomSectionMap;

  public static $inject = ['$sce', '$interpolate'];
  constructor(private $sce: ISCEService, private $interpolate: IInterpolateService) {}

  public $onInit() {
    const annotations: IAnnotationsMap = get(this, ['manifest', 'metadata', 'annotations']);
    if (annotations == null) {
      return;
    }
    this.populateCustomSections(annotations);
  }

  private populateCustomSections(annotations: IAnnotationsMap) {
    const customSections: ICustomSectionMap = Object.keys(annotations).reduce(
      (memo: ICustomSectionMap, annotationKey: string) => {
        const entry = this.annotationToEntry(annotations[annotationKey], annotationKey);
        if (entry != null && entry.title) {
          memo[entry.title] = memo[entry.title] || [];
          memo[entry.title].push(entry);
        }
        return memo;
      },
      {},
    );
    // Sort section contents such that text entries appear before HTML entries.
    this.customSections = Object.keys(customSections).reduce((memo: ICustomSectionMap, sectionTitle: string) => {
      const entriesHtmlText = partition(customSections[sectionTitle], (section) => section.isHtml);
      memo[sectionTitle] = entriesHtmlText[1].concat(entriesHtmlText[0]);
      return memo;
    }, {});
  }

  private annotationToEntry(content: string, annotationKey: string): ICustomSection {
    const parsed = this.parseAnnotationKey(annotationKey);
    if (parsed == null) {
      return null;
    }
    if (this.resource && content.includes('{{')) {
      content = this.$interpolate(content)({ ...this.resource, manifest: this.manifest });
    }
    return {
      title: parsed.title.replace(/-/g, ' ').trim(),
      key: parsed.key.replace(/-/g, ' ').trim(),
      content: parsed.isHtml ? this.sanitizeContent(content) : content,
      isHtml: parsed.isHtml,
    };
  }

  private parseAnnotationKey(annotationKey: string): { title: string; key: string; isHtml: boolean } {
    const keyParts = /([^.]+)\.details\.(html\.)?spinnaker\.io(?:\/(.*))?/.exec(annotationKey);
    if (keyParts == null || keyParts.length !== 4) {
      return null;
    }
    return {
      title: keyParts[1] || '',
      key: keyParts[3] || '',
      isHtml: !!keyParts[2],
    };
  }

  private sanitizeContent(unsanitized: string): any {
    const sanitized = DOMPurify.sanitize(unsanitized, {
      ADD_ATTR: ['target'],
    });
    return this.$sce.trustAsHtml(sanitized);
  }
}

const kubernetesAnnotationCustomSectionsComponent: IComponentOptions = {
  bindings: { manifest: '<', resource: '<' },
  controller: ['$sce', '$interpolate', KubernetesAnnotationCustomSections],
  controllerAs: 'ctrl',
  template: `
    <collapsible-section expanded="true" ng-if="ctrl.manifest" ng-repeat="(section, entries) in ctrl.customSections" heading="{{ section }}">
      <div ng-repeat="entry in entries">
        <div ng-if="entry.isHtml" ng-bind-html="entry.content"></div>
        <div ng-if="!entry.isHtml">
          <span ng-if="entry.key" style="font-weight:bold">{{ entry.key }}</span>
          <span>{{ entry.content }}</span>
        </div>
      </div>
    </collapsible-section>
  `,
};

export const KUBERNETES_ANNOTATION_CUSTOM_SECTIONS = 'spinnaker.kubernetes.v2.manifest.annotation.custom.sections';
module(KUBERNETES_ANNOTATION_CUSTOM_SECTIONS, []).component(
  'kubernetesAnnotationCustomSections',
  kubernetesAnnotationCustomSectionsComponent,
);
