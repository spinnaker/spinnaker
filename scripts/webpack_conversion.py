import os
import sys
import re

root = sys.argv[1]

def getModules():
  modules = {}
  for r, subfolders, files in os.walk(root):
    for filename in files:
      path = os.path.join(r, filename)
      with open(path) as f:
        for line in f:
          moduleDeclaration = re.search(r"\.module\('([^\[]+)',", line)
          if moduleDeclaration:
            modules[moduleDeclaration.groups()[0]] = path
            break

  return modules

def replaceDependenciesWithRequire(modules):
  for r, subfolders, files in os.walk(root):
    for filename in files:
      path = os.path.join(r, filename)
      with open(path, 'r+') as f:
        inDependenciesBlock = False
        lines = f.readlines()
        f.seek(0)
        f.truncate()
        for line in lines:
          newline = line
          if inDependenciesBlock:
            dependencyBlockEnd = re.search(r"\]", line)
            if dependencyBlockEnd:
              inDependenciesBlock = False
            else:
              spinnakerDependency = re.search(r"'(spinnaker\..+)'", line)
              if spinnakerDependency:
                lookup = modules.get(spinnakerDependency.groups()[0])
                if lookup:
                  relativePath = os.path.relpath(lookup, path)[3:]
                  if relativePath[0:3] != '../':
                    relativePath = "./%s" % relativePath
                  newline = re.sub(r"'.+'", "require('%s')" % relativePath, line)
          else:
            moduleDeclaration = re.search(r"\.module\('([^\[]+)',", line)
            if moduleDeclaration:
              inDependenciesBlock = True
          f.write(newline)

def replaceExternalDependenciesWithRequire():
  modules = {
    'angulartics': "require('angulartics')",
    'angulartics.google.analytics': "require('angulartics')",
    'ngAnimate': "require('angular-animate')",
    'ngSanitize': "require('angular-sanitize')",
    'ui.bootstrap': "require('angular-bootstrap')",
    'ui.select': "require('ui-select')",
    'restangular': "require('restangular')",
    'angularSpinner': "require('angular-spinner')",
    'angular.filter': "require('angular-filter')",
    'infinite-scroll': "require('ng-infinite-scroll')",
  }

  for r, subfolders, files in os.walk(root):
    for filename in files:
      path = os.path.join(r, filename)
      with open(path, 'r+') as f:
        lines = f.readlines()
        f.seek(0)
        f.truncate()
        for line in lines:
          newline = line
          for key in modules.keys():
            dependency = re.search("'(%s)'" % key, line)
            if dependency:
              required = re.search('require', line)
              if not required:
                newline = re.sub("'%s'" % key, modules[dependency.groups()[0]], line)
              break
          f.write(newline)

replaceDependenciesWithRequire(getModules())
replaceExternalDependenciesWithRequire()
