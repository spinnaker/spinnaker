import type { Identifier, Literal } from 'estree';

export function getImportName(node: Identifier | Literal): string {
  if (node.type === 'Identifier') {
    return node.name;
  }
  if (node.type === 'Literal' && typeof node.value === 'string') {
    return node.value;
  }
  return '';
}
