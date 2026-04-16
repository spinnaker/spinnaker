const { globalIgnores } = require("eslint/config");

const spinnakerEslintPlugin = require("@spinnaker/eslint-plugin");

module.exports = [
    ...spinnakerEslintPlugin.configs.base,
    globalIgnores([
        "**/*.spec.js",
        "**/node_modules",
        "packages/*/dist/**/*",
        "packages/*/lib/**/*",
    ]),
    {
        files: ["packages/scripts/**/*.js"],
        rules: {
            "no-console": "off",
        },
    },
];
