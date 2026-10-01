#!/usr/bin/env node

const { execFile, execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const process = require('node:process');
const { promisify } = require('node:util');

const execFileAsync = promisify(execFile);
const sharedViteConfigPath = path.resolve(__dirname, 'config', 'vite.config.base.module.js');

const preserveSubprocessDiagnostics = (error) => {
  const diagnostics = [error.stdout, error.stderr]
    .filter((output) => typeof output === 'string' && output.trim())
    .map((output) => output.trimEnd())
    .filter((output) => !error.message.includes(output));
  if (diagnostics.length) error.message = [error.message, ...diagnostics].join('\n');
  return error;
};

const withCallerNodeEnvironment = async (callback) => {
  const callerNodeEnvironment = process.env.NODE_ENV;
  if (!callerNodeEnvironment) process.env.NODE_ENV = 'development';
  try {
    return await callback();
  } finally {
    if (callerNodeEnvironment === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = callerNodeEnvironment;
  }
};

const resolveViteConfigPath = (file, cwd = process.cwd()) => {
  if (file) {
    const explicitConfigPath = path.resolve(cwd, file);
    if (!fs.existsSync(explicitConfigPath)) throw new Error(`Could not find ${explicitConfigPath}`);
    return explicitConfigPath;
  }

  const localConfigPath = path.resolve(cwd, 'vite.config.js');
  return fs.existsSync(localConfigPath) ? localConfigPath : sharedViteConfigPath;
};

const runDeclarations = (cwd = process.cwd(), execute = execFileAsync) => {
  const typescriptBin = require.resolve('typescript/bin/tsc', { paths: [cwd] });
  return execute(process.execPath, [typescriptBin, '--emitDeclarationOnly'], { cwd }).catch((error) => {
    throw preserveSubprocessDiagnostics(error);
  });
};

const runYalcPush = (cwd = process.cwd(), execute = execFileSync) => {
  const yalcBin = require.resolve('yalc/src/yalc.js', { paths: [__dirname] });
  return execute(process.execPath, [yalcBin, 'push'], { cwd });
};

const resolveOutputDirectories = (resolvedConfig) => {
  const { outDir, rollupOptions } = resolvedConfig.build;
  const outputOptions = rollupOptions.output
    ? Array.isArray(rollupOptions.output)
      ? rollupOptions.output
      : [rollupOptions.output]
    : [{}];
  const outputDirectories = outputOptions.map((output) => {
    const outputDirectory = output?.dir ?? outDir;
    if (!output || typeof output !== 'object' || Array.isArray(output) || typeof outputDirectory !== 'string') {
      throw new Error(`Refusing to clean unsafe Vite output directory: ${String(outputDirectory)}`);
    }
    return path.resolve(resolvedConfig.root, outputDirectory);
  });
  return [...new Set(outputDirectories)];
};

const isStrictDescendant = (parent, child) => {
  const relativePath = path.relative(parent, child);
  return (
    Boolean(relativePath) &&
    relativePath !== '..' &&
    !relativePath.startsWith(`..${path.sep}`) &&
    !path.isAbsolute(relativePath)
  );
};

const pathsOverlap = (first, second) =>
  first === second || isStrictDescendant(first, second) || isStrictDescendant(second, first);

const hasSymlinkComponent = (packageRoot, target) => {
  let current = packageRoot;
  for (const segment of path.relative(packageRoot, target).split(path.sep)) {
    current = path.join(current, segment);
    try {
      if (fs.lstatSync(current).isSymbolicLink()) return true;
    } catch (error) {
      if (error.code === 'ENOENT') return false;
      throw error;
    }
  }
  return false;
};

const collectInputPaths = (input, root, paths = []) => {
  if (typeof input === 'string' && input && !input.startsWith('\0')) {
    paths.push(path.resolve(root, input));
  } else if (Array.isArray(input)) {
    input.forEach((value) => collectInputPaths(value, root, paths));
  } else if (input && typeof input === 'object') {
    Object.values(input).forEach((value) => collectInputPaths(value, root, paths));
  }
  return paths;
};

const createHandlers = (dependencies = {}) => {
  const cwd = dependencies.cwd ?? process.cwd();
  const loadVite = dependencies.loadVite ?? (() => import('vite'));
  const resolveConfigPath = dependencies.resolveViteConfigPath ?? resolveViteConfigPath;
  const reportError = dependencies.reportError ?? console.error;
  const emitDeclarations = dependencies.runDeclarations ?? (() => runDeclarations(cwd));
  const runDeclarationJob = (resolvedConfig) =>
    Promise.resolve()
      .then(() => emitDeclarations())
      .then(() => resolvedConfig.spinnaker?.afterDeclarations?.(resolvedConfig))
      .catch((error) => {
        throw preserveSubprocessDiagnostics(error);
      });
  const pushPackage = dependencies.runYalcPush ?? (() => runYalcPush(cwd));
  const scheduleZeroDelay =
    dependencies.scheduleZeroDelay ??
    ((callback) => {
      const timer = setTimeout(callback, 0);
      return () => clearTimeout(timer);
    });
  const schedulePush =
    dependencies.schedulePush ??
    ((callback) => {
      let cancelSecondTurn;
      const cancelFirstTurn = scheduleZeroDelay(() => {
        cancelSecondTurn = scheduleZeroDelay(callback);
      });
      return () => {
        cancelFirstTurn();
        cancelSecondTurn?.();
      };
    });
  const setExitCode =
    dependencies.setExitCode ??
    ((code) => {
      process.exitCode = code;
    });
  const signalSource = dependencies.signalSource ?? process;
  const cleanOutput = async (resolvedConfig, configFile) => {
    const packageRoot = fs.realpathSync(cwd);
    const outputDirectories = resolveOutputDirectories(resolvedConfig);
    const buildInputs = collectInputPaths(
      [resolvedConfig.build.lib?.entry, resolvedConfig.build.rollupOptions.input],
      resolvedConfig.root,
    );
    const protectedInputs = [
      path.join(packageRoot, 'src'),
      path.join(packageRoot, 'package.json'),
      path.join(packageRoot, 'tsconfig.json'),
      configFile,
      resolvedConfig.configFile,
      ...(resolvedConfig.configFileDependencies ?? []),
      ...buildInputs,
    ]
      .filter(Boolean)
      .map((input) => path.resolve(input));

    for (const outputDirectory of outputDirectories) {
      if (
        !isStrictDescendant(packageRoot, outputDirectory) ||
        outputDirectory === path.resolve(resolvedConfig.root) ||
        hasSymlinkComponent(packageRoot, outputDirectory) ||
        protectedInputs.some((input) => pathsOverlap(outputDirectory, input))
      ) {
        throw new Error(`Refusing to clean unsafe Vite output directory: ${outputDirectory}`);
      }
    }
    for (const outputDirectory of outputDirectories) {
      await fs.promises.rm(outputDirectory, { force: true, recursive: true });
    }
    for (const outputDirectory of outputDirectories) {
      await fs.promises.mkdir(outputDirectory, { recursive: true });
    }
  };

  const buildHandler = async ({ file }) => {
    const configFile = resolveConfigPath(file, cwd);
    await withCallerNodeEnvironment(async () => {
      const vite = await loadVite();
      const resolvedConfig = await vite.resolveConfig({ configFile }, 'build', 'production', 'production');
      await cleanOutput(resolvedConfig, configFile);
      await Promise.all([vite.build({ configFile, build: { emptyOutDir: false } }), runDeclarationJob(resolvedConfig)]);
    });
  };

  const startHandler = async ({ file, push }) => {
    const configFile = resolveConfigPath(file, cwd);
    const { resolvedConfig, watcher } = await withCallerNodeEnvironment(async () => {
      const vite = await loadVite();
      const resolvedConfig = await vite.resolveConfig({ configFile }, 'build', 'production', 'production');
      await cleanOutput(resolvedConfig, configFile);
      const watcher = await vite.build({ configFile, build: { emptyOutDir: false, watch: {} } });
      return { resolvedConfig, watcher };
    });
    const closeViteWatcher = watcher.close.bind(watcher);
    let currentCycle;
    let latestGeneration = 0;
    let declarationQueue = Promise.resolve();
    let cancelPendingPush;
    let closing;

    const clearPendingPush = () => {
      cancelPendingPush?.();
      cancelPendingPush = undefined;
    };
    const invalidateGeneration = () => {
      latestGeneration++;
      clearPendingPush();
    };

    watcher.on('change', invalidateGeneration);
    watcher.on('restart', invalidateGeneration);

    watcher.on('event', (event) => {
      if (event.code === 'START') {
        clearPendingPush();
        const cycle = { bundleSucceeded: false, generation: ++latestGeneration };
        const declarationJob = declarationQueue.then(() => runDeclarationJob(resolvedConfig));
        cycle.declarations = declarationJob.then(
          () => true,
          (error) => {
            reportError(error);
            return false;
          },
        );
        declarationQueue = cycle.declarations.then(() => undefined);
        currentCycle = cycle;
        return;
      }
      if (event.code === 'BUNDLE_END' && currentCycle) {
        currentCycle.bundleSucceeded = true;
        return;
      }
      if (event.code === 'ERROR') {
        if (currentCycle) currentCycle.bundleSucceeded = false;
        reportError(event.error);
        return;
      }
      if (event.code === 'END' && currentCycle) {
        const completedCycle = currentCycle;
        currentCycle = undefined;
        void completedCycle.declarations.then((declarationsSucceeded) => {
          if (
            !push ||
            closing ||
            completedCycle.generation !== latestGeneration ||
            !completedCycle.bundleSucceeded ||
            !declarationsSucceeded
          ) {
            return;
          }
          cancelPendingPush = schedulePush(async () => {
            cancelPendingPush = undefined;
            if (closing || completedCycle.generation !== latestGeneration) return;
            try {
              await pushPackage();
            } catch (error) {
              reportError(error);
            }
          });
        });
      }
    });

    const closeWatcher = (signal) => {
      if (closing) return closing;
      clearPendingPush();
      signalSource.removeListener('SIGINT', handleSigint);
      signalSource.removeListener('SIGTERM', handleSigterm);
      closing = Promise.resolve()
        .then(() => closeViteWatcher())
        .then(
          () => {
            if (signal) setExitCode(signal === 'SIGINT' ? 130 : 143);
          },
          (error) => {
            reportError(error);
            setExitCode(1);
            throw error;
          },
        );
      return closing;
    };
    const handleSigint = () => void closeWatcher('SIGINT').catch(() => undefined);
    const handleSigterm = () => void closeWatcher('SIGTERM').catch(() => undefined);
    signalSource.once('SIGINT', handleSigint);
    signalSource.once('SIGTERM', handleSigterm);
    watcher.close = () => closeWatcher();

    return watcher;
  };

  return { buildHandler, startHandler };
};

const fileOption = {
  alias: 'f',
  describe: 'custom Vite config file',
  type: 'string',
};

const pushOption = {
  alias: 'p',
  default: false,
  type: 'boolean',
};

const runCli = (argv = process.argv.slice(2), handlers = createHandlers()) =>
  require('yargs/yargs')(argv)
    .scriptName('spinnaker-scripts')
    .command(
      'start',
      'Builds your package in watch mode',
      { file: fileOption, push: pushOption },
      handlers.startHandler,
    )
    .command('build', 'Builds your package', { file: fileOption }, handlers.buildHandler)
    .help()
    .demandCommand(1)
    .exitProcess(false)
    .fail((message, error) => {
      throw error || new Error(message);
    })
    .parseAsync();

if (require.main === module) {
  void runCli().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}

module.exports = {
  createHandlers,
  resolveViteConfigPath,
  runCli,
  runDeclarations,
  runYalcPush,
};
