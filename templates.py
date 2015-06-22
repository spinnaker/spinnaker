import os
import sys
import re

root = sys.argv[1]
s = 'scripts'
v = 'views'

for r, subfolders, files in os.walk(root):
  for filename in files:
    path = os.path.join(r, filename)
    with open(path, 'r+') as f:
      lines = f.readlines()
      f.seek(0)
      f.truncate()
      for line in lines:
        newline = line
        template = re.search("templateUrl:", line)
        if template:
          address = re.search("'(.+)'", line)
          filePath = '/%s' % path
          viewPath = '/%s' % address.groups()[0]
          viewPath = os.path.relpath(viewPath, filePath)
          if viewPath[0:6] == '../../':
            viewPath = viewPath[3:]
          else:
            viewPath = viewPath[1:]
          newline = re.sub('templateUrl', 'template', line)
          newline = re.sub("'.+'", "require('%s')" % viewPath, newline)
        f.write(newline)




