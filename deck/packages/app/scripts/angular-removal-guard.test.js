const assert = require('node:assert/strict');
const {
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} = require('node:fs');
const { createRequire } = require('node:module');
const { tmpdir } = require('node:os');
const path = require('node:path');
const test = require('node:test');

const deckRoot = path.resolve(__dirname, '../../..');
const repositoryRoot = path.resolve(deckRoot, '..');
const coreSourceRoot = path.resolve(__dirname, '../../core/src');
const parseYaml = createRequire(path.join(deckRoot, 'packages/core/package.json'))('js-yaml').load;
const deckTypescript = createRequire(path.join(deckRoot, 'packages/app/package.json'))('typescript');
const deckKayentaRoot = path.join(repositoryRoot, 'deck-kayenta');
const deckKayentaSourceRoot = path.join(deckKayentaRoot, 'src');
const spinnakerGradleProjectRoot = path.join(repositoryRoot, 'spinnaker-gradle-project');
const functionalRoot = path.join(deckRoot, 'test/functional');
const deckWorkspacePath = path.join(deckRoot, 'pnpm-workspace.yaml');
const deckKayentaWorkspacePath = path.join(deckKayentaRoot, 'pnpm-workspace.yaml');
const functionalWorkspacePath = path.join(functionalRoot, 'pnpm-workspace.yaml');
const activeWorkspacePaths = [deckWorkspacePath, deckKayentaWorkspacePath, functionalWorkspacePath];
const deckLockPath = path.join(deckRoot, 'pnpm-lock.yaml');
const deckKayentaLockPath = path.join(deckKayentaRoot, 'pnpm-lock.yaml');
const functionalLockPath = path.join(functionalRoot, 'pnpm-lock.yaml');
const activeLockPaths = [deckLockPath, deckKayentaLockPath, functionalLockPath];
const coreManifestPath = path.join(deckRoot, 'packages/core/package.json');
const pluginsdkPeerdepsManifestPath = path.join(deckRoot, 'packages/pluginsdk-peerdeps/package.json');
const deckTestRoot = path.join(deckRoot, 'test');
const appScriptsRoot = path.join(deckRoot, 'packages/app/scripts');
const karmaFiles = [path.join(deckRoot, 'karma-shim.js'), path.join(deckRoot, 'karma.conf.js')];
const angularTemplateLoaderHelperPath = path.join(
  deckRoot,
  'packages/scripts/helpers',
  ['rollup-plugin-angularjs', 'template-loader.js'].join('-'),
);
const sharedRollupConfigPath = path.join(deckRoot, 'packages/scripts/config/rollup.config.base.js');
const activeBuildConfigFiles = [];
const legacyFacadeConsumerRoots = [
  path.join(deckRoot, 'packages'),
  path.join(deckRoot, 'test'),
  path.join(repositoryRoot, 'deck-kayenta/src'),
];
const angularServicesPath = path.join(coreSourceRoot, 'angular', 'services.ts');
const angularServicesSpecPath = path.join(coreSourceRoot, 'angular', 'services.spec.ts');
const bridgePath = path.join(coreSourceRoot, 'navigation/legacyStateConfig.bridge.ts');
const legacyImportPackage = ['ng', 'import'].join('');
const routeProvider = /['"](?:stateConfigProvider|applicationStateProvider)['"]/;
const angularServicesName = 'Angular' + 'Services';
const angularServiceAccessorsName = 'Angular' + 'ServiceAccessors';
const angularServicesImportPath = ['angular', 'services'].join('/');
const sourceExtensions = ['.js', '.jsx', '.ts', '.tsx', '.mjs', '.cjs', '.mts', '.cts'];
const angularRegistrationMethods = [
  'component',
  'config',
  'constant',
  'controller',
  'decorator',
  'directive',
  'factory',
  'filter',
  'provider',
  'run',
  'service',
  'value',
];
const angularRuntimePackages = [
  '@uirouter/angularjs',
  '@uirouter/react-hybrid',
  'angular',
  'angular-messages',
  'angular-sanitize',
  'angular-spinner',
  'angular-ui-bootstrap',
  'angulartics',
  'angulartics-google-analytics',
  'ui-select',
];
const forbiddenAngularPackages = [
  '@types/angular',
  '@types/angular-mocks',
  '@types/angular-ui-bootstrap',
  '@uirouter/angularjs',
  '@uirouter/react-hybrid',
  'angular',
  'angular-messages',
  'angular-mocks',
  'angular-sanitize',
  'angular-spinner',
  'angular-ui-bootstrap',
  'angular-ui-sortable',
  'angular2react',
  'angulartics',
  'angulartics-google-analytics',
  ['ng', 'component'].join(''),
  ['ng', 'import'].join(''),
  ['ng', 'template-loader'].join(''),
  'react2angular',
  'ui-select',
];
const unrelatedAngularPackageNames = new Set(['angular-like', '@types/angular-like']);
const forbiddenAngularBuildTools = [
  ['ng', 'template-loader'].join(''),
  ['rollup-plugin-angularjs', 'template-loader'].join('-'),
];
const angularRemovalGuardFixturePath = __filename;
const workspaceExtensions = new Set([...sourceExtensions, '.html', '.json']);
const buildConfigExtensions = new Set([...sourceExtensions, '', '.gradle', '.json', '.kts']);
const styleExtensions = new Set(['.css', '.less']);
const generatedAndDependencyDirectories = new Set([
  '.cache-loader',
  '.gradle',
  'build',
  'coverage',
  'dist',
  'node_modules',
]);
const forbiddenFacadePatterns = [angularServicesName, angularServiceAccessorsName, angularServicesImportPath];
const removedCompatibilityIdentifiers = new Set([
  'DirectProviderServiceDelegate',
  'PROVIDER_SERVICE_DELEGATE',
  'modalInstanceEmulation',
  'IModalServiceInstanceEmulation',
  'notifyAngular',
  'makeSortedStringFromAngularObject',
  'APPLICATION_INITIALIZERS_MODULE',
  'AUTHENTICATION_MODULE',
  'CORE_NOTIFICATION_NOTIFICATIONS_MODULE',
  'AMAZON_MODULE',
  'APPENGINE_MODULE',
  'AZURE_MODULE',
  'CLOUDRUN_MODULE',
  'DCOS_DCOS_MODULE',
  'DOCKER_MODULE',
  'ECS_MODULE',
  'GOOGLE_MODULE',
  'HUAWEICLOUD_MODULE',
  'ORACLE_MODULE',
  'TENCENTCLOUD_MODULE',
  'TENCENTCLOUD_REACT_MODULE',
  'TITUS_MODULE',
  'TITUS_REACT_MODULE',
]);
const forbiddenDollarIdentifiers = new Set(['$q', '$timeout', '$log', '$injector']);

function productionSourceFiles(directory) {
  return workspaceSourceFiles(directory).filter(
    (file) => sourceExtensions.includes(path.extname(file)) && !/\.(?:spec|test)\.[^.]+$/.test(file),
  );
}

function angularConfigCallbacks(source, file) {
  return angularGraphDetails(source, file).registrations.has('config') ? ['Angular .config registration'] : [];
}

function workspaceFiles(directory, extensions) {
  if (lstatSync(directory).isSymbolicLink()) {
    throw new Error(`Refusing to scan symbolic link: ${directory}`);
  }

  return readdirSync(directory, { withFileTypes: true })
    .sort((left, right) => left.name.localeCompare(right.name))
    .flatMap((entry) => {
      const entryPath = path.join(directory, entry.name);
      if (entry.isSymbolicLink()) {
        throw new Error(`Refusing to scan symbolic link: ${entryPath}`);
      }
      if (entry.isDirectory()) {
        if (generatedAndDependencyDirectories.has(entry.name)) {
          return [];
        }
        return workspaceFiles(entryPath, extensions);
      }
      return !extensions || extensions.has(path.extname(entry.name)) ? [entryPath] : [];
    });
}

function workspaceSourceFiles(directory) {
  return workspaceFiles(directory, workspaceExtensions);
}

function packageSourceRoots() {
  return readdirSync(path.join(deckRoot, 'packages'), { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => {
      const packageRoot = path.join(deckRoot, 'packages', entry.name);
      return entry.name === 'eslint-plugin' ? packageRoot : path.join(packageRoot, 'src');
    })
    .filter((sourceRoot) => existsSync(sourceRoot));
}

function productionSourceRoots() {
  return [...packageSourceRoots(), deckKayentaSourceRoot];
}

function isWithin(file, directory) {
  const relativePath = path.relative(directory, file);
  return relativePath === '' || (!relativePath.startsWith(`..${path.sep}`) && relativePath !== '..');
}

function discoverActiveBuildConfigFiles(
  roots = [deckRoot, deckKayentaRoot, functionalRoot, spinnakerGradleProjectRoot],
) {
  const pluginConfigRoot = path.join(deckRoot, 'packages/pluginsdk/pluginconfig');
  const scaffoldRoot = path.join(deckRoot, 'packages/pluginsdk/scaffold');
  const nonconventionalRoots = [
    path.join(deckRoot, 'scripts'),
    path.join(deckRoot, 'packages/scripts/config'),
    path.join(deckRoot, 'packages/scripts/helpers'),
    pluginConfigRoot,
    path.join(deckKayentaRoot, 'build_scripts'),
  ];
  const toolConfigName = /^(?:rollup|webpack|vite|karma|jest|babel|postcss|cypress)(?:[.-]|[A-Z])/;
  const files = roots.flatMap((root) => workspaceFiles(root));

  return Array.from(
    new Set(
      files.filter((file) => {
        const basename = path.basename(file);
        const configExtension = path.extname(file);
        const eligibleConfigFile = sourceExtensions.includes(configExtension) || configExtension === '.json';
        const configFamily =
          basename.startsWith('.babelrc') ||
          /^(?:.+\.)?\.?eslintrc(?:\..+)?$/.test(basename) ||
          basename === 'jsconfig.json' ||
          /^tsconfig(?:[.-].*)?\.json$/.test(basename) ||
          /^eslint\.config\./.test(basename) ||
          /^lint\..*config\./.test(basename) ||
          /^.+\.config\.(?:js|jsx|ts|tsx|mjs|cjs|mts|cts|json)$/.test(basename) ||
          (eligibleConfigFile && file.split(path.sep).includes('.storybook'));
        const scaffoldRelativePath = path.relative(scaffoldRoot, file);
        const scaffoldConfig =
          eligibleConfigFile &&
          !scaffoldRelativePath.startsWith('..') &&
          !scaffoldRelativePath.includes(path.sep) &&
          file !== scaffoldRoot;
        return (
          file.endsWith('.gradle') ||
          file.endsWith('.gradle.kts') ||
          configFamily ||
          toolConfigName.test(basename) ||
          (eligibleConfigFile && nonconventionalRoots.some((root) => isWithin(file, root))) ||
          scaffoldConfig
        );
      }),
    ),
  ).sort();
}

activeBuildConfigFiles.push(...discoverActiveBuildConfigFiles());

function discoverActiveAppArtifactFiles(appRoot, fixtureHost) {
  const indexPaths = ['index.deck', 'index.html'].map((file) => path.join(appRoot, file)).filter(existsSync);
  const scriptsRoot = path.join(appRoot, 'scripts');
  const scriptFiles = existsSync(scriptsRoot)
    ? workspaceSourceFiles(scriptsRoot).filter((file) => sourceExtensions.includes(path.extname(file)))
    : [];

  return [...indexPaths, ...scriptFiles].filter((file) => file !== fixtureHost).sort();
}

function activeSourceFiles() {
  return Array.from(
    new Set(
      [path.join(deckRoot, 'packages'), path.join(deckRoot, 'scripts'), deckKayentaRoot, functionalRoot]
        .flatMap((sourceRoot) => workspaceSourceFiles(sourceRoot))
        .filter((file) => sourceExtensions.includes(path.extname(file)))
        .filter((file) => file !== angularRemovalGuardFixturePath)
        .concat(
          discoverActiveAppArtifactFiles(path.join(deckRoot, 'packages/app'), angularRemovalGuardFixturePath),
          activeBuildConfigFiles.filter((file) => sourceExtensions.includes(path.extname(file))),
        ),
    ),
  ).sort();
}

function activeStyleFiles(roots = productionSourceRoots()) {
  return roots.flatMap((root) => workspaceFiles(root, styleExtensions)).sort();
}

function activePolicyFiles() {
  return Array.from(new Set([...activeSourceFiles(), ...activeStyleFiles(), ...activeBuildConfigFiles])).sort();
}

function activeSpecFiles() {
  return activeSourceFiles().filter((file) => /\.(?:spec|test)\.[^.]+$/.test(file));
}

function activeManifestFiles() {
  const packagesRoot = path.join(deckRoot, 'packages');
  return [
    path.join(deckRoot, 'package.json'),
    ...workspaceFiles(packagesRoot, new Set(['.json'])).filter((file) => path.basename(file) === 'package.json'),
    path.join(deckTestRoot, 'functional/package.json'),
    path.join(deckKayentaRoot, 'package.json'),
  ].sort();
}

function createSyntaxTree(file, source = readFileSync(file, 'utf8')) {
  return deckTypescript.createSourceFile(file, source, deckTypescript.ScriptTarget.Latest, true);
}

function visitSyntaxTree(node, visitor) {
  visitor(node);
  deckTypescript.forEachChild(node, (child) => visitSyntaxTree(child, visitor));
}

function stringLiteralValue(node) {
  if (!node) return null;
  return deckTypescript.isStringLiteral(node) || deckTypescript.isNoSubstitutionTemplateLiteral(node)
    ? node.text
    : null;
}

function propertyName(node) {
  if (!node) return null;
  if (
    deckTypescript.isIdentifier(node) ||
    deckTypescript.isStringLiteral(node) ||
    deckTypescript.isNoSubstitutionTemplateLiteral(node)
  ) {
    return node.text;
  }
  if (deckTypescript.isComputedPropertyName(node)) return stringLiteralValue(node.expression);
  return null;
}

function memberAccessName(node) {
  if (deckTypescript.isPropertyAccessExpression(node)) return node.name.text;
  if (deckTypescript.isElementAccessExpression(node)) return stringLiteralValue(node.argumentExpression);
  return null;
}

function moduleReferences(source, file = 'source.ts') {
  const references = [];
  const sourceFile = createSyntaxTree(file, source);

  visitSyntaxTree(sourceFile, (node) => {
    if (deckTypescript.isImportDeclaration(node) || deckTypescript.isExportDeclaration(node)) {
      const specifier = stringLiteralValue(node.moduleSpecifier);
      if (specifier !== null) {
        references.push({ specifier, typeOnly: Boolean(node.importClause?.isTypeOnly || node.isTypeOnly) });
      }
      return;
    }
    if (
      deckTypescript.isImportEqualsDeclaration(node) &&
      deckTypescript.isExternalModuleReference(node.moduleReference)
    ) {
      const specifier = stringLiteralValue(node.moduleReference.expression);
      if (specifier !== null) references.push({ specifier, typeOnly: Boolean(node.isTypeOnly) });
      return;
    }
    if (deckTypescript.isImportTypeNode(node)) {
      const argument = deckTypescript.isLiteralTypeNode(node.argument) ? node.argument.literal : node.argument;
      const specifier = stringLiteralValue(argument);
      if (specifier !== null) references.push({ specifier, typeOnly: true });
      return;
    }
    if (deckTypescript.isModuleDeclaration(node)) {
      const specifier = stringLiteralValue(node.name);
      if (specifier !== null) references.push({ specifier, typeOnly: true });
      return;
    }
    if (!deckTypescript.isCallExpression(node) || node.arguments.length !== 1) return;
    const isDynamicImport = node.expression.kind === deckTypescript.SyntaxKind.ImportKeyword;
    const isRequire = deckTypescript.isIdentifier(node.expression) && node.expression.text === 'require';
    const isRequireResolve =
      (deckTypescript.isPropertyAccessExpression(node.expression) ||
        deckTypescript.isElementAccessExpression(node.expression)) &&
      deckTypescript.isIdentifier(node.expression.expression) &&
      node.expression.expression.text === 'require' &&
      memberAccessName(node.expression) === 'resolve';
    if (!isDynamicImport && !isRequire && !isRequireResolve) return;
    const specifier = stringLiteralValue(node.arguments[0]);
    if (specifier !== null) references.push({ specifier, typeOnly: false });
  });

  return references;
}

function identifierNames(sourceFile) {
  const names = new Set();
  visitSyntaxTree(sourceFile, (node) => {
    if (deckTypescript.isIdentifier(node)) names.add(node.text);
  });
  return names;
}

function findLegacyFacadeReferences(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const names = identifierNames(createSyntaxTree(file, source));
    const specifiers = moduleReferences(source, file).map(({ specifier }) => specifier);
    const relativePath = path.relative(displayRoot, file);
    return forbiddenFacadePatterns
      .filter((pattern) =>
        pattern === angularServicesImportPath
          ? specifiers.some((specifier) => specifier === pattern || specifier.endsWith(`/${pattern}`))
          : names.has(pattern),
      )
      .map((pattern) => `${relativePath}: ${pattern}`);
  });
}

function angularGraphDetails(source, file) {
  const sourceFile = createSyntaxTree(file, source);
  const runtimeReferences = moduleReferences(source, file).filter(
    ({ specifier, typeOnly }) =>
      !typeOnly &&
      angularRuntimePackages.some(
        (packageName) => specifier === packageName || specifier.startsWith(`${packageName}/`),
      ),
  );
  const callableModuleFactories = new Set();
  const namespaceModuleFactories = new Set(['angular']);
  const angularModuleInstances = new Set();
  const registrations = new Set();
  const adapters = new Set();
  let moduleFactory = false;

  const isAngularModuleFactoryCall = (node) => {
    if (!node || !deckTypescript.isCallExpression(node)) return false;
    if (deckTypescript.isIdentifier(node.expression)) return callableModuleFactories.has(node.expression.text);
    if (
      !deckTypescript.isPropertyAccessExpression(node.expression) &&
      !deckTypescript.isElementAccessExpression(node.expression)
    ) {
      return false;
    }
    const receiver = node.expression.expression.getText(sourceFile);
    return (
      memberAccessName(node.expression) === 'module' &&
      (namespaceModuleFactories.has(receiver) || receiver === 'window.angular')
    );
  };

  visitSyntaxTree(sourceFile, (node) => {
    if (deckTypescript.isImportDeclaration(node)) {
      const specifier = stringLiteralValue(node.moduleSpecifier);
      if (
        specifier &&
        angularRuntimePackages.some(
          (packageName) => specifier === packageName || specifier.startsWith(`${packageName}/`),
        ) &&
        !node.importClause?.isTypeOnly
      ) {
        if (node.importClause?.name) namespaceModuleFactories.add(node.importClause.name.text);
        const bindings = node.importClause?.namedBindings;
        if (bindings && deckTypescript.isNamespaceImport(bindings)) namespaceModuleFactories.add(bindings.name.text);
        if (bindings && deckTypescript.isNamedImports(bindings)) {
          bindings.elements.forEach((element) => {
            if ((element.propertyName || element.name).text === 'module')
              callableModuleFactories.add(element.name.text);
          });
        }
      }
    }
    if (
      deckTypescript.isVariableDeclaration(node) &&
      deckTypescript.isIdentifier(node.name) &&
      node.initializer &&
      deckTypescript.isCallExpression(node.initializer) &&
      deckTypescript.isIdentifier(node.initializer.expression) &&
      node.initializer.expression.text === 'require' &&
      node.initializer.arguments.length === 1 &&
      stringLiteralValue(node.initializer.arguments[0]) === 'angular'
    ) {
      namespaceModuleFactories.add(node.name.text);
    }
    if (
      deckTypescript.isVariableDeclaration(node) &&
      deckTypescript.isIdentifier(node.name) &&
      isAngularModuleFactoryCall(node.initializer)
    ) {
      angularModuleInstances.add(node.name.text);
    }
    if (
      deckTypescript.isIdentifier(node) &&
      ['angularComponentFromReact', 'react2angular', 'angular2react'].includes(node.text)
    ) {
      adapters.add(node.text);
    }
    if (!deckTypescript.isCallExpression(node)) return;
    if (isAngularModuleFactoryCall(node)) moduleFactory = true;
    if (
      deckTypescript.isPropertyAccessExpression(node.expression) ||
      deckTypescript.isElementAccessExpression(node.expression)
    ) {
      const receiver = node.expression.expression.getText(sourceFile);
      const method = memberAccessName(node.expression);
      if (
        angularRegistrationMethods.includes(method) &&
        (angularModuleInstances.has(receiver) || isAngularModuleFactoryCall(node.expression.expression))
      ) {
        registrations.add(method);
      }
    }
  });

  return { adapters, moduleFactory, registrations, runtimeReferences };
}

function findProductionAngularGraph(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const relativePath = path.relative(displayRoot, file);
    const details = angularGraphDetails(source, file);
    const findings = [];

    if (details.runtimeReferences.length) findings.push(`${relativePath}: Angular runtime import`);
    if (details.moduleFactory) findings.push(`${relativePath}: Angular module factory`);
    angularRegistrationMethods.forEach((method) => {
      if (details.registrations.has(method)) findings.push(`${relativePath}: Angular .${method} registration`);
    });
    ['angularComponentFromReact', 'react2angular', 'angular2react'].forEach((adapter) => {
      if (details.adapters.has(adapter)) findings.push(`${relativePath}: ${adapter}`);
    });

    return findings;
  });
}

function findForbiddenAngularSourceReferences(files, displayRoot) {
  return files.flatMap((file) => {
    const specifiers = moduleReferences(readFileSync(file, 'utf8'), file).map(({ specifier }) => specifier);
    const relativePath = path.relative(displayRoot, file);
    return forbiddenPackageNamesFromReferences(specifiers).map((packageName) => `${relativePath}: ${packageName}`);
  });
}

function findProductionHtmlModuleReferences(files, displayRoot) {
  return files.flatMap((file) => {
    const relativePath = path.relative(displayRoot, file);
    return moduleReferences(readFileSync(file, 'utf8'), file)
      .map(({ specifier }) => specifier)
      .filter((specifier) => /\.html(?:[?#].*)?$/i.test(specifier))
      .map((specifier) => `${relativePath}: ${specifier}`);
  });
}

function findForbiddenAngularApiReferences(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const sourceFile = createSyntaxTree(file, source);
    const relativePath = path.relative(displayRoot, file);
    const labels = new Set();

    visitSyntaxTree(sourceFile, (node) => {
      if (
        deckTypescript.isQualifiedName(node) &&
        deckTypescript.isIdentifier(node.left) &&
        node.left.text === 'ng' &&
        /^I[A-Z]/.test(node.right.text)
      ) {
        labels.add('ng.I type');
      }
      if (!deckTypescript.isPropertyAccessExpression(node)) return;
      const receiver = node.expression.getText(sourceFile);
      const member = node.name.text;
      if (receiver === 'angular' && member === 'mock') labels.add(['angular', 'mock'].join('.'));
      if (receiver === 'mock' && member === 'module') labels.add(['mock', 'module'].join('.'));
      if (receiver === 'mock' && member === 'inject') labels.add(['mock', 'inject'].join('.'));
      if (receiver === 'ng' && /^I[A-Z]/.test(member)) labels.add('ng.I type');
      if (member === '$digest') labels.add(['$', 'digest'].join(''));
    });

    const findings = Array.from(labels, (label) => `${relativePath}: ${label}`);
    if (angularGraphDetails(source, file).moduleFactory) findings.push(`${relativePath}: Angular module factory`);
    return findings;
  });
}

function findAngularDiMetadata(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const sourceFile = createSyntaxTree(file, source);
    const relativePath = path.relative(displayRoot, file);
    const labels = new Set();

    visitSyntaxTree(sourceFile, (node) => {
      const declaredPropertyName = deckTypescript.isPropertyDeclaration(node) ? propertyName(node.name) : null;
      if (
        deckTypescript.isPropertyDeclaration(node) &&
        declaredPropertyName === '$inject' &&
        node.modifiers?.some((modifier) => modifier.kind === deckTypescript.SyntaxKind.StaticKeyword)
      ) {
        labels.add('static $inject');
      }
      if (declaredPropertyName === 'ngInject') labels.add('ngInject metadata');
      if (
        deckTypescript.isBinaryExpression(node) &&
        node.operatorToken.kind === deckTypescript.SyntaxKind.EqualsToken &&
        memberAccessName(node.left) === '$inject'
      ) {
        labels.add('$inject assignment');
      }
      if (
        deckTypescript.isBinaryExpression(node) &&
        node.operatorToken.kind === deckTypescript.SyntaxKind.EqualsToken &&
        memberAccessName(node.left) === 'ngInject'
      ) {
        labels.add('ngInject assignment');
      }
      if (deckTypescript.isPropertyAssignment(node) && propertyName(node.name) === 'ngInject') {
        labels.add('ngInject metadata');
      }
      if (deckTypescript.isExpressionStatement(node) && stringLiteralValue(node.expression) === 'ngInject') {
        labels.add("'ngInject' marker");
      }
    });

    return Array.from(labels, (label) => `${relativePath}: ${label}`);
  });
}

function findForbiddenAngularTestIdentifiers(files, displayRoot) {
  const identifiers = [
    ['$', 'q'].join(''),
    ['$', 'scope'].join(''),
    ['$', 'rootScope'].join(''),
    ['$', 'apply'].join(''),
    ['$', 'applyAsync'].join(''),
    ['$', 'digest'].join(''),
  ];
  const angularNoop = ['angular', 'noop'].join('.');

  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const sourceFile = createSyntaxTree(file, source);
    const names = identifierNames(sourceFile);
    const relativePath = path.relative(displayRoot, file);
    const findings = identifiers
      .filter((identifier) => names.has(identifier))
      .map((identifier) => `${relativePath}: ${identifier}`);
    let usesAngularNoop = false;
    visitSyntaxTree(sourceFile, (node) => {
      if (
        deckTypescript.isPropertyAccessExpression(node) &&
        node.expression.getText(sourceFile) === 'angular' &&
        node.name.text === 'noop'
      ) {
        usesAngularNoop = true;
      }
    });
    if (usesAngularNoop) findings.push(`${relativePath}: ${angularNoop}`);
    return findings;
  });
}

function collectAstStringValues(node, aliases, values, resolving = new Set()) {
  const value = stringLiteralValue(node);
  if (value !== null) {
    values.push(value);
    return;
  }
  if (deckTypescript.isIdentifier(node) && aliases.has(node.text) && !resolving.has(node.text)) {
    const nextResolving = new Set(resolving).add(node.text);
    collectAstStringValues(aliases.get(node.text), aliases, values, nextResolving);
    return;
  }
  deckTypescript.forEachChild(node, (child) => collectAstStringValues(child, aliases, values, resolving));
}

function findForbiddenAngularBuildToolReferences(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const sourceFile = createSyntaxTree(file, source);
    const relativePath = path.relative(displayRoot, file);
    const contextualValues = moduleReferences(source, file).map(({ specifier }) => specifier);
    const aliases = new Map();
    visitSyntaxTree(sourceFile, (node) => {
      if (
        deckTypescript.isVariableDeclaration(node) &&
        deckTypescript.isIdentifier(node.name) &&
        deckTypescript.isVariableDeclarationList(node.parent) &&
        (node.parent.flags & (deckTypescript.NodeFlags.Const | deckTypescript.NodeFlags.Let)) !== 0
      ) {
        if (node.initializer) aliases.set(node.name.text, node.initializer);
      }
    });
    visitSyntaxTree(sourceFile, (node) => {
      if (
        deckTypescript.isShorthandPropertyAssignment(node) &&
        ['loader', 'use'].includes(node.name.text) &&
        aliases.has(node.name.text)
      ) {
        collectAstStringValues(node.name, aliases, contextualValues);
        return;
      }
      if (!deckTypescript.isPropertyAssignment(node) || !['loader', 'use'].includes(propertyName(node.name))) return;
      collectAstStringValues(node.initializer, aliases, contextualValues);
    });
    if (!sourceExtensions.includes(path.extname(file))) {
      for (const match of source.matchAll(
        /\/\*[\s\S]*?\*\/|\/\/[^\r\n]*|'''[\s\S]*?'''|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`/g,
      )) {
        const value = match[0];
        if (value.startsWith('//') || value.startsWith('/*')) continue;
        contextualValues.push(
          value.startsWith("'''") || value.startsWith('"""') ? value.slice(3, -3).trim() : value.slice(1, -1),
        );
      }
    }
    return forbiddenAngularBuildTools
      .filter((tool) => {
        const escapedTool = tool.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const packageReference = new RegExp(`(?:^|/)${escapedTool}(?=$|[/?#:]|\\.(?:c|m)?js(?:[?#]|$))`);
        return contextualValues.some((value) => packageReference.test(value));
      })
      .map((tool) => `${relativePath}: ${tool}`);
  });
}

const configBearingKeys = new Set([
  'alias',
  'aliases',
  'dependencies',
  'devDependencies',
  'extends',
  'loader',
  'loaders',
  'moduleNameMapper',
  'optionalDependencies',
  'paths',
  'peerDependencies',
  'plugin',
  'plugins',
  'preset',
  'presets',
  'typeRoots',
  'types',
  'use',
]);
const nonExecutableConfigKeys = new Set(['comment', 'comments', 'description', 'documentation', 'example', 'examples']);

function forbiddenReferenceNames(values) {
  const packageNames = forbiddenPackageNamesFromReferences(values);
  const buildTools = forbiddenAngularBuildTools.filter((candidate) => {
    const escapedCandidate = candidate.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const packageReference = new RegExp(`(?:^|/)${escapedCandidate}(?=$|[/?#:@]|\\.(?:c|m)?js(?:[?#]|$))`);
    return values.some((value) => typeof value === 'string' && packageReference.test(value));
  });
  return Array.from(new Set([...packageNames, ...buildTools]));
}

function collectConfigDataValues(value, active, values) {
  if (typeof value === 'string') {
    if (active) values.add(value);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectConfigDataValues(item, active, values));
    return;
  }
  if (!value || typeof value !== 'object') return;
  Object.entries(value).forEach(([key, child]) => {
    if (nonExecutableConfigKeys.has(key)) return;
    const childActive = active || configBearingKeys.has(key);
    if (childActive) values.add(key);
    collectConfigDataValues(child, childActive, values);
  });
}

function isInNonExecutableConfigContext(node) {
  for (let current = node.parent; current; current = current.parent) {
    if (deckTypescript.isPropertyAssignment(current) && nonExecutableConfigKeys.has(propertyName(current.name))) {
      return true;
    }
  }
  return false;
}

function collectConfigAstValues(node, values) {
  const value = stringLiteralValue(node);
  if (value !== null) {
    values.add(value);
    return;
  }
  if (deckTypescript.isPropertyAssignment(node)) {
    const name = propertyName(node.name);
    if (name) values.add(name);
    collectConfigAstValues(node.initializer, values);
    return;
  }
  if (deckTypescript.isShorthandPropertyAssignment(node)) {
    values.add(node.name.text);
    return;
  }
  if (deckTypescript.isSpreadAssignment(node)) {
    collectConfigAstValues(node.expression, values);
    return;
  }
  deckTypescript.forEachChild(node, (child) => collectConfigAstValues(child, values));
}

function findForbiddenConfigObjectReferences(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const values = new Set();
    if (path.extname(file) === '.json' || path.basename(file).startsWith('.babelrc')) {
      const config = deckTypescript.parseConfigFileTextToJson(file, source).config || {};
      collectConfigDataValues(config, false, values);
    } else if (sourceExtensions.includes(path.extname(file))) {
      const sourceFile = createSyntaxTree(file, source);
      visitSyntaxTree(sourceFile, (node) => {
        if (
          deckTypescript.isPropertyAssignment(node) &&
          configBearingKeys.has(propertyName(node.name)) &&
          !isInNonExecutableConfigContext(node)
        ) {
          collectConfigAstValues(node.initializer, values);
        }
      });
    }
    return forbiddenReferenceNames([...values]).map((reference) => `${path.relative(displayRoot, file)}: ${reference}`);
  });
}

function packageNameFromOverrideSelector(selector) {
  const target = selector.split('>').at(-1).trim();
  const versionSeparator = target.indexOf('@', target.startsWith('@') ? 1 : 0);
  return versionSeparator === -1 ? target : target.slice(0, versionSeparator);
}

function packageNameFromNpmAlias(reference) {
  if (typeof reference !== 'string' || !reference.startsWith('npm:')) return null;

  const target = reference.slice('npm:'.length);
  const packageSeparator = target.lastIndexOf('@');
  const packageNameEnd = target.startsWith('@') ? target.indexOf('/') : 0;
  return packageSeparator > packageNameEnd ? target.slice(0, packageSeparator) : target;
}

function isForbiddenAngularPackage(packageName) {
  if (typeof packageName !== 'string' || unrelatedAngularPackageNames.has(packageName)) return false;

  return (
    forbiddenAngularPackages.includes(packageName) ||
    /^@angular\/[^/]+$/.test(packageName) ||
    /^@types\/angular(?:$|-)/.test(packageName) ||
    packageName === 'angular' ||
    packageName.startsWith('angular-') ||
    packageName.startsWith('angulartics')
  );
}

function forbiddenPackageNamesFromReferences(references) {
  const found = new Set(references.flatMap(forbiddenPackageNamesFromPnpmReference));
  return [
    ...forbiddenAngularPackages.filter((packageName) => found.has(packageName)),
    ...Array.from(found)
      .filter((packageName) => !forbiddenAngularPackages.includes(packageName))
      .sort(),
  ];
}

function forbiddenDependencyNames(dependencies, overrideSelectors = false) {
  return Array.from(
    new Set(
      Object.entries(dependencies || {})
        .flatMap(([packageName, reference]) => [
          overrideSelectors ? packageNameFromOverrideSelector(packageName) : packageName,
          packageNameFromNpmAlias(reference),
        ])
        .filter(isForbiddenAngularPackage),
    ),
  ).sort();
}

function findForbiddenAngularManifestMetadata(files, displayRoot) {
  const dependencySections = ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies'];

  return [...files].sort().flatMap((file) => {
    const manifest = JSON.parse(readFileSync(file, 'utf8'));
    const relativePath = path.relative(displayRoot, file);
    const sections = dependencySections.map((section) => [section, manifest[section]]);
    sections.push(['pnpm.overrides', manifest.pnpm?.overrides]);

    return sections.flatMap(([location, dependencies]) =>
      forbiddenDependencyNames(dependencies, location === 'pnpm.overrides').map(
        (packageName) => `${relativePath} ${location}: ${packageName}`,
      ),
    );
  });
}

function workspaceOverrides(file) {
  const metadata = parseYaml(readFileSync(file, 'utf8')) || {};
  return metadata.overrides && typeof metadata.overrides === 'object' ? metadata.overrides : {};
}

function findForbiddenAngularWorkspaceOverrides(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const relativePath = path.relative(displayRoot, file);
    return forbiddenDependencyNames(workspaceOverrides(file), true).map(
      (packageName) => `${relativePath} overrides: ${packageName}`,
    );
  });
}

function packageNameFromPnpmLocatorSegment(segment) {
  let locator = segment.trim().replace(/^\/+/, '');
  if (locator.startsWith('npm:')) locator = locator.slice('npm:'.length);
  if (!locator || /\s/.test(locator)) return null;

  if (locator.startsWith('@')) {
    if (!locator.includes('/') && locator.includes('+')) locator = locator.replace('+', '/');
    const scopeSeparator = locator.indexOf('/');
    if (scopeSeparator === -1) return locator;
    const versionSeparators = [
      locator.indexOf('@', scopeSeparator + 1),
      locator.indexOf('/', scopeSeparator + 1),
    ].filter((index) => index !== -1);
    const versionSeparator = versionSeparators.length ? Math.min(...versionSeparators) : locator.length;
    return locator.slice(0, versionSeparator);
  }

  const versionSeparators = [locator.indexOf('@'), locator.indexOf('/')].filter((index) => index !== -1);
  const versionSeparator = versionSeparators.length ? Math.min(...versionSeparators) : locator.length;
  return locator.slice(0, versionSeparator);
}

function forbiddenPackageNamesFromPnpmReference(reference) {
  if (typeof reference !== 'string') return [];

  return Array.from(
    new Set(reference.split(/[()]/).map(packageNameFromPnpmLocatorSegment).filter(isForbiddenAngularPackage)),
  ).sort();
}

function findForbiddenAngularLockReferences(lockData) {
  const dependencyCollections = new Set([
    'dependencies',
    'devDependencies',
    'optionalDependencies',
    'peerDependencies',
    'transitivePeerDependencies',
  ]);
  const dependencyReferenceFields = new Set(['specifier', 'version']);
  const findings = [];

  function addFindings(reference, location, referenceKind) {
    forbiddenPackageNamesFromPnpmReference(reference).forEach((packageName) => {
      findings.push(`${location.join(' > ')} [${referenceKind}]: ${packageName}`);
    });
  }

  function visitDependencyCollection(node, location) {
    if (Array.isArray(node)) {
      node.forEach((value, index) => {
        const valueLocation = [...location, String(index)];
        if (typeof value === 'string') addFindings(value, valueLocation, 'value');
      });
      return;
    }
    if (!node || typeof node !== 'object') return;

    Object.keys(node)
      .sort()
      .forEach((key) => {
        const value = node[key];
        const valueLocation = [...location, key];
        addFindings(key, valueLocation, 'key');
        if (typeof value === 'string') {
          addFindings(value, valueLocation, 'value');
          return;
        }
        if (!value || typeof value !== 'object') return;
        dependencyReferenceFields.forEach((field) => {
          if (typeof value[field] === 'string') addFindings(value[field], [...valueLocation, field], 'value');
        });
        visitGraph(value, valueLocation);
      });
  }

  function visitGraph(node, location) {
    if (Array.isArray(node)) {
      node.forEach((value, index) => visitGraph(value, [...location, String(index)]));
      return;
    }
    if (!node || typeof node !== 'object') return;
    Object.keys(node)
      .sort()
      .forEach((key) => {
        const value = node[key];
        const valueLocation = [...location, key];
        if (dependencyCollections.has(key)) {
          visitDependencyCollection(value, valueLocation);
        } else {
          visitGraph(value, valueLocation);
        }
      });
  }

  function visitPackageIndex(section) {
    const packages = lockData?.[section];
    if (!packages || typeof packages !== 'object') return;
    Object.keys(packages)
      .sort()
      .forEach((locator) => {
        const location = [section, locator];
        addFindings(locator, location, 'key');
        visitGraph(packages[locator], location);
      });
  }

  if (lockData?.importers) visitGraph(lockData.importers, ['importers']);
  if (lockData?.overrides && typeof lockData.overrides === 'object') {
    Object.keys(lockData.overrides)
      .sort()
      .forEach((selector) => {
        const location = ['overrides', selector];
        const packageName = packageNameFromOverrideSelector(selector);
        if (isForbiddenAngularPackage(packageName)) {
          findings.push(`${location.join(' > ')} [key]: ${packageName}`);
        }
        addFindings(lockData.overrides[selector], location, 'value');
      });
  }
  ['packages', 'snapshots'].forEach((section) => {
    visitPackageIndex(section);
  });
  return findings.sort();
}

function findForbiddenAngularLockfileReferences(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const lockData = parseYaml(readFileSync(file, 'utf8')) || {};
    const relativePath = path.relative(displayRoot, file);
    return findForbiddenAngularLockReferences(lockData).map((finding) => `${relativePath}: ${finding}`);
  });
}

function findActiveLexicalResidue(files, displayRoot) {
  // Active comments and ordinary strings are policy violations; exclusions belong in the inventory, not allowlists.
  return [...files]
    .sort()
    .flatMap((file) =>
      /\bangular(?:js)?\b/i.test(readFileSync(file, 'utf8'))
        ? [`${path.relative(displayRoot, file)}: forbidden framework word`]
        : [],
    );
}

function findForbiddenAppShellContracts(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const source = readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    const styleSource = Array.from(source.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style\s*>/gi), (match) => match[1]).join(
      '\n',
    );
    const markup = source.replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, '');
    const tags = Array.from(markup.matchAll(/<[A-Za-z][^>]*>/g), (match) => match[0]);
    const relativePath = path.relative(displayRoot, file);
    const findings = [];
    if (tags.some((tag) => /\s(?:(?:data-|x-)?ng-app|ng:app)(?=\s|=|\/?>)/i.test(tag))) {
      findings.push(`${relativePath}: ng-app attribute`);
    }
    if (tags.some((tag) => /\s(?:(?:data-|x-)?ng-strict-di|ng:strict-di)(?=\s|=|\/?>)/i.test(tag))) {
      findings.push(`${relativePath}: ng-strict-di attribute`);
    }
    if (tags.some((tag) => /^<spinnaker(?=\s|>)/i.test(tag))) {
      findings.push(`${relativePath}: legacy <spinnaker> root`);
    }
    forbiddenStyleContractLabels(file, styleSource).forEach((label) => findings.push(`${relativePath}: ${label}`));
    return findings;
  });
}

function styleSelectors(source) {
  const selectors = [];
  const withoutComments = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  for (const match of withoutComments.matchAll(/([^{}]+)\{/g)) {
    const header = match[1].trim().split(';').at(-1).trim();
    if (header && !header.startsWith('@')) selectors.push(header);
  }
  return selectors.join('\n');
}

function forbiddenStyleContractLabels(file, source) {
  const selectors = styleSelectors(source);
  const labels = [];
  if (/\.directive\.less$/i.test(file)) labels.push('.directive.less path');
  if (
    /\.ng-(?:(?:valid|invalid)(?:-[\w-]+)?|pristine|dirty|touched|untouched|scope|binding)\b/.test(selectors) ||
    /\[\s*(?:data-)?ng-(?:model|valid|invalid|pristine|dirty|touched|untouched|scope|binding)\b/.test(selectors)
  ) {
    labels.push('ng-* selector');
  }
  if (/\.ui-select(?:-[\w-]+)?\b|\[\s*ui-select\b/.test(selectors)) {
    labels.push('UI Select selector');
  }
  if (/\.uib-[\w-]+\b|\[\s*uib-[\w-]+\b/.test(selectors)) {
    labels.push('UIB selector');
  }
  if (/\.select2(?:-[\w-]+)?\b|\[\s*select2\b/.test(selectors)) {
    labels.push('Select2 selector');
  }
  if (/(?:^|[\n,])\s*(?:&\s+)?(?:[a-z][a-z0-9]*-)+[a-z][a-z0-9-]*(?=$|\s|[.#:>+~]|\[|\])/.test(selectors)) {
    labels.push('directive custom-element selector');
  }
  return labels;
}

function findForbiddenStyleContracts(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const relativePath = path.relative(displayRoot, file);
    return forbiddenStyleContractLabels(file, readFileSync(file, 'utf8')).map((label) => `${relativePath}: ${label}`);
  });
}

function isPromiseServiceReceiver(node, sourceFile) {
  if (deckTypescript.isCallExpression(node)) {
    return deckTypescript.isIdentifier(node.expression) && node.expression.text === 'createNativePromiseService';
  }
  return /(?:^|\.)(?:nativePromiseService|promiseService)$/.test(node.getText(sourceFile));
}

function findRemovedCompatibilityApis(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const sourceFile = createSyntaxTree(file);
    const relativePath = path.relative(displayRoot, file);
    const labels = new Set();

    visitSyntaxTree(sourceFile, (node) => {
      if (deckTypescript.isIdentifier(node)) {
        if (removedCompatibilityIdentifiers.has(node.text) || forbiddenDollarIdentifiers.has(node.text)) {
          labels.add(node.text);
        }
      }
      if (
        deckTypescript.isCallExpression(node) &&
        deckTypescript.isPropertyAccessExpression(node.expression) &&
        ['when', 'notify', 'defer'].includes(node.expression.name.text) &&
        isPromiseServiceReceiver(node.expression.expression, sourceFile)
      ) {
        labels.add(`PromiseService.${node.expression.name.text}`);
      }
    });

    return Array.from(labels, (label) => `${relativePath}: ${label}`);
  });
}

function isRegistryConfigObject(node) {
  if (!deckTypescript.isObjectLiteralExpression(node)) return false;
  const names = new Set(node.properties.map((property) => propertyName(property.name)).filter(Boolean));
  const stageShape =
    names.has('key') &&
    [
      'accountExtractor',
      'cloudProvider',
      'component',
      'configAccountExtractor',
      'executionLabelComponent',
      'provides',
      'validators',
    ].some((name) => names.has(name));
  const stateShape =
    names.has('name') &&
    names.has('url') &&
    ['abstract', 'component', 'parent', 'redirectTo', 'views'].some((name) => names.has(name));
  return stageShape || stateShape;
}

function unwrapExpression(node) {
  let value = node;
  while (
    value &&
    (deckTypescript.isAsExpression(value) ||
      deckTypescript.isParenthesizedExpression(value) ||
      deckTypescript.isSatisfiesExpression(value) ||
      deckTypescript.isTypeAssertionExpression(value))
  ) {
    value = value.expression;
  }
  return value;
}

function isKnownRegistrationCall(node, sourceFile) {
  if (
    !deckTypescript.isCallExpression(node) ||
    (!deckTypescript.isPropertyAccessExpression(node.expression) &&
      !deckTypescript.isElementAccessExpression(node.expression))
  ) {
    return false;
  }
  const method = memberAccessName(node.expression);
  const receiver = node.expression.expression.getText(sourceFile);
  if (method === 'registerStage') return receiver === 'Registry.pipeline';
  if (method === 'registerProvider') return receiver === 'CloudProviderRegistry';
  if (method === 'register') return /(?:^|\.)stateRegistry$/.test(receiver);
  return (
    ['addState', 'addToRootState', 'addToApplicationState'].includes(method) &&
    /(?:^|\.)(?:stateConfigProvider|applicationStateProvider)$/.test(receiver)
  );
}

function resolveObjectLiteral(node, aliases) {
  const value = unwrapExpression(node);
  if (!value) return null;
  if (deckTypescript.isObjectLiteralExpression(value)) return value;
  return deckTypescript.isIdentifier(value) ? aliases.get(value.text) || null : null;
}

function collectObsoleteRegistryMetadata(node, aliases, labels, visited = new Set()) {
  if (!node || visited.has(node)) return;
  visited.add(node);
  node.properties.forEach((property) => {
    if (deckTypescript.isSpreadAssignment(property)) {
      collectObsoleteRegistryMetadata(resolveObjectLiteral(property.expression, aliases), aliases, labels, visited);
      return;
    }
    const name = propertyName(property.name);
    if (['templateUrl', 'controller', 'controllerAs', 'executionDetailsUrl'].includes(name)) labels.add(name);
  });
}

function findObsoleteRegistryMetadata(files, displayRoot) {
  return [...files].sort().flatMap((file) => {
    const sourceFile = createSyntaxTree(file);
    const relativePath = path.relative(displayRoot, file);
    const labels = new Set();
    const aliases = new Map();

    visitSyntaxTree(sourceFile, (node) => {
      if (
        deckTypescript.isVariableDeclaration(node) &&
        deckTypescript.isIdentifier(node.name) &&
        deckTypescript.isVariableDeclarationList(node.parent) &&
        (node.parent.flags & deckTypescript.NodeFlags.Const) !== 0
      ) {
        const object = resolveObjectLiteral(node.initializer, aliases);
        if (object) aliases.set(node.name.text, object);
      }
    });
    visitSyntaxTree(sourceFile, (node) => {
      if (isRegistryConfigObject(node)) collectObsoleteRegistryMetadata(node, aliases, labels);
      if (!isKnownRegistrationCall(node, sourceFile)) return;
      node.arguments.forEach((argument) => {
        collectObsoleteRegistryMetadata(resolveObjectLiteral(argument, aliases), aliases, labels);
      });
    });

    return Array.from(labels, (label) => `${relativePath}: ${label}`);
  });
}

function lifecycleValueDestination(node, sourceFile) {
  let value = node;
  while (
    deckTypescript.isParenthesizedExpression(value.parent) ||
    deckTypescript.isAsExpression(value.parent) ||
    deckTypescript.isSatisfiesExpression(value.parent) ||
    deckTypescript.isTypeAssertionExpression(value.parent) ||
    deckTypescript.isNonNullExpression(value.parent)
  ) {
    value = value.parent;
  }

  const parent = value.parent;
  if (deckTypescript.isVariableDeclaration(parent) && parent.initializer === value) {
    return { owned: true, target: parent.name.getText(sourceFile) };
  }
  if (deckTypescript.isBinaryExpression(parent)) {
    return parent.operatorToken.kind === deckTypescript.SyntaxKind.EqualsToken && parent.right === value
      ? { owned: true, target: parent.left.getText(sourceFile) }
      : { owned: false, target: null };
  }
  if (deckTypescript.isReturnStatement(parent) && parent.expression === value) return { owned: true, target: null };
  if (deckTypescript.isArrowFunction(parent) && parent.body === value) return { owned: true, target: null };
  return { owned: false, target: null };
}

function findOnNextRefreshUsage(files, displayRoot) {
  const callFiles = new Set();
  const findings = [];

  [...files].sort().forEach((file) => {
    const sourceFile = createSyntaxTree(file);
    const relativePath = path.relative(displayRoot, file);
    const invokedTargets = new Set();
    visitSyntaxTree(sourceFile, (node) => {
      if (
        deckTypescript.isCallExpression(node) &&
        (deckTypescript.isIdentifier(node.expression) ||
          deckTypescript.isPropertyAccessExpression(node.expression) ||
          deckTypescript.isElementAccessExpression(node.expression))
      ) {
        invokedTargets.add(node.expression.getText(sourceFile));
      }
    });
    visitSyntaxTree(sourceFile, (node) => {
      if (
        !deckTypescript.isCallExpression(node) ||
        !deckTypescript.isPropertyAccessExpression(node.expression) ||
        node.expression.name.text !== 'onNextRefresh'
      ) {
        return;
      }
      callFiles.add(relativePath);
      const destination = lifecycleValueDestination(node, sourceFile);
      if (!destination.owned) findings.push(`${relativePath}: unowned onNextRefresh call`);
      if (destination.target && !invokedTargets.has(destination.target)) {
        findings.push(`${relativePath}: onNextRefresh disposer ${destination.target} is never invoked`);
      }
    });
  });

  return { callFiles: [...callFiles], findings };
}

test('Core routes do not depend on the legacy Angular state config bridge', () => {
  assert.throws(() => readFileSync(bridgePath, 'utf8'), { code: 'ENOENT' });

  const bridgeReferences = productionSourceFiles(coreSourceRoot).filter((file) =>
    readFileSync(file, 'utf8').includes('legacyStateConfig.bridge'),
  );
  assert.deepEqual(bridgeReferences, []);
});

test('production Core source has no Angular route config callbacks', () => {
  const routeConfigs = productionSourceFiles(coreSourceRoot).flatMap((file) =>
    angularConfigCallbacks(readFileSync(file, 'utf8'), file)
      .filter((callback) => routeProvider.test(callback))
      .map(() => path.relative(coreSourceRoot, file)),
  );

  assert.deepEqual(routeConfigs, []);
});

test('route config callback detector handles bracket registration calls contextually', () => {
  const angularPackage = ['ang', 'ular'].join('');
  const bracketConfig = `import angular from '${angularPackage}';\nconst runtime = angular.module('fixture', []);\nruntime['config'](['service', function configure(service) {}]);`;

  assert.equal(angularConfigCallbacks(bracketConfig, 'bracket.ts').length, 1);
  assert.deepEqual(angularConfigCallbacks(`client.config(['region']);\nclient.filter(['active']);`, 'neutral.ts'), []);
});

test('production Deck and Deck-Kayenta source has no Angular runtime graph', () => {
  const productionFiles = productionSourceRoots().flatMap((root) => productionSourceFiles(root));
  assert.deepEqual(findProductionAngularGraph(productionFiles, repositoryRoot), []);
});

test('Core package root does not load the legacy Angular aggregate', () => {
  const coreIndex = readFileSync(path.join(coreSourceRoot, 'index.ts'), 'utf8');

  assert.doesNotMatch(coreIndex, /core\.module/);
});

test('Core has no Angular-backed PromiseLike compatibility declaration', () => {
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'types/promise.d.ts'), 'utf8'), { code: 'ENOENT' });

  const coreTypesIndex = readFileSync(path.join(coreSourceRoot, 'types/index.d.ts'), 'utf8');
  assert.doesNotMatch(coreTypesIndex, /promise\.d\.ts/);
});

test('production Deck and Deck-Kayenta roots contain no legacy HTML template files', () => {
  const htmlFiles = productionSourceRoots()
    .flatMap((root) => workspaceSourceFiles(root))
    .filter((file) => path.extname(file) === '.html')
    .map((file) => path.relative(repositoryRoot, file));

  assert.deepEqual(htmlFiles, []);
});

test('production Deck and Deck-Kayenta source has no HTML module references', () => {
  const productionFiles = productionSourceRoots().flatMap((root) => productionSourceFiles(root));

  assert.ok(productionFiles.includes(path.join(deckKayentaSourceRoot, 'initializeKayenta.ts')));
  assert.deepEqual(findProductionHtmlModuleReferences(productionFiles, repositoryRoot), []);
});

test('legacy Angular root and React adapter infrastructure is absent', () => {
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'angular/angularComponentFromReact.tsx'), 'utf8'), {
    code: 'ENOENT',
  });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'angular/angularComponentFromReact.spec.tsx'), 'utf8'), {
    code: 'ENOENT',
  });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'bootstrap/spinnakerContainer.component.ts'), 'utf8'), {
    code: 'ENOENT',
  });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'core.module.ts'), 'utf8'), { code: 'ENOENT' });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'utils/failedToInstantiateModule.ts'), 'utf8'), {
    code: 'ENOENT',
  });
});

test('direct bootstrap explicitly loads Core routes', () => {
  const bootstrapSource = readFileSync(path.join(coreSourceRoot, 'bootstrap/bootstrapDeck.tsx'), 'utf8');

  assert.match(bootstrapSource, /import ['"]\.\.\/navigation\/coreRoutes['"];?/);
});

test('the legacy Angular service facade implementation is absent', () => {
  assert.throws(() => readFileSync(angularServicesPath, 'utf8'), { code: 'ENOENT' });
});

test('the legacy Angular service facade spec is absent', () => {
  assert.throws(() => readFileSync(angularServicesSpecPath, 'utf8'), { code: 'ENOENT' });
});

test('workspace source and dependency metadata do not reference the legacy Angular import bridge', () => {
  const references = findForbiddenAngularSourceReferences(activeSourceFiles(), deckRoot).filter((finding) =>
    finding.endsWith(`: ${legacyImportPackage}`),
  );

  assert.deepEqual(references, []);
});

test('Deck and Deck-Kayenta source and tests do not use legacy Angular service facades', () => {
  const references = findLegacyFacadeReferences(
    legacyFacadeConsumerRoots.flatMap((root) => workspaceSourceFiles(root)),
    repositoryRoot,
  );

  assert.deepEqual(references, []);
});

function withFixtureRoot(assertions) {
  const fixtureRoot = mkdtempSync(path.join(tmpdir(), 'angular-removal-guard-'));
  try {
    assertions(fixtureRoot);
  } finally {
    rmSync(fixtureRoot, { recursive: true, force: true });
  }
}

test('PR7 detector finds forbidden Angular source imports and requires', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularAnimate = ['angular', 'animate'].join('-');
    const angularCore = ['@angular', 'core'].join('/');
    const packageNames = [
      ['@types', 'angular'].join('/'),
      ['@uirouter', 'angularjs'].join('/'),
      ['@uirouter', 'react-hybrid'].join('/'),
      ['ang', 'ular'].join(''),
      ['angular', 'messages'].join('-'),
      ['angular', 'mocks'].join('-'),
      ['angular', 'sanitize'].join('-'),
      ['angular', 'spinner'].join('-'),
      ['angular', 'ui-bootstrap'].join('-'),
      ['angular', 'ui-sortable'].join('-'),
      ['angular', 'tics'].join(''),
      ['angulartics', 'google-analytics'].join('-'),
      ['ui', 'select'].join('-'),
    ];
    const fixtures = new Map([
      ['00-import-type.ts', `import type { IQService } from '${packageNames[3]}';`],
      ['01-side-effect.ts', `import '${packageNames[5]}';`],
      ['02-require-subpath.cjs', `module.exports = require('${packageNames[0]}/index');`],
      ['03-import-subpath.ts', `import router from '${packageNames[1]}/lib';`],
      ...packageNames
        .filter((_, index) => ![0, 1, 3, 5].includes(index))
        .map((packageName, index) => [
          `${String(index + 4).padStart(2, '0')}-package.ts`,
          `import dependency from '${packageName}';`,
        ]),
      ['97-family.ts', `import '${angularAnimate}';`],
      ['98-scoped-family.ts', `const core = require('${angularCore}');`],
      [
        '99-neutral.ts',
        [
          "import React from 'react';",
          "import changelog from 'conventional-changelog-angular';",
          "import similar from 'angular-like';",
          "import suffixed from 'foo-angular';",
          "const loader = require('html-loader');",
        ].join('\n'),
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findForbiddenAngularSourceReferences(productionSourceFiles(fixtureRoot), fixtureRoot), [
      `00-import-type.ts: ${packageNames[3]}`,
      `01-side-effect.ts: ${packageNames[5]}`,
      `02-require-subpath.cjs: ${packageNames[0]}`,
      `03-import-subpath.ts: ${packageNames[1]}`,
      ...packageNames
        .filter((_, index) => ![0, 1, 3, 5].includes(index))
        .map((packageName, index) => `${String(index + 4).padStart(2, '0')}-package.ts: ${packageName}`),
      `97-family.ts: ${angularAnimate}`,
      `98-scoped-family.ts: ${angularCore}`,
    ]);
  });
});

test('PR7 detector finds Angular test and runtime APIs without package imports', () => {
  withFixtureRoot((fixtureRoot) => {
    const apiNames = {
      angularMock: ['angular', 'mock'].join('.'),
      digest: ['$', 'digest'].join(''),
      mockInject: ['mock', 'inject'].join('.'),
      mockModule: ['mock', 'module'].join('.'),
    };
    const fixtures = new Map([
      ['00-angular-mock.ts', ['angular', 'mock', 'reset();'].join('.')],
      ['01-mock-module.ts', [['mock', 'module'].join('.'), "('fixture');"].join('')],
      ['02-mock-inject.ts', [['mock', 'inject'].join('.'), '(() => undefined);'].join('')],
      ['03-ambient-type.ts', ['let service: ng', 'IHttpService;'].join('.')],
      ['04-angular-module.ts', [['angular', 'module'].join('.'), "('fixture', []);"].join('')],
      ['05-window-module.ts', [['window', 'angular', 'module'].join('.'), "('fixture', []);"].join('')],
      ['06-digest.ts', ['scope', ['$', 'digest'].join(''), '();'].join('.')],
      ['99-neutral.ts', 'mockModule();\nconst label: ng.Info = angular.modules;'],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findForbiddenAngularApiReferences(productionSourceFiles(fixtureRoot), fixtureRoot), [
      `00-angular-mock.ts: ${apiNames.angularMock}`,
      `01-mock-module.ts: ${apiNames.mockModule}`,
      `02-mock-inject.ts: ${apiNames.mockInject}`,
      '03-ambient-type.ts: ng.I type',
      '04-angular-module.ts: Angular module factory',
      '05-window-module.ts: Angular module factory',
      `06-digest.ts: ${apiNames.digest}`,
    ]);
  });
});

test('PR7 detector finds Angular DI metadata', () => {
  withFixtureRoot((fixtureRoot) => {
    const injectProperty = ['$', 'inject'].join('');
    const injectMarker = ['ng', 'Inject'].join('');
    const fixtures = new Map([
      ['00-static.ts', `class Controller { static ${injectProperty} = ['service']; }`],
      ['01-assignment.ts', `Controller.${injectProperty} = ['service'];`],
      ['02-bracket-assignment.ts', `Controller['${injectProperty}'] = ['service'];`],
      ['03-marker.ts', `function controller() { '${injectMarker}'; }`],
      ['04-ng-assignment.ts', `Controller.${injectMarker} = true;`],
      ['05-ng-bracket-assignment.ts', `Controller['${injectMarker}'] = true;`],
      ['06-ng-metadata.ts', `const metadata = { ${injectMarker}: true };`],
      ['07-client-config.ts', `client.config(['region']);`],
      ['08-client-filter.ts', `client['filter'](['active']);`],
      [
        '99-neutral.ts',
        `const ${injectProperty}able = true;\nconst marker = '${injectMarker}ed';\nController['${injectProperty}able'] = [];\nconst metadata = { ${injectMarker}ed: true };`,
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findAngularDiMetadata(productionSourceFiles(fixtureRoot), fixtureRoot), [
      '00-static.ts: static $inject',
      '01-assignment.ts: $inject assignment',
      '02-bracket-assignment.ts: $inject assignment',
      "03-marker.ts: 'ngInject' marker",
      '04-ng-assignment.ts: ngInject assignment',
      '05-ng-bracket-assignment.ts: ngInject assignment',
      '06-ng-metadata.ts: ngInject metadata',
    ]);
  });
});

test('PR7 detector finds Angular-specific identifiers in tests', () => {
  withFixtureRoot((fixtureRoot) => {
    const identifiers = {
      apply: ['$', 'apply'].join(''),
      applyAsync: ['$', 'applyAsync'].join(''),
      digest: ['$', 'digest'].join(''),
      noop: ['angular', 'noop'].join('.'),
      promise: ['$', 'q'].join(''),
      rootScope: ['$', 'rootScope'].join(''),
      scope: ['$', 'scope'].join(''),
    };
    const fixtures = new Map([
      ['00-promise.spec.ts', `const ${identifiers.promise} = createPromiseService();`],
      ['01-scope.spec.ts', `const ${identifiers.scope} = createScope();`],
      ['02-apply.spec.ts', `scope.${identifiers.apply}();`],
      ['03-apply-async.spec.ts', `scope.${identifiers.applyAsync}();`],
      ['04-digest.spec.ts', `scope.${identifiers.digest}();`],
      ['05-noop.spec.ts', `const callback = ${identifiers.noop};`],
      ['06-root-scope.spec.ts', `const ${identifiers.rootScope} = createRootScope();`],
      [
        '99-neutral.spec.ts',
        'const $queue = [];\nconst $scopeId = 1;\nconst $rootScopeId = 2;\nconst $application = {};\nconst angularNoopener = true;',
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findForbiddenAngularTestIdentifiers(workspaceSourceFiles(fixtureRoot), fixtureRoot), [
      `00-promise.spec.ts: ${identifiers.promise}`,
      `01-scope.spec.ts: ${identifiers.scope}`,
      `02-apply.spec.ts: ${identifiers.apply}`,
      `03-apply-async.spec.ts: ${identifiers.applyAsync}`,
      `04-digest.spec.ts: ${identifiers.digest}`,
      `05-noop.spec.ts: ${identifiers.noop}`,
      `06-root-scope.spec.ts: ${identifiers.rootScope}`,
    ]);
  });
});

test('PR7 detector finds only Angular-specific build tooling', () => {
  withFixtureRoot((fixtureRoot) => {
    const buildTools = [['ng', 'template-loader'].join(''), ['rollup-plugin-angularjs', 'template-loader'].join('-')];
    const fixtures = new Map([
      [
        'alias-loader.config.js',
        `const legacyLoader = '${buildTools[0]}?relativeTo=/src';\nmodule.exports = { module: { rules: [{ loader: legacyLoader }] } };`,
      ],
      [
        'alias-use.config.js',
        `let legacyHelper = './helpers/${buildTools[1]}.js?raw';\nmodule.exports = { module: { rules: [{ use: ['style-loader', legacyHelper] }] } };`,
      ],
      [
        'alias-shorthand.config.js',
        `const loader = '${buildTools[0]}?relativeTo=/src';\nmodule.exports = { module: { rules: [{ loader }] } };`,
      ],
      [
        'alias-array.config.js',
        `const baseLoaders = ['style-loader', '${buildTools[0]}?relativeTo=/src'];\nconst loaders = baseLoaders;\nmodule.exports = { module: { rules: [{ use: loaders }] } };`,
      ],
      ['build.gradle', `apply plugin: '${buildTools[0]}'`],
      ['build.gradle.kts', `apply(from = "./helpers/${buildTools[1]}.js?raw")`],
      ['resolve.config.js', `module.exports = { loader: require.resolve('${buildTools[0]}?raw') };`],
      ['triple.gradle', `def loader = '''\n${buildTools[0]}?raw\n'''`],
      ['triple.gradle.kts', `val loader = """\n${buildTools[1]}?raw\n""".trimIndent()`],
      [
        'neutral.gradle',
        [
          `// apply plugin: '${buildTools[0]}'`,
          `def documentation = 'Use ${buildTools[0]} only in archived builds'`,
          `def integrity = 'sha512-${buildTools[0]}'`,
          `def nearby = '${buildTools[0]}-fork'`,
          `def helperDocumentation = '${buildTools[1]} was removed'`,
          `def tripleNearby = '''${buildTools[0]}-fork'''`,
          `def tripleDocumentation = """Use ${buildTools[1]} only in archived builds"""`,
        ].join('\n'),
      ],
      ['rollup.config.js', `const loader = require('./helpers/${buildTools[1]}');`],
      ['scaffold.gradle', `apply from: './helpers/${buildTools[1]}.js?raw'`],
      [
        'webpack.config.js',
        `module.exports = { module: { rules: [{ loader: '${buildTools[0]}?relativeTo=/src' }] } };`,
      ],
      [
        'neutral.config.js',
        [
          `const loaderDocumentation = '${buildTools[0]}?relativeTo=/src';`,
          `const helperDocumentation = './helpers/${buildTools[1]}.js?raw';`,
          `const documentationLoaders = ['${buildTools[0]}?raw'];`,
          'const documentationAlias = documentationLoaders;',
          "module.exports = { documentation: [loaderDocumentation, helperDocumentation], tools: ['html-loader', 'karma', 'webpack', 'vite', 'rollup'] };",
        ].join('\n'),
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(
      findForbiddenAngularBuildToolReferences(workspaceFiles(fixtureRoot, buildConfigExtensions), fixtureRoot),
      [
        `alias-array.config.js: ${buildTools[0]}`,
        `alias-loader.config.js: ${buildTools[0]}`,
        `alias-shorthand.config.js: ${buildTools[0]}`,
        `alias-use.config.js: ${buildTools[1]}`,
        `build.gradle: ${buildTools[0]}`,
        `build.gradle.kts: ${buildTools[1]}`,
        `resolve.config.js: ${buildTools[0]}`,
        `rollup.config.js: ${buildTools[1]}`,
        `scaffold.gradle: ${buildTools[1]}`,
        `triple.gradle: ${buildTools[0]}`,
        `triple.gradle.kts: ${buildTools[1]}`,
        `webpack.config.js: ${buildTools[0]}`,
      ],
    );
  });
});

test('config object detector recursively checks package and tool-bearing keys', () => {
  withFixtureRoot((fixtureRoot) => {
    const packageNames = {
      angular: ['ang', 'ular'].join(''),
      router: ['@uirouter', 'angularjs'].join('/'),
      types: ['@types', 'angular'].join('/'),
    };
    const buildTools = [['ng', 'template-loader'].join(''), ['rollup-plugin-angularjs', 'template-loader'].join('-')];
    const jsonPath = path.join(fixtureRoot, '00-config.json');
    writeFileSync(
      jsonPath,
      JSON.stringify({
        compilerOptions: {
          paths: { [packageNames.angular]: ['angular-like'] },
          types: [packageNames.types],
        },
        plugins: [{ name: `${buildTools[0]}?raw` }],
        nested: { dependencies: { legacy: `${packageNames.router}/lib` } },
        documentation: packageNames.angular,
      }),
    );
    const jsPath = path.join(fixtureRoot, '01-config.js');
    writeFileSync(
      jsPath,
      [
        'module.exports = {',
        `  resolve: { alias: { legacy: '${packageNames.angular}/messages' } },`,
        `  module: { rules: [{ loader: './helpers/${buildTools[1]}.js?raw' }] },`,
        `  documentation: { plugin: '${packageNames.angular}' },`,
        '};',
      ].join('\n'),
    );
    const babelPath = path.join(fixtureRoot, '.babelrc.custom');
    writeFileSync(babelPath, JSON.stringify({ plugins: [buildTools[0]] }));
    const neutralPath = path.join(fixtureRoot, '99-neutral.json');
    writeFileSync(
      neutralPath,
      JSON.stringify({
        compilerOptions: { paths: { 'angular-like': ['@types/angular-like'] }, types: ['angular-like'] },
        plugins: ['ngtemplate-loader-fork'],
        documentation: [packageNames.angular, buildTools[0]],
      }),
    );

    assert.deepEqual(findForbiddenConfigObjectReferences([jsonPath, jsPath, babelPath, neutralPath], fixtureRoot), [
      `.babelrc.custom: ${buildTools[0]}`,
      `00-config.json: ${packageNames.types}`,
      `00-config.json: ${packageNames.router}`,
      `00-config.json: ${packageNames.angular}`,
      `00-config.json: ${buildTools[0]}`,
      `01-config.js: ${packageNames.angular}`,
      `01-config.js: ${buildTools[1]}`,
    ]);
  });
});

test('PR7 detector finds forbidden Angular manifest metadata deterministically', () => {
  withFixtureRoot((fixtureRoot) => {
    const packageNames = {
      angular: ['ang', 'ular'].join(''),
      angular2react: ['angular2', 'react'].join(''),
      angularMessages: ['angular', 'messages'].join('-'),
      angularSanitize: ['angular', 'sanitize'].join('-'),
      angularSpinner: ['angular', 'spinner'].join('-'),
      angularUiBootstrap: ['angular', 'ui-bootstrap'].join('-'),
      angularUiSortable: ['angular', 'ui-sortable'].join('-'),
      angulartics: ['angular', 'tics'].join(''),
      angularticsGoogleAnalytics: ['angulartics', 'google-analytics'].join('-'),
      hybrid: ['@uirouter', 'react-hybrid'].join('/'),
      mocks: ['angular', 'mocks'].join('-'),
      ngComponent: ['ng', 'component'].join(''),
      ngImport: ['ng', 'import'].join(''),
      ngtemplateLoader: ['ng', 'template-loader'].join(''),
      react2angular: ['react2', 'angular'].join(''),
      types: ['@types', 'angular'].join('/'),
      typesMocks: ['@types', 'angular-mocks'].join('/'),
      typesUiBootstrap: ['@types', 'angular-ui-bootstrap'].join('/'),
      router: ['@uirouter', 'angularjs'].join('/'),
      select: ['ui', 'select'].join('-'),
    };
    writeFileSync(
      path.join(fixtureRoot, 'alias-package.json'),
      JSON.stringify({
        dependencies: {
          [packageNames.angularMessages]: '1.0.0',
          legacy: `npm:${packageNames.angular}@1.8.3`,
          legacyScoped: `npm:${packageNames.types}@1.6.26`,
        },
        devDependencies: { legacy: `npm:${packageNames.typesMocks}@1.5.10` },
        optionalDependencies: { legacy: `npm:${packageNames.typesUiBootstrap}@0.13.41` },
        peerDependencies: { legacy: `npm:${packageNames.router}@1.0.26` },
        pnpm: { overrides: { legacy: `npm:${packageNames.hybrid}@1.0.2` } },
      }),
    );
    writeFileSync(
      path.join(fixtureRoot, 'package.json'),
      JSON.stringify({
        dependencies: {
          [packageNames.angular]: '1.0.0',
          [packageNames.angular2react]: '1.0.0',
          [packageNames.angularMessages]: '1.0.0',
          [packageNames.angularSanitize]: '1.0.0',
          [packageNames.angularSpinner]: '1.0.0',
          [packageNames.angularUiBootstrap]: '1.0.0',
          [packageNames.angularUiSortable]: '1.0.0',
          [packageNames.angulartics]: '1.0.0',
          [packageNames.angularticsGoogleAnalytics]: '1.0.0',
          [packageNames.hybrid]: '1.0.0',
          [packageNames.ngComponent]: '1.0.0',
          [packageNames.ngImport]: '1.0.0',
          [packageNames.ngtemplateLoader]: '1.0.0',
          [packageNames.react2angular]: '1.0.0',
          [packageNames.typesMocks]: '1.0.0',
          [packageNames.typesUiBootstrap]: '1.0.0',
          react: '1.0.0',
        },
        devDependencies: { [packageNames.mocks]: '1.0.0' },
        optionalDependencies: { [packageNames.types]: '1.0.0' },
        peerDependencies: { [packageNames.router]: '1.0.0' },
        pnpm: { overrides: { [packageNames.select]: '1.0.0' } },
      }),
    );
    writeFileSync(
      path.join(fixtureRoot, 'neutral-package.json'),
      JSON.stringify({
        dependencies: { legacy: 'npm:react@16.14.0', react: '1.0.0' },
        devDependencies: { 'html-loader': '1.0.0', vite: '1.0.0' },
        pnpm: { overrides: { webpack: '1.0.0' } },
      }),
    );

    assert.deepEqual(findForbiddenAngularManifestMetadata(workspaceSourceFiles(fixtureRoot), fixtureRoot), [
      `alias-package.json dependencies: ${packageNames.types}`,
      `alias-package.json dependencies: ${packageNames.angular}`,
      `alias-package.json dependencies: ${packageNames.angularMessages}`,
      `alias-package.json devDependencies: ${packageNames.typesMocks}`,
      `alias-package.json optionalDependencies: ${packageNames.typesUiBootstrap}`,
      `alias-package.json peerDependencies: ${packageNames.router}`,
      `alias-package.json pnpm.overrides: ${packageNames.hybrid}`,
      `package.json dependencies: ${packageNames.typesMocks}`,
      `package.json dependencies: ${packageNames.typesUiBootstrap}`,
      `package.json dependencies: ${packageNames.hybrid}`,
      `package.json dependencies: ${packageNames.angular}`,
      `package.json dependencies: ${packageNames.angularMessages}`,
      `package.json dependencies: ${packageNames.angularSanitize}`,
      `package.json dependencies: ${packageNames.angularSpinner}`,
      `package.json dependencies: ${packageNames.angularUiBootstrap}`,
      `package.json dependencies: ${packageNames.angularUiSortable}`,
      `package.json dependencies: ${packageNames.angular2react}`,
      `package.json dependencies: ${packageNames.angulartics}`,
      `package.json dependencies: ${packageNames.angularticsGoogleAnalytics}`,
      `package.json dependencies: ${packageNames.ngComponent}`,
      `package.json dependencies: ${packageNames.ngImport}`,
      `package.json dependencies: ${packageNames.ngtemplateLoader}`,
      `package.json dependencies: ${packageNames.react2angular}`,
      `package.json devDependencies: ${packageNames.mocks}`,
      `package.json optionalDependencies: ${packageNames.types}`,
      `package.json peerDependencies: ${packageNames.router}`,
      `package.json pnpm.overrides: ${packageNames.select}`,
    ]);
  });
});

test('PR7 detector finds forbidden Angular pnpm workspace overrides', () => {
  withFixtureRoot((fixtureRoot) => {
    const packageNames = {
      angular: ['ang', 'ular'].join(''),
      angularMessages: ['angular', 'messages'].join('-'),
      angularSanitize: ['angular', 'sanitize'].join('-'),
      hybrid: ['@uirouter', 'react-hybrid'].join('/'),
      types: ['@types', 'angular'].join('/'),
      typesMocks: ['@types', 'angular-mocks'].join('/'),
      typesUiBootstrap: ['@types', 'angular-ui-bootstrap'].join('/'),
    };
    const blockWorkspacePath = path.join(fixtureRoot, 'block-workspace.yaml');
    writeFileSync(
      blockWorkspacePath,
      [
        'packages:',
        "  - 'packages/*'",
        `sharedAlias: &sharedAlias npm:${packageNames.types}@1.6.26`,
        'overrides:',
        `  ${packageNames.angular}: 1.8.3`,
        `  legacy: npm:${packageNames.angularMessages}@1.8.3`,
        '  scopedLegacy: *sharedAlias',
        `  '${packageNames.hybrid}@1.0.2': 1.0.2`,
        `  "fixture@1.0.0>${packageNames.typesMocks}@1.5.10": 1.5.10`,
        '  react: 16.14.0',
        'allowBuilds:',
        '  esbuild: true',
      ].join('\n'),
    );
    const inlineWorkspacePath = path.join(fixtureRoot, 'inline-workspace.yaml');
    writeFileSync(
      inlineWorkspacePath,
      `overrides: { legacy: 'npm:${packageNames.angularSanitize}@1.8.3', '${packageNames.typesUiBootstrap}': 0.13.41, neutral: 'npm:react@16.14.0' }`,
    );

    assert.deepEqual(findForbiddenAngularWorkspaceOverrides([inlineWorkspacePath, blockWorkspacePath], fixtureRoot), [
      `block-workspace.yaml overrides: ${packageNames.types}`,
      `block-workspace.yaml overrides: ${packageNames.typesMocks}`,
      `block-workspace.yaml overrides: ${packageNames.hybrid}`,
      `block-workspace.yaml overrides: ${packageNames.angular}`,
      `block-workspace.yaml overrides: ${packageNames.angularMessages}`,
      `inline-workspace.yaml overrides: ${packageNames.typesUiBootstrap}`,
      `inline-workspace.yaml overrides: ${packageNames.angularSanitize}`,
    ]);
  });
});

test('PR7 detector finds forbidden Angular pnpm lock references deterministically', () => {
  const packageNames = {
    angular: ['ang', 'ular'].join(''),
    angular2react: ['angular2', 'react'].join(''),
    mocks: ['angular', 'mocks'].join('-'),
    ngComponent: ['ng', 'component'].join(''),
    ngImport: ['ng', 'import'].join(''),
    ngtemplateLoader: ['ng', 'template-loader'].join(''),
    react2angular: ['react2', 'angular'].join(''),
    router: ['@uirouter', 'angularjs'].join('/'),
    types: ['@types', 'angular'].join('/'),
  };
  const lockData = parseYaml(
    [
      'importers:',
      '  fixture:',
      '    dependencies:',
      '      componentAdapter:',
      `        specifier: npm:${packageNames.ngComponent}@0.2.0`,
      '        version: 0.2.0',
      '      importAdapter:',
      `        specifier: npm:${packageNames.ngImport}@0.3.1`,
      '        version: 0.3.1',
      '      reactAdapter:',
      `        specifier: npm:${packageNames.react2angular}@4.0.6`,
      '        version: 4.0.6',
      '      templateLoader:',
      `        specifier: npm:${packageNames.ngtemplateLoader}@2.1.0`,
      '        version: 2.1.0',
      '      viewAdapter:',
      `        specifier: npm:${packageNames.angular2react}@3.0.0`,
      '        version: 3.0.0',
      '      neutralAlias:',
      '        specifier: npm:angular-like@1.0.0',
      '        version: 1.0.0',
      `    description: ${packageNames.angular}`,
      'overrides:',
      `  legacy: npm:${packageNames.angular}@1.8.3`,
      `  fixture>${packageNames.types}@1.8.9: 1.8.9`,
      '  neutral: npm:angular-like@1.0.0',
      'packages:',
      `  "${packageNames.types}@1.8.9": {}`,
      `  ${packageNames.angular}@1.8.3: {}`,
      `  react@16.14.0(${packageNames.router}@1.0.31): {}`,
      '  angular-like@1.0.0:',
      `    description: ${packageNames.angular}`,
      '    resolution:',
      '      integrity: sha512-angular',
      '  "@types/angular-like@1.0.0": {}',
      '  foo_angular@1.0.0: {}',
      '  react@16.14.0(angular-like@1.0.0): {}',
      'snapshots:',
      '  fixture@1.0.0:',
      '    dependencies:',
      `      ${packageNames.mocks}: 1.8.3`,
      '      angular-like: 1.0.0',
      '      neutralAlias: npm:angular-like@1.0.0',
    ].join('\n'),
  );

  assert.deepEqual(findForbiddenAngularLockReferences(lockData), [
    `importers > fixture > dependencies > componentAdapter > specifier [value]: ${packageNames.ngComponent}`,
    `importers > fixture > dependencies > importAdapter > specifier [value]: ${packageNames.ngImport}`,
    `importers > fixture > dependencies > reactAdapter > specifier [value]: ${packageNames.react2angular}`,
    `importers > fixture > dependencies > templateLoader > specifier [value]: ${packageNames.ngtemplateLoader}`,
    `importers > fixture > dependencies > viewAdapter > specifier [value]: ${packageNames.angular2react}`,
    `overrides > fixture>${packageNames.types}@1.8.9 [key]: ${packageNames.types}`,
    `overrides > legacy [value]: ${packageNames.angular}`,
    `packages > ${packageNames.types}@1.8.9 [key]: ${packageNames.types}`,
    `packages > ${packageNames.angular}@1.8.3 [key]: ${packageNames.angular}`,
    `packages > react@16.14.0(${packageNames.router}@1.0.31) [key]: ${packageNames.router}`,
    `snapshots > fixture@1.0.0 > dependencies > ${packageNames.mocks} [key]: ${packageNames.mocks}`,
  ]);
});

test('package families are rejected across metadata without suffix or substring false positives', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularAnimate = ['angular', 'animate'].join('-');
    const angularCore = ['@angular', 'core'].join('/');
    const neutralPackages = ['conventional-changelog-angular', 'angular-like', 'foo-angular'];
    const manifestPath = path.join(fixtureRoot, 'package.json');
    writeFileSync(
      manifestPath,
      JSON.stringify({
        dependencies: {
          animateAlias: `npm:${angularAnimate}@1.8.3`,
          coreAlias: `npm:${angularCore}@17.0.0`,
          ...Object.fromEntries(neutralPackages.map((packageName) => [packageName, '1.0.0'])),
        },
      }),
    );
    const workspacePath = path.join(fixtureRoot, 'pnpm-workspace.yaml');
    writeFileSync(
      workspacePath,
      [
        'overrides:',
        `  animateAlias: npm:${angularAnimate}@1.8.3`,
        `  coreAlias: npm:${angularCore}@17.0.0`,
        ...neutralPackages.map((packageName) => `  ${packageName}: 1.0.0`),
      ].join('\n'),
    );
    const configPath = path.join(fixtureRoot, 'tool.config.json');
    writeFileSync(configPath, JSON.stringify({ plugins: [angularAnimate, angularCore, ...neutralPackages] }));
    const lockData = {
      importers: {
        fixture: {
          dependencies: {
            animateAlias: { specifier: `npm:${angularAnimate}@1.8.3`, version: '1.8.3' },
            coreAlias: { specifier: `npm:${angularCore}@17.0.0`, version: '17.0.0' },
            ...Object.fromEntries(
              neutralPackages.map((packageName) => [packageName, { specifier: packageName, version: '1.0.0' }]),
            ),
          },
        },
      },
      packages: {
        [`${angularCore}@17.0.0`]: {},
        [`${angularAnimate}@1.8.3`]: {},
        [`react@18.0.0(${angularCore}@17.0.0)`]: {},
        ...Object.fromEntries(neutralPackages.map((packageName) => [`${packageName}@1.0.0`, {}])),
      },
    };

    assert.deepEqual(findForbiddenAngularManifestMetadata([manifestPath], fixtureRoot), [
      `package.json dependencies: ${angularCore}`,
      `package.json dependencies: ${angularAnimate}`,
    ]);
    assert.deepEqual(findForbiddenAngularWorkspaceOverrides([workspacePath], fixtureRoot), [
      `pnpm-workspace.yaml overrides: ${angularCore}`,
      `pnpm-workspace.yaml overrides: ${angularAnimate}`,
    ]);
    assert.deepEqual(findForbiddenConfigObjectReferences([configPath], fixtureRoot), [
      `tool.config.json: ${angularCore}`,
      `tool.config.json: ${angularAnimate}`,
    ]);
    assert.deepEqual(findForbiddenAngularLockReferences(lockData), [
      `importers > fixture > dependencies > animateAlias > specifier [value]: ${angularAnimate}`,
      `importers > fixture > dependencies > coreAlias > specifier [value]: ${angularCore}`,
      `packages > ${angularCore}@17.0.0 [key]: ${angularCore}`,
      `packages > ${angularAnimate}@1.8.3 [key]: ${angularAnimate}`,
      `packages > react@18.0.0(${angularCore}@17.0.0) [key]: ${angularCore}`,
    ]);
  });
});

test('PR7 detector fixtures are accepted by repository-level source scans', () => {
  assert.deepEqual(findForbiddenAngularSourceReferences([__filename], repositoryRoot), []);
  assert.deepEqual(findForbiddenAngularApiReferences([__filename], repositoryRoot), []);
  assert.deepEqual(findForbiddenAngularBuildToolReferences([__filename], repositoryRoot), []);
  assert.deepEqual(findForbiddenAngularTestIdentifiers([__filename], repositoryRoot), []);
});

test('active source scan covers Deck, Deck-Kayenta, app artifacts, functional tests, and Karma', () => {
  const files = activeSourceFiles();
  const nodeScriptTests = readdirSync(appScriptsRoot, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.test.js'))
    .map((entry) => path.join(appScriptsRoot, entry.name))
    .filter((file) => file !== angularRemovalGuardFixturePath);
  const representativeFiles = [
    path.join(deckRoot, 'packages/app/index.deck'),
    path.join(deckRoot, 'packages/app/index.html'),
    path.join(appScriptsRoot, 'bootstrap-entry.test.js'),
    path.join(coreSourceRoot, 'bootstrap/bootstrapDeck.spec.tsx'),
    path.join(deckRoot, 'packages/scripts/index.js'),
    path.join(deckRoot, 'scripts/buildModules.js'),
    path.join(deckRoot, 'packages/pluginsdk/scripts/check-plugin.js'),
    path.join(deckRoot, 'packages/pluginsdk/scripts/check-plugin/linters/lint.eslintrc.js'),
    path.join(deckRoot, 'packages/pluginsdk-peerdeps/convert-peerdeps.js'),
    path.join(deckRoot, 'packages/eslint-plugin/rules/import-sort.spec.ts'),
    ...nodeScriptTests,
    path.join(deckKayentaSourceRoot, 'initializeKayenta.spec.ts'),
    path.join(deckKayentaSourceRoot, 'kayenta/report/detail/graph/semiotic/declarations/semiotic.d.ts'),
    path.join(deckTestRoot, 'functional/cypress.config.ts'),
    path.join(deckRoot, 'packages/app/webpack.config.js'),
    path.join(repositoryRoot, 'deck-kayenta/jest.config.js'),
    ...karmaFiles,
  ];

  representativeFiles.forEach((file) =>
    assert.ok(files.includes(file), `Expected active source scan to include ${file}`),
  );
  assert.ok(activeBuildConfigFiles.includes(path.join(deckRoot, 'scripts/buildModules.js')));
  assert.equal(files.includes(angularRemovalGuardFixturePath), false);
  assert.deepEqual(files, [...new Set(files)].sort());
});

test('app artifact discovery includes active entrypoints and excludes only generated content and its fixture host', () => {
  withFixtureRoot((appRoot) => {
    const scriptsRoot = path.join(appRoot, 'scripts');
    const generatedRoot = path.join(appRoot, 'dist');
    const dependencyRoot = path.join(appRoot, 'node_modules');
    mkdirSync(scriptsRoot);
    mkdirSync(generatedRoot);
    mkdirSync(dependencyRoot);
    const fixtureHost = path.join(scriptsRoot, 'guard.test.js');
    const expectedFiles = [
      path.join(appRoot, 'index.deck'),
      path.join(appRoot, 'index.html'),
      path.join(scriptsRoot, 'bootstrap.test.js'),
      path.join(scriptsRoot, 'local-auth.js'),
      path.join(scriptsRoot, 'local-run.js'),
    ].sort();
    [
      ...expectedFiles,
      fixtureHost,
      path.join(generatedRoot, 'bundle.js'),
      path.join(dependencyRoot, 'library.js'),
    ].forEach((file) => writeFileSync(file, 'fixture'));

    assert.deepEqual(discoverActiveAppArtifactFiles(appRoot, fixtureHost), expectedFiles);
  });
});

test('app shell detector rejects Angular bootstrap contracts and accepts React and neutral HTML', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtures = new Map([
      ['00-ng-app.html', '<html ng-app="fixture"><body></body></html>'],
      ['01-strict.deck', '<body ng-strict-di><div></div></body>'],
      ['02-spinnaker.html', '<spinnaker></spinnaker>'],
      ['03-data-ng-app.html', '<html data-ng-app="fixture"><body></body></html>'],
      ['04-x-ng-app.html', '<html x-ng-app="fixture"><body></body></html>'],
      ['05-colon-ng-app.html', '<html ng:app="fixture"><body></body></html>'],
      ['06-data-strict.html', '<body data-ng-strict-di><div></div></body>'],
      ['07-x-strict.html', '<body x-ng-strict-di><div></div></body>'],
      ['08-colon-strict.html', '<body ng:strict-di><div></div></body>'],
      ['09-inline-style.html', '<style>.ng-invalid[ng-model] { color: red; }</style><main></main>'],
      ['98-neutral.html', '<style>.nginx { color: green; }</style><main data-app-shell="neutral"></main>'],
      [
        '99-react.deck',
        '<!-- <spinnaker ng-app="docs"></spinnaker> --><script>const example = `<spinnaker>`;</script><div id="spinnaker-root"></div>',
      ],
    ]);
    const files = Array.from(fixtures, ([file, source]) => {
      const fixturePath = path.join(fixtureRoot, file);
      writeFileSync(fixturePath, source);
      return fixturePath;
    });

    assert.deepEqual(findForbiddenAppShellContracts(files, fixtureRoot), [
      '00-ng-app.html: ng-app attribute',
      '01-strict.deck: ng-strict-di attribute',
      '02-spinnaker.html: legacy <spinnaker> root',
      '03-data-ng-app.html: ng-app attribute',
      '04-x-ng-app.html: ng-app attribute',
      '05-colon-ng-app.html: ng-app attribute',
      '06-data-strict.html: ng-strict-di attribute',
      '07-x-strict.html: ng-strict-di attribute',
      '08-colon-strict.html: ng-strict-di attribute',
      '09-inline-style.html: ng-* selector',
    ]);
  });
});

test('active app shells use only the React root contract', () => {
  const shellFiles = [path.join(deckRoot, 'packages/app/index.deck'), path.join(deckRoot, 'packages/app/index.html')];

  shellFiles.forEach((file) => assert.ok(activeSourceFiles().includes(file)));
  assert.deepEqual(findForbiddenAppShellContracts(shellFiles, repositoryRoot), []);
});

test('active package metadata covers functional and nested scaffold manifests', () => {
  assert.ok(activeManifestFiles().includes(path.join(deckTestRoot, 'functional/package.json')));
  assert.ok(activeManifestFiles().includes(path.join(deckRoot, 'packages/pluginsdk/scaffold/package.json')));
});

test('active workspace metadata covers Deck, Deck-Kayenta, and functional roots', () => {
  assert.deepEqual(activeWorkspacePaths, [
    deckWorkspacePath,
    deckKayentaWorkspacePath,
    path.join(deckTestRoot, 'functional/pnpm-workspace.yaml'),
  ]);
});

test('active pnpm lock metadata covers Deck, Deck-Kayenta, and functional roots', () => {
  assert.deepEqual(activeLockPaths, [
    deckLockPath,
    deckKayentaLockPath,
    path.join(deckTestRoot, 'functional/pnpm-lock.yaml'),
  ]);
});

test('active source and test infrastructure do not use Angular', () => {
  const files = activeSourceFiles();

  assert.deepEqual(
    [
      ...findForbiddenAngularSourceReferences(files, repositoryRoot),
      ...findForbiddenAngularApiReferences(files, repositoryRoot),
    ],
    [],
  );
});

test('active source, tests, styles, and configs contain no lexical Angular residue', () => {
  const files = activePolicyFiles();

  assert.equal(files.includes(angularRemovalGuardFixturePath), false);
  assert.deepEqual(findActiveLexicalResidue(files, repositoryRoot), []);
});

test('lexical zero policy scans active comments and strings without product-file allowlists', () => {
  const files = activePolicyFiles();

  assert.ok(files.includes(path.join(deckRoot, 'packages/scripts/index.js')));
  assert.equal(files.includes(angularRemovalGuardFixturePath), false);
  assert.equal(
    files.some((file) => /(?:^|\/)(?:CHANGELOG[^/]*|README[^/]*)$|pnpm-lock\.yaml$/.test(file)),
    false,
  );
  assert.deepEqual(files, [...new Set(files)].sort());
});

test('active build configs and helpers do not use Angular template loaders', () => {
  const files = [
    ...activeBuildConfigFiles,
    ...(existsSync(angularTemplateLoaderHelperPath) ? [angularTemplateLoaderHelperPath] : []),
  ];

  assert.deepEqual(findForbiddenAngularBuildToolReferences(files, repositoryRoot), []);
});

test('active build config objects have no forbidden package or tool references', () => {
  assert.deepEqual(findForbiddenConfigObjectReferences(activeBuildConfigFiles, repositoryRoot), []);
});

test('shared Rollup URL assets accept generic HTML without forbidden template tools', async () => {
  const fixtureRoot = mkdtempSync(path.join(tmpdir(), 'rollup-html-asset-'));
  const entryPath = path.join(fixtureRoot, 'entry.js');
  const assetPath = path.join(fixtureRoot, 'document.html');
  let bundle;

  try {
    writeFileSync(entryPath, "import documentAsset from './document.html';\nexport default documentAsset;\n");
    writeFileSync(assetPath, '<main>Generic asset</main>');

    const { rollup } = createRequire(path.join(deckRoot, 'packages/scripts/package.json'))('rollup');
    const sharedRollupConfig = require(sharedRollupConfigPath);
    bundle = await rollup({ input: entryPath, plugins: sharedRollupConfig.plugins });
    const { output } = await bundle.generate({ dir: path.join(fixtureRoot, 'dist'), format: 'es' });
    const chunk = output.find(({ type }) => type === 'chunk');

    assert.match(chunk.code, /data:text\/html;base64,/);
    assert.equal(existsSync(angularTemplateLoaderHelperPath), false);
    assert.deepEqual(findForbiddenAngularBuildToolReferences([sharedRollupConfigPath], repositoryRoot), []);
  } finally {
    await bundle?.close();
    rmSync(fixtureRoot, { recursive: true, force: true });
  }
});

test('custom Angular template-loader helper is absent', () => {
  assert.throws(() => readFileSync(angularTemplateLoaderHelperPath, 'utf8'), { code: 'ENOENT' });
});

test('active non-ESLint specs use neutral async identifiers', () => {
  assert.deepEqual(findForbiddenAngularTestIdentifiers(activeSpecFiles(), repositoryRoot), []);
});

test('active source has no Angular DI metadata', () => {
  const files = activeSourceFiles();

  assert.ok(files.includes(path.join(deckRoot, 'packages/app/index.html')));
  assert.ok(files.includes(path.join(appScriptsRoot, 'bootstrap-entry.test.js')));
  assert.ok(files.includes(path.join(deckKayentaSourceRoot, 'initializeKayenta.ts')));
  assert.deepEqual(findAngularDiMetadata(files, repositoryRoot), []);
});

test('active styles have no obsolete framework-emitted contracts', () => {
  const files = activeStyleFiles();

  assert.ok(files.includes(path.join(coreSourceRoot, 'fonts/icons.css')));
  assert.ok(files.includes(path.join(deckKayentaSourceRoot, 'kayenta/canary.less')));
  assert.deepEqual(files, [...files].sort());
  assert.deepEqual(findForbiddenStyleContracts(files, repositoryRoot), []);
});

test('removed compatibility source and stylesheet paths stay absent', () => {
  const removedPaths = [
    path.join(coreSourceRoot, 'angular'),
    path.join(coreSourceRoot, 'core.module.ts'),
    path.join(coreSourceRoot, 'types/promise.d.ts'),
    path.join(coreSourceRoot, 'utils/angular-messages.d.ts'),
    path.join(coreSourceRoot, 'utils/angular-sanitize.d.ts'),
    path.join(coreSourceRoot, 'utils/angular-spinner.d.ts'),
    path.join(coreSourceRoot, 'utils/ui-select.d.ts'),
    angularTemplateLoaderHelperPath,
  ];

  removedPaths.forEach((removedPath) => assert.equal(existsSync(removedPath), false, removedPath));
});

test('active source has no removed compatibility APIs or metadata', () => {
  const files = activeSourceFiles();

  assert.deepEqual(findRemovedCompatibilityApis(files, repositoryRoot), []);
  assert.deepEqual(findObsoleteRegistryMetadata(files, repositoryRoot), []);
});

test('production onNextRefresh callers include representative ownership forms with zero findings', () => {
  const productionFiles = productionSourceRoots().flatMap((sourceRoot) => productionSourceFiles(sourceRoot));
  const usage = findOnNextRefreshUsage(productionFiles, repositoryRoot);
  const representativeCallers = [
    'deck/packages/amazon/src/function/CreateLambdaFunction.tsx',
    'deck/packages/google/src/loadBalancer/configure/common/GceProxyLoadBalancerModal.tsx',
  ];

  representativeCallers.forEach((file) => assert.ok(usage.callFiles.includes(file), file));
  assert.deepEqual(usage.findings, []);
});

test('active package manifests have no forbidden Angular dependencies or build tools', () => {
  assert.deepEqual(findForbiddenAngularManifestMetadata(activeManifestFiles(), repositoryRoot), []);
});

test('published router contracts match the Core host versions', () => {
  const coreManifest = JSON.parse(readFileSync(coreManifestPath, 'utf8'));
  const kayentaManifest = JSON.parse(readFileSync(path.join(deckKayentaRoot, 'package.json'), 'utf8'));
  const pluginsdkPeerdepsManifest = JSON.parse(readFileSync(pluginsdkPeerdepsManifestPath, 'utf8'));

  ['@uirouter/core', '@uirouter/react'].forEach((routerPackage) => {
    const hostVersion = coreManifest.dependencies[routerPackage];
    assert.equal(kayentaManifest.peerDependencies[routerPackage], hostVersion);
    assert.equal(kayentaManifest.devDependencies[routerPackage], hostVersion);
    assert.equal(pluginsdkPeerdepsManifest.peerDependencies[routerPackage], hostVersion);
  });
});

test('active workspaces have no forbidden Angular overrides', () => {
  assert.deepEqual(findForbiddenAngularWorkspaceOverrides(activeWorkspacePaths, repositoryRoot), []);
});

test('active pnpm lock graphs have no forbidden Angular package references', () => {
  assert.deepEqual(findForbiddenAngularLockfileReferences(activeLockPaths, repositoryRoot), []);
});

test('production Angular graph scan detects value imports and module aliases', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularPackage = ['ang', 'ular'].join('');
    const moduleMethod = ['mod', 'ule'].join('');
    const fixtures = new Map([
      ['default.ts', `import angular from '${angularPackage}';\nangular.${moduleMethod}('default', []);`],
      ['named.ts', `import {\n  module as ngModule,\n} from '${angularPackage}';\nngModule('named', []);`],
      ['namespace.ts', `import * as ng from '${angularPackage}';\nng.module('namespace', []);`],
      ['require.cjs', `const angular = require('${angularPackage}');\nangular.${moduleMethod}('required', []);`],
      ['side-effect.js', `import '${angularPackage}';`],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), [
      'default.ts: Angular runtime import',
      'default.ts: Angular module factory',
      'named.ts: Angular runtime import',
      'named.ts: Angular module factory',
      'namespace.ts: Angular runtime import',
      'namespace.ts: Angular module factory',
      'require.cjs: Angular runtime import',
      'require.cjs: Angular module factory',
      'side-effect.js: Angular runtime import',
    ]);
  });
});

test('production Angular graph scan detects ambient module factories without imports', () => {
  withFixtureRoot((fixtureRoot) => {
    const moduleMethod = ['mod', 'ule'].join('');
    const fixtures = new Map([
      ['ambient.ts', `angular.${moduleMethod}('regression', []);`],
      ['window.ts', `window.angular.${moduleMethod}('regression', []);`],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), [
      'ambient.ts: Angular module factory',
      'window.ts: Angular module factory',
    ]);
  });
});

test('production Angular graph scan detects bracket module and registration calls', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularPackage = ['ang', 'ular'].join('');
    const moduleMethod = ['mod', 'ule'].join('');
    const fixturePath = path.join(fixtureRoot, 'bracket.ts');
    writeFileSync(
      fixturePath,
      `import angular from '${angularPackage}';\nconst runtime = angular['${moduleMethod}']('fixture', []);\nruntime['config'](['service', function configure(service) {}]);`,
    );

    assert.deepEqual(findProductionAngularGraph([fixturePath], fixtureRoot), [
      'bracket.ts: Angular runtime import',
      'bracket.ts: Angular module factory',
      'bracket.ts: Angular .config registration',
    ]);
  });
});

test('production Angular graph scan ignores registrations on non-Angular receivers', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularPackage = ['ang', 'ular'].join('');
    const fixturePath = path.join(fixtureRoot, 'neutral-receiver.ts');
    writeFileSync(
      fixturePath,
      `import angular from '${angularPackage}';\nlet pending;\nclient.config(['region']);\nclient.filter(['active']);`,
    );

    assert.deepEqual(findProductionAngularGraph([fixturePath], fixtureRoot), [
      'neutral-receiver.ts: Angular runtime import',
    ]);
  });
});

test('runtime Angular graph scan ignores type-only imports that the strict PR7 detector rejects', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularPackage = ['ang', 'ular'].join('');
    writeFileSync(
      path.join(fixtureRoot, 'types.ts'),
      `import type { IQService } from '${angularPackage}';\nexport type Q = IQService;`,
    );

    const files = productionSourceFiles(fixtureRoot);
    assert.deepEqual(findProductionAngularGraph(files, fixtureRoot), []);
    assert.deepEqual(findForbiddenAngularSourceReferences(files, fixtureRoot), [`types.ts: ${angularPackage}`]);
  });
});

test('production Angular graph scan detects every Angular registration method', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularPackage = ['ang', 'ular'].join('');
    angularRegistrationMethods.forEach((method) => {
      writeFileSync(
        path.join(fixtureRoot, `${method}.ts`),
        `import { module } from '${angularPackage}';\nconst ngModule = module('fixture', []);\nngModule.${method}('fixture', {});`,
      );
    });

    assert.deepEqual(
      findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot),
      angularRegistrationMethods.flatMap((method) => [
        `${method}.ts: Angular runtime import`,
        `${method}.ts: Angular module factory`,
        `${method}.ts: Angular .${method} registration`,
      ]),
    );
  });
});

test('production Angular graph scan detects Angular React adapters in every source extension', () => {
  withFixtureRoot((fixtureRoot) => {
    const adapters = ['angularComponentFromReact', 'react2angular', 'angular2react'];
    const fixtureFiles = adapters
      .flatMap((adapter) => sourceExtensions.map((extension) => `${adapter}${extension}`))
      .sort();
    fixtureFiles.forEach((file) => {
      const adapter = path.basename(file, path.extname(file));
      writeFileSync(path.join(fixtureRoot, file), `${adapter}(Component);`);
    });

    assert.deepEqual(
      findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot),
      fixtureFiles.map((file) => `${file}: ${path.basename(file, path.extname(file))}`),
    );
  });
});

test('production Angular graph scan excludes spec and test source', () => {
  withFixtureRoot((fixtureRoot) => {
    const angularPackage = ['ang', 'ular'].join('');
    const moduleMethod = ['mod', 'ule'].join('');
    const source = `import angular from '${angularPackage}';\nangular.${moduleMethod}('fixture', []);`;
    writeFileSync(path.join(fixtureRoot, 'fixture.spec.ts'), source);
    writeFileSync(path.join(fixtureRoot, 'fixture.test.ts'), source);

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), []);
  });
});

test('legacy facade scan includes and accepts its own source', () => {
  const scannedFiles = workspaceSourceFiles(path.dirname(__filename));

  assert.ok(scannedFiles.includes(__filename));
  assert.deepEqual(findLegacyFacadeReferences([__filename], repositoryRoot), []);
});

test('legacy facade scan detects names and raw paths independent of import formatting', () => {
  withFixtureRoot((fixtureRoot) => {
    const relativeImportPath = ['..', angularServicesImportPath].join('/');
    const fixtureSources = new Map([
      ['accessor.cts', angularServiceAccessorsName],
      ['formatted-require.cjs', ['const load = require', '  ', '(', `  \`${relativeImportPath}\``, ');'].join('\n')],
      ['service.ts', angularServicesName],
      ['template-dynamic.mts', ['const load = import(`', relativeImportPath, '`);'].join('')],
    ]);
    fixtureSources.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findLegacyFacadeReferences(workspaceSourceFiles(fixtureRoot), fixtureRoot), [
      `accessor.cts: ${angularServiceAccessorsName}`,
      `formatted-require.cjs: ${angularServicesImportPath}`,
      `service.ts: ${angularServicesName}`,
      `template-dynamic.mts: ${angularServicesImportPath}`,
    ]);
  });
});

test('legacy facade scan checks every supported JavaScript and TypeScript extension', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtureFiles = sourceExtensions.map((extension) => `fixture${extension}`).sort();
    fixtureFiles.forEach((file) => writeFileSync(path.join(fixtureRoot, file), angularServicesName));

    assert.deepEqual(
      findLegacyFacadeReferences(workspaceSourceFiles(fixtureRoot), fixtureRoot),
      fixtureFiles.map((file) => `${file}: ${angularServicesName}`),
    );
  });
});

test('workspace source scan rejects file and directory symbolic links', () => {
  withFixtureRoot((fixtureRoot) => {
    const fileCaseRoot = path.join(fixtureRoot, 'file-case');
    mkdirSync(fileCaseRoot);
    const sourceFile = path.join(fileCaseRoot, 'source.ts');
    const linkedFile = path.join(fileCaseRoot, 'linked.ts');
    writeFileSync(sourceFile, 'export {};');
    symlinkSync(sourceFile, linkedFile, 'file');

    assert.throws(() => workspaceSourceFiles(fileCaseRoot), {
      message: `Refusing to scan symbolic link: ${linkedFile}`,
    });

    const directoryCaseRoot = path.join(fixtureRoot, 'directory-case');
    const sourceDirectory = path.join(directoryCaseRoot, 'source');
    const linkedDirectory = path.join(directoryCaseRoot, 'linked-source');
    mkdirSync(sourceDirectory, { recursive: true });
    writeFileSync(path.join(sourceDirectory, 'source.ts'), 'export {};');
    symlinkSync(sourceDirectory, linkedDirectory, 'dir');

    assert.throws(() => workspaceSourceFiles(directoryCaseRoot), {
      message: `Refusing to scan symbolic link: ${linkedDirectory}`,
    });
    assert.throws(() => workspaceSourceFiles(linkedDirectory), {
      message: `Refusing to scan symbolic link: ${linkedDirectory}`,
    });
  });
});

test('syntax-aware source detector covers module syntax and ignores lexical noise', () => {
  withFixtureRoot((fixtureRoot) => {
    const forbiddenPackage = ['ang', 'ular'].join('');
    const fixtures = new Map([
      ['00-import.ts', `import runtime from '${forbiddenPackage}';`],
      ['01-export-star.ts', `export * from '${forbiddenPackage}/messages';`],
      ['02-export-named.ts', `export { module as moduleFactory } from '${forbiddenPackage}';`],
      ['03-ambient.d.ts', `declare module '${forbiddenPackage}' { export const version: string; }`],
      ['04-dynamic.ts', `const runtime = import('${forbiddenPackage}');`],
      ['05-require-html.ts', `const template = require('./template.html');`],
      ['06-import-html.ts', `import template from './template.html?raw';`],
      ['07-export-html.ts', `export { default as template } from './template.html';`],
      ['08-dynamic-html.ts', `const template = import('./template.html#asset');`],
      ['09-ambient-html.d.ts', `declare module '*.html' { const template: string; export default template; }`],
      ['10-import-equals.ts', `import runtime = require('${forbiddenPackage}/messages');`],
      ['11-import-type-node.ts', `type Service = import('${forbiddenPackage}').IService;`],
      ['12-import-type-html.ts', `type Template = import('./template.html?raw').default;`],
      ['13-require-resolve.ts', `const runtimePath = require.resolve('${forbiddenPackage}/runtime');`],
      [
        '99-neutral.ts',
        [
          `// import runtime from '${forbiddenPackage}';`,
          `const documentation = "require('${forbiddenPackage}') and import('./template.html')";`,
          "const hash = 'sha512-angular';",
          "import nearby from 'angular-like';",
          "import nearbyTypes from '@types/angular-like';",
        ].join('\n'),
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));
    const files = productionSourceFiles(fixtureRoot);

    assert.deepEqual(findForbiddenAngularSourceReferences(files, fixtureRoot), [
      `00-import.ts: ${forbiddenPackage}`,
      `01-export-star.ts: ${forbiddenPackage}`,
      `02-export-named.ts: ${forbiddenPackage}`,
      `03-ambient.d.ts: ${forbiddenPackage}`,
      `04-dynamic.ts: ${forbiddenPackage}`,
      `10-import-equals.ts: ${forbiddenPackage}`,
      `11-import-type-node.ts: ${forbiddenPackage}`,
      `13-require-resolve.ts: ${forbiddenPackage}`,
    ]);
    assert.deepEqual(findProductionHtmlModuleReferences(files, fixtureRoot), [
      '05-require-html.ts: ./template.html',
      '06-import-html.ts: ./template.html?raw',
      '07-export-html.ts: ./template.html',
      '08-dynamic-html.ts: ./template.html#asset',
      '09-ambient-html.d.ts: *.html',
      '12-import-type-html.ts: ./template.html?raw',
    ]);
  });
});

test('context-aware raw detectors ignore comments and ordinary strings', () => {
  withFixtureRoot((fixtureRoot) => {
    const forbiddenPackage = ['ang', 'ular'].join('');
    const forbiddenTool = ['ng', 'template-loader'].join('');
    const injectMarker = ['ng', 'Inject'].join('');
    const fixturePath = path.join(fixtureRoot, 'neutral.ts');
    writeFileSync(
      fixturePath,
      [
        `// angular.mock.reset(); scope.$digest(); ${angularServicesName};`,
        `const documentation = '${forbiddenPackage} ${forbiddenTool} ${injectMarker} $q $scope ${angularServicesName}';`,
      ].join('\n'),
    );

    assert.deepEqual(findLegacyFacadeReferences([fixturePath], fixtureRoot), []);
    assert.deepEqual(findForbiddenAngularSourceReferences([fixturePath], fixtureRoot), []);
    assert.deepEqual(findForbiddenAngularApiReferences([fixturePath], fixtureRoot), []);
    assert.deepEqual(findAngularDiMetadata([fixturePath], fixtureRoot), []);
    assert.deepEqual(findForbiddenAngularTestIdentifiers([fixturePath], fixtureRoot), []);
    assert.deepEqual(findForbiddenAngularBuildToolReferences([fixturePath], fixtureRoot), []);

    const loaderPath = path.join(fixtureRoot, 'webpack.config.js');
    writeFileSync(loaderPath, `module.exports = { module: { rules: [{ loader: '${forbiddenTool}' }] } };`);
    assert.deepEqual(findForbiddenAngularBuildToolReferences([loaderPath], fixtureRoot), [
      `webpack.config.js: ${forbiddenTool}`,
    ]);
  });
});

test('active build config discovery covers every representative toolchain', () => {
  const representativeFiles = [
    path.join(deckKayentaRoot, '.eslintrc.js'),
    path.join(deckKayentaRoot, 'jsconfig.json'),
    path.join(deckRoot, 'eslint.config.js'),
    path.join(deckRoot, 'jsconfig.json'),
    path.join(deckRoot, 'tsconfig.json'),
    path.join(deckRoot, 'build.gradle'),
    path.join(deckRoot, 'settings.gradle'),
    path.join(deckRoot, 'packages/app/webpack.config.js'),
    path.join(deckRoot, 'packages/app/vite.config.js'),
    path.join(deckRoot, 'packages/app/.babelrc'),
    path.join(deckRoot, 'packages/app/webpackImportMetaLoader.js'),
    path.join(deckRoot, 'packages/core/rollup.config.js'),
    path.join(deckRoot, 'packages/presentation/rollup.config.js'),
    path.join(deckRoot, 'packages/pluginsdk/rollup.config.js'),
    path.join(deckRoot, 'packages/pluginsdk/pluginconfig/rollup.config.js'),
    path.join(deckRoot, 'packages/pluginsdk/scripts/check-plugin/linters/lint.eslintrc.js'),
    path.join(deckRoot, 'packages/pluginsdk/scripts/check-plugin/linters/lint.rollup.config.js'),
    path.join(deckRoot, 'packages/pluginsdk/scaffold/rollup.config.js'),
    path.join(deckRoot, 'packages/pluginsdk/scaffold/scaffold-deck.gradle'),
    path.join(deckRoot, 'packages/scripts/config/rollup.config.base.js'),
    path.join(deckRoot, 'packages/scripts/helpers/rollup-node-auto-external-configurer.js'),
    path.join(deckRoot, 'packages/eslint-plugin/test.eslintrc'),
    path.join(deckRoot, 'test/functional/vite.config.ts'),
    path.join(deckRoot, 'test/functional/tsconfig.json'),
    path.join(deckRoot, 'karma.conf.js'),
    path.join(deckRoot, 'postcss.config.js'),
    path.join(repositoryRoot, 'deck-kayenta/rollup.config.js'),
    path.join(repositoryRoot, 'deck-kayenta/jest.config.js'),
    path.join(repositoryRoot, 'deck-kayenta/babel.config.js'),
    path.join(repositoryRoot, 'spinnaker-gradle-project/spinnaker-extensions/build.gradle.kts'),
  ];

  representativeFiles.forEach((file) =>
    assert.ok(activeBuildConfigFiles.includes(file), `Expected build config discovery to include ${file}`),
  );
  assert.deepEqual(activeBuildConfigFiles, [...activeBuildConfigFiles].sort());
});

test('build config discovery includes Babel, TypeScript, ESLint, Storybook, and Cypress configs', () => {
  withFixtureRoot((fixtureRoot) => {
    const storybookRoot = path.join(fixtureRoot, '.storybook');
    const cypressRoot = path.join(fixtureRoot, 'cypress');
    mkdirSync(storybookRoot);
    mkdirSync(cypressRoot);
    const expectedFiles = [
      path.join(fixtureRoot, '.babelrc'),
      path.join(fixtureRoot, '.babelrc.custom'),
      path.join(fixtureRoot, '.eslintrc.js'),
      path.join(storybookRoot, 'main.ts'),
      path.join(cypressRoot, 'tsconfig.json'),
      path.join(fixtureRoot, 'cypress.config.ts'),
      path.join(fixtureRoot, 'eslint.config.mjs'),
      path.join(fixtureRoot, 'jsconfig.json'),
      path.join(fixtureRoot, 'lint.rollup.config.js'),
      path.join(fixtureRoot, 'test.eslintrc'),
      path.join(fixtureRoot, 'tsconfig.base.json'),
    ].sort();
    [...expectedFiles, path.join(fixtureRoot, 'ordinary.json')].forEach((file) => writeFileSync(file, '{}'));

    assert.deepEqual(discoverActiveBuildConfigFiles([fixtureRoot]), expectedFiles);
  });
});

test('build config discovery includes Groovy and Kotlin Gradle scripts', () => {
  withFixtureRoot((fixtureRoot) => {
    const gradlePath = path.join(fixtureRoot, 'build.gradle');
    const kotlinGradlePath = path.join(fixtureRoot, 'build.gradle.kts');
    const neutralKotlinPath = path.join(fixtureRoot, 'source.kts');
    [gradlePath, kotlinGradlePath, neutralKotlinPath].forEach((file) => writeFileSync(file, 'fixture'));

    assert.deepEqual(discoverActiveBuildConfigFiles([fixtureRoot]), [gradlePath, kotlinGradlePath]);
  });
});

test('lexical residue detector rejects exact framework words without near-name false positives', () => {
  withFixtureRoot((fixtureRoot) => {
    const frameworkName = ['ang', 'ular'].join('');
    const frameworkRuntimeName = `${frameworkName}js`;
    const fixtures = new Map([
      ['00-comment.ts', `// ${frameworkName} runtime`],
      ['01-string.ts', `const description = '${frameworkRuntimeName} bootstrap';`],
      ['99-neutral.ts', 'const triangularity = true; const angularity = 1;'],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findActiveLexicalResidue(workspaceSourceFiles(fixtureRoot), fixtureRoot), [
      '00-comment.ts: forbidden framework word',
      '01-string.ts: forbidden framework word',
    ]);
  });
});

test('style detector rejects obsolete emitted selectors and directive stylesheet paths', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtures = new Map([
      ['00.directive.less', '.safe-class { color: red; }'],
      ['01-ng.less', '.ng-invalid[ng-model] { color: red; }'],
      ['02-ui-select.less', '.ui-select-container { display: block; }'],
      ['03-uib.less', '.uib-modal-window { display: block; }'],
      ['04-select2.less', '.select2-container { display: block; }'],
      ['05-custom-element.less', 'task-monitor .status { display: block; }'],
      [
        '99-neutral.less',
        [
          '/* .ng-invalid and task-monitor are historical prose. */',
          '.task-monitor, .select2fa, .nginx { display: block; }',
          '.message::after { content: "ng-invalid"; }',
        ].join('\n'),
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findForbiddenStyleContracts(activeStyleFiles([fixtureRoot]), fixtureRoot), [
      '00.directive.less: .directive.less path',
      '01-ng.less: ng-* selector',
      '02-ui-select.less: UI Select selector',
      '03-uib.less: UIB selector',
      '04-select2.less: Select2 selector',
      '05-custom-element.less: directive custom-element selector',
    ]);
  });
});

test('removed API detector rejects curated compatibility identifiers and contextual PromiseService methods', () => {
  withFixtureRoot((fixtureRoot) => {
    const dollarIdentifiers = ['$q', '$timeout', '$log', '$injector'];
    const removedIdentifiers = [
      'DirectProviderServiceDelegate',
      'PROVIDER_SERVICE_DELEGATE',
      'modalInstanceEmulation',
      'IModalServiceInstanceEmulation',
      'notifyAngular',
      'makeSortedStringFromAngularObject',
      'APPLICATION_INITIALIZERS_MODULE',
      'AUTHENTICATION_MODULE',
      'CORE_NOTIFICATION_NOTIFICATIONS_MODULE',
      'AMAZON_MODULE',
      'APPENGINE_MODULE',
      'AZURE_MODULE',
      'CLOUDRUN_MODULE',
      'DCOS_DCOS_MODULE',
      'DOCKER_MODULE',
      'ECS_MODULE',
      'GOOGLE_MODULE',
      'HUAWEICLOUD_MODULE',
      'ORACLE_MODULE',
      'TENCENTCLOUD_MODULE',
      'TENCENTCLOUD_REACT_MODULE',
      'TITUS_MODULE',
      'TITUS_REACT_MODULE',
    ];
    const fixtures = new Map([
      ...removedIdentifiers.map((identifier, index) => [
        `${String(index).padStart(2, '0')}-${identifier}.ts`,
        `void ${identifier};`,
      ]),
      ...dollarIdentifiers.map((identifier, index) => [
        `${String(index + removedIdentifiers.length).padStart(2, '0')}-dollar.ts`,
        `const ${identifier} = service;`,
      ]),
      ['40-promise-when.ts', 'promiseService.when(value);'],
      ['41-promise-notify.ts', 'nativePromiseService.notify(value);'],
      ['42-promise-defer.ts', 'this.promiseService.defer();'],
      ['43-created-promise.ts', 'createNativePromiseService().when(value);'],
      [
        '99-neutral.ts',
        [
          'void $state; void $stateParams; void $type; void $input; void item.$$hashKey;',
          'urlRouter.when(path, redirect); subject.notify(value); deferred.resolve(value);',
          `// ${removedIdentifiers.join(' ')}`,
          `const documentation = '${dollarIdentifiers.join(' ')}';`,
        ].join('\n'),
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findRemovedCompatibilityApis(productionSourceFiles(fixtureRoot), fixtureRoot), [
      ...removedIdentifiers.map(
        (identifier, index) => `${String(index).padStart(2, '0')}-${identifier}.ts: ${identifier}`,
      ),
      ...dollarIdentifiers.map(
        (identifier, index) => `${String(index + removedIdentifiers.length).padStart(2, '0')}-dollar.ts: ${identifier}`,
      ),
      '40-promise-when.ts: PromiseService.when',
      '41-promise-notify.ts: PromiseService.notify',
      '42-promise-defer.ts: PromiseService.defer',
      '43-created-promise.ts: PromiseService.when',
    ]);
  });
});

test('metadata detector rejects obsolete registry properties without matching prose or domain objects', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtures = new Map([
      [
        '00-stage-registration.ts',
        [
          'Registry.pipeline.registerStage({',
          "  templateUrl: './stage.html',",
          '  controller: StageController,',
          "  controllerAs: 'vm',",
          "  executionDetailsUrl: './details.html',",
          '});',
        ].join('\n'),
      ],
      ['01-state-registration.ts', "stateConfigProvider.addToRootState({ controllerAs: 'vm' });"],
      ['02-unrelated-stage.ts', "unrelated.registerStage({ executionDetailsUrl: './details.html' });"],
      ['03-stage-shape.ts', "const stage = { key: 'deploy', provides: 'deploy', templateUrl: './stage.html' };"],
      [
        '04-state-shape.ts',
        "const state = { name: 'details', url: '/details', views: {}, controller: DetailsController };",
      ],
      [
        '05-provider-registration.ts',
        "CloudProviderRegistry.registerProvider('fixture', { templateUrl: './provider.html', controller: ProviderController });",
      ],
      [
        '06-identifier-stage.ts',
        "const stageConfig = { templateUrl: './stage.html', controller: StageController };\nRegistry.pipeline.registerStage(stageConfig);",
      ],
      [
        '07-spread-stage.ts',
        "const legacyMetadata = { controllerAs: 'vm', executionDetailsUrl: './details.html' };\nconst stageConfig = { key: 'deploy', provides: 'deploy', ...legacyMetadata };\nRegistry.pipeline.registerStage(stageConfig);",
      ],
      ['08-unrelated-state.ts', 'domainRegistry.addState({ controller: DomainController });'],
      [
        '98-help-registry.ts',
        "HelpContentsRegistry.register('help.key', { templateUrl: './example.html', controller: ExampleController, controllerAs: 'example', executionDetailsUrl: '/example/details' });",
      ],
      [
        '99-neutral.ts',
        [
          "const templateRecord = { templateUrl: './document.html' };",
          'const ownerReference = { controller: DomainController };',
          "const aliasRecord = { controllerAs: 'owner' };",
          "const detailsLink = { executionDetailsUrl: '/domain/details' };",
          "const combinedDomainConfig = { templateUrl: './document.html', controller: DomainController, controllerAs: 'owner', executionDetailsUrl: '/domain/details', owner: 'team' };",
          'declare const externalConfig: DomainConfig;',
          "const documentation = 'templateUrl controller controllerAs executionDetailsUrl';",
          '// A registry config must not use templateUrl: or controller: properties.',
        ].join('\n'),
      ],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findObsoleteRegistryMetadata(productionSourceFiles(fixtureRoot), fixtureRoot), [
      '00-stage-registration.ts: templateUrl',
      '00-stage-registration.ts: controller',
      '00-stage-registration.ts: controllerAs',
      '00-stage-registration.ts: executionDetailsUrl',
      '01-state-registration.ts: controllerAs',
      '03-stage-shape.ts: templateUrl',
      '04-state-shape.ts: controller',
      '05-provider-registration.ts: templateUrl',
      '05-provider-registration.ts: controller',
      '06-identifier-stage.ts: templateUrl',
      '06-identifier-stage.ts: controller',
      '07-spread-stage.ts: controllerAs',
      '07-spread-stage.ts: executionDetailsUrl',
    ]);
  });
});

test('lifecycle detector rejects bare refresh subscriptions and accepts owned disposers', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtures = new Map([
      ['00-bare.ts', 'dataSource.onNextRefresh(refresh);'],
      ['01-parenthesized.ts', '(dataSource.onNextRefresh(refresh));'],
      ['02-void.ts', 'void dataSource.onNextRefresh(refresh);'],
      ['03-comma.ts', '(dataSource.onNextRefresh(refresh), cleanup);'],
      ['04-argument.ts', 'consume(dataSource.onNextRefresh(refresh));'],
      ['05-property-read.ts', 'dataSource.onNextRefresh(refresh).unsubscribe;'],
      ['06-unary.ts', '!dataSource.onNextRefresh(refresh);'],
      ['07-assigned-never.ts', 'const dispose = dataSource.onNextRefresh(refresh);'],
      ['08-property-never.ts', 'this.dispose = dataSource.onNextRefresh(refresh);'],
      ['09-ref-never.ts', 'disposeRef.current = dataSource.onNextRefresh(refresh);'],
      ['10-assignment.ts', 'const dispose = dataSource.onNextRefresh(refresh); dispose();'],
      ['11-return.ts', 'function subscribe() { return dataSource.onNextRefresh(refresh); }'],
      ['12-property.ts', 'this.dispose = dataSource.onNextRefresh(refresh); this.dispose?.();'],
      ['13-ref.ts', 'disposeRef.current = dataSource.onNextRefresh(refresh); disposeRef.current?.();'],
      ['14-wrapped-assignment.ts', 'const dispose = (dataSource.onNextRefresh(refresh)); dispose?.();'],
      ['15-arrow-return.ts', 'const subscribe = () => dataSource.onNextRefresh(refresh);'],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findOnNextRefreshUsage(productionSourceFiles(fixtureRoot), fixtureRoot), {
      callFiles: [...fixtures.keys()].sort(),
      findings: [
        '00-bare.ts: unowned onNextRefresh call',
        '01-parenthesized.ts: unowned onNextRefresh call',
        '02-void.ts: unowned onNextRefresh call',
        '03-comma.ts: unowned onNextRefresh call',
        '04-argument.ts: unowned onNextRefresh call',
        '05-property-read.ts: unowned onNextRefresh call',
        '06-unary.ts: unowned onNextRefresh call',
        '07-assigned-never.ts: onNextRefresh disposer dispose is never invoked',
        '08-property-never.ts: onNextRefresh disposer this.dispose is never invoked',
        '09-ref-never.ts: onNextRefresh disposer disposeRef.current is never invoked',
      ],
    });
  });
});
