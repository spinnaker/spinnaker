import { isObject } from 'lodash';

export interface IJsonListEntry {
  leaf: string;
  value: string | number;
}

export class JsonListBuilder {
  public static convertJsonKeysToBracketedList(json: any, ignoreList: string[] = []): IJsonListEntry[] {
    const stack: any[] = [];
    const array: IJsonListEntry[] = [];
    const parentList: any[] = [];

    stack.push(json);

    while (stack.length !== 0) {
      const node = stack.pop();
      const keys = Object.keys(node);
      const p = parentList.pop() || '';
      this.processKeys(keys, node, parentList, p, stack, array, ignoreList);
    }

    return array;
  }

  public static escapeForRegEx(item: string): string {
    // eslint-disable-next-line no-useless-escape
    return item ? item.replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, '\\$&') : null;
  }

  private static processKeys(
    keys: any[],
    node: any,
    parentList: any[],
    parent: any,
    stack: any[],
    array: IJsonListEntry[],
    ignoreList: string[],
  ): void {
    keys.forEach((key) => {
      const entry = isFinite(parseInt(key, 10)) ? `${parent}[${parseInt(key, 10)}]` : `${parent}['${key}']`;

      const value = node[key];
      if (!(isObject(value) || Array.isArray(value))) {
        if (
          !ignoreList.some((ignoreItem) => {
            const testerString = `['${ignoreItem}`;
            return entry.substr(0, testerString.length) === testerString;
          })
        ) {
          array.push({ leaf: entry, value: value });
        }
      }

      if (isObject(node[key])) {
        parentList.push(entry);
        stack.push(node[key]);
      }
    });
  }
}
