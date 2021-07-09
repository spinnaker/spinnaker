import { yamlStringToDocuments, yamlDocumentsToString } from './yamlEditorUtils';

describe('YAML editor utils', () => {
  describe('yamlStringToDocuments', () => {
    it('converts string to list of YAML documents', () => {
      stringToDocsTestCases.forEach(({ str, docs }) => {
        expect(yamlStringToDocuments(str)).toEqual(docs);
      });
    });
  });
  describe('yamlDocumentsToString', () => {
    it('converts list of YAML documents to string', () => {
      docsToStringTestCases.forEach(({ str, docs }) => {
        expect(yamlDocumentsToString(docs)).toEqual(str);
      });
    });
  });
});

const yamlDoc1 = {
  kind: 'ConfigMap',
  metadata: {
    name: 'my-config-map',
  },
};

const yamlDoc2 = {
  kind: 'Deployment',
  metadata: {
    name: 'my-deployment',
  },
};

const stringToDocsTestCases = [
  {
    str: `kind: ConfigMap
metadata:
  name: my-config-map
`,
    docs: [yamlDoc1],
  },
  {
    str: `kind: ConfigMap
metadata:
  name: my-config-map
---
kind: Deployment
metadata:
  name: my-deployment
`,
    docs: [yamlDoc1, yamlDoc2],
  },
  {
    str: `- kind: ConfigMap
  metadata:
    name: my-config-map
- kind: Deployment
  metadata:
    name: my-deployment
`,
    docs: [yamlDoc1, yamlDoc2],
  },
  {
    str: `kind: ConfigMap
metadata:
  name: -
`,
    docs: null,
  },
  {
    str: '',
    docs: [],
  },
];

const docsToStringTestCases = [
  {
    docs: [yamlDoc1],
    str: `kind: ConfigMap
metadata:
  name: my-config-map
`,
  },
  {
    docs: [yamlDoc1, yamlDoc2],
    str: `kind: ConfigMap
metadata:
  name: my-config-map
---
kind: Deployment
metadata:
  name: my-deployment
`,
  },
  {
    docs: [],
    str: '',
  },
  {
    docs: null,
    str: '',
  },
  {
    docs: undefined,
    str: '',
  },
];
