export function ReactSelect(selector) {
  const context = {
    get: options => cy.get(`${selector} .Select`, options),
    toggleDropdown: options => cy.get(`${selector} .Select .Select-arrow`).click(options),
    type: (text, options) => cy.get(`${selector} .Select .Select-input input`).type(text, options),
    getOptions: options => cy.get(`${selector} .Select .Select-option`, options),
    select: (textOrIndex, options) => {
      return typeof textOrIndex === 'string'
        ? context
            .getOptions(options)
            .contains()
            .first()
            .click()
        : context
            .getOptions(options)
            .eq(textOrIndex)
            .click();
    },
  };
  return context;
}
