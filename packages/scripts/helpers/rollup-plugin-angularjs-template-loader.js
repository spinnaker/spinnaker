const fs = require('fs');
const path = require('path');
const MagicString = require('magic-string');

// This rollup plugin finds AngularJS templates and loads them into the $templateCache
// This is a rollup replacement for the ngtemplate-loader webpack loader
module.exports = function angularJsTemplateLoader(options = {}) {
  function isSourceMapEnabled() {
    return options.sourceMap !== false && options.sourcemap !== false;
  }

  return {
    transform(originalCode, id) {
      let code = originalCode;
      const templateRegex = /require\(['"]([^'"]+\.html)['"]\)/g;

      // look for things like require('./template.html')
      if (!code.includes("require('") || id.includes('node_modules')) {
        return;
      }

      if (!fs.existsSync(id)) {
        throw new Error('Unable to load AngularJS template; could not find source file ' + id);
      }

      // Find the directory the JS source file is in
      const baseDir = fs.lstatSync(id).isDirectory() ? id : path.dirname(id);
      const moduleRootDir = path.resolve('.');
      const moduleDirName = path.basename(moduleRootDir);

      let match = templateRegex.exec(code);
      if (!match) {
        return;
      }

      let magicString = new MagicString(code);

      while (match) {
        // i.e., './template.html'
        const templatePath = match[1];
        // Absolute path to the actual template.html file
        const contentPath = path.resolve(baseDir, templatePath);
        if (!fs.existsSync(contentPath)) {
          throw new Error('Unable to load AngularJS template; could not find template file ' + contentPath);
        }

        const templatePathFromModuleRoot = `${moduleDirName}${path.sep}${path.relative(moduleRootDir, contentPath)}`;

        const startIdx = match.index;
        const endIdx = startIdx + match[0].length;

        // read the contents of template.html
        const content = fs.readFileSync(contentPath, { encoding: 'UTF8' }).replace(/`/g, '\\`');

        // Replace: templateUrl: require('./template.html')
        // With:    templateUrl: 'path/from/module/root/to/template.html'
        magicString.overwrite(startIdx, endIdx, `'${templatePathFromModuleRoot}'`);

        // Append a run block that adds the HTML content into the $templateCache (used by angularjs when loading templates)
        magicString.append(`

/*********************************************************************
 * angularjs-template-loader rollup plugin -- ${templatePathFromModuleRoot} start  *
 ********************************************************************/

window.angular.module('ng').run(['$templateCache', function(templateCache) {
  templateCache.put('${templatePathFromModuleRoot}',
\`${content}\`)
}]);

/*********************************************************************
 * angularjs-template-loader rollup plugin -- ${templatePathFromModuleRoot} end    *
 ********************************************************************/
`);
        // look for another match
        match = templateRegex.exec(code);
      }

      if (isSourceMapEnabled()) {
        return { code: magicString.toString(), map: magicString.generateMap({ hires: true }) };
      } else {
        return { code: magicString.toString() };
      }
    },
  };
};
