export function replaceValue(str: string, regExStr?: string, replacement?: string): string {
  if (regExStr === undefined || replacement === undefined) {
    return str;
  }

  return str.replace(new RegExp(regExStr, 'g'), replacement);
}
