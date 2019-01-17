const formXpath = 'form[contains(@name, "newApplicationForm")]';

export const NewApplicationModalLocators = Object.freeze({
  nameInput: `//${formXpath}//input[contains(@name, "name")]`,
  emailInput: `//${formXpath}//input[contains(@name, "email")]`,
  createButton: `//${formXpath}//button[contains(., "Create")]`,
});
