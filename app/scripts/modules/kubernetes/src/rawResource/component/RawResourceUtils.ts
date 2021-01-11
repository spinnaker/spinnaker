export class RawResourceUtils {
  static GLOBAL_LABEL = '(global)';

  static namespaceDisplayName(ns: string): string {
    if (ns === null || ns === '') {
      return RawResourceUtils.GLOBAL_LABEL;
    }
    return ns;
  }

  static resourceKey(resource: IApiKubernetesResource): string {
    if (resource === null) {
      return '';
    }
    return resource.namespace === ''
      ? resource.account + '-' + resource.kind + '-' + resource.name
      : resource.account + '-' + resource.namespace + '-' + resource.kind + '-' + resource.name;
  }
}
