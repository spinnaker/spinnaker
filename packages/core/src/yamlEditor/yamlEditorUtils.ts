import { dump, loadAll } from 'js-yaml';
import { head } from 'lodash';

export function yamlStringToDocuments(yamlString: string): any[] {
  try {
    const yamlDocuments = loadAll(yamlString, null);
    if (Array.isArray(head(yamlDocuments))) {
      // Multi-doc entered as list of maps
      return head(yamlDocuments);
    }
    return yamlDocuments;
  } catch (e) {
    return null;
  }
}

export function yamlDocumentsToString(yamlDocuments: any[]): string {
  try {
    return yamlDocuments.map((m) => dump(m)).join('---\n');
  } catch (e) {
    return '';
  }
}
