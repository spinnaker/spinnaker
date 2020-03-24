export interface IParsedVersionComponents {
  packageName: string;
  version: string;
  buildNumber: string;
  commit: string;
}

// Lifted from Netflix/frigga
// https://github.com/Netflix/frigga/blob/master/src/main/java/com/netflix/frigga/ami/AppVersion.java
// https://github.com/Netflix/frigga/blob/master/src/main/java/com/netflix/frigga/NameConstants.java
export function parseName(amiName: string): IParsedVersionComponents {
  const NAME_CHARS = 'a-zA-Z0-9._';
  const EXTENDED_NAME_CHARS = NAME_CHARS + '~\\^';
  const NAME_HYPHEN_CHARS = '-' + EXTENDED_NAME_CHARS;
  const regex =
    '([' +
    NAME_HYPHEN_CHARS +
    ']+)-([0-9.a-zA-Z~]+)-(\\w+)(?:[.](\\w+))?(?:\\/([' +
    NAME_HYPHEN_CHARS +
    ']+)\\/([0-9]+))?';
  const match = amiName.match(regex);
  if (match) {
    const [, packageName, version, buildString, commit] = match;
    const buildNumber = buildString?.substring(1);
    return { packageName, version, buildNumber, commit };
  }
  return { packageName: null, version: null, buildNumber: null, commit: null };
}
