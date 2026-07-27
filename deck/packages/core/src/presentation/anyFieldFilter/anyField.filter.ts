/**
 * Matches an item when any configured field contains its search text.
 */

export function anyFieldFilter() {
  return function (items: any, props: any): any[] {
    let out: any[] = [];

    if (Array.isArray(items)) {
      items.forEach(function (item) {
        let itemMatches = false;

        const keys: any[] = Object.keys(props);
        for (const prop of keys) {
          const text: string = (props as any)[prop].toLowerCase();
          if (item[prop] && item[prop].toString().toLowerCase().includes(text)) {
            itemMatches = true;
            break;
          }
        }

        if (itemMatches) {
          out.push(item);
        }
      });
    } else {
      // Let the output be the input untouched
      out = items;
    }

    return out;
  };
}
