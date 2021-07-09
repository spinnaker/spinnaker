// The native atob() doesn't know how to decode all unicode chars because Strings are 16-bit.
// This escapes the unicode chars after atob() tries to process them, and re-decodes them.
export const decodeUnicodeBase64 = (encodedString: string) =>
  decodeURIComponent(
    atob(encodedString)
      .split('')
      .map((char) => '%' + ('00' + char.charCodeAt(0).toString(16)).slice(-2))
      .join(''),
  );
