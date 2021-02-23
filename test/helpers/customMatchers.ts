beforeEach(() => {
  jasmine.addMatchers({
    textMatch: () => ({
      compare: (node: JQuery, expected: string) => {
        let actual: string;
        if (expected === undefined) {
          expected = '';
        }
        if (node !== undefined) {
          actual = node.text().trim().replace(/\s+/g, " ");
        }
        const result: jasmine.CustomMatcherResult = { pass: expected === actual };
        if (result.pass) {
          result.message = `Expected ${expected}`;
        } else {
          result.message = `Expected ${expected} but was ${actual}`;
        }
        return result;
      },
    }),
  });
});
