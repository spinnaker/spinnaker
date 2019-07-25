# Spinnaker IntelliJ IDEA files

IntelliJ IDEA will modify some of these files from their checked-in versions when the project is
opened. To work around this, the Spinnaker Gradle plugin will mark these files in Git as "assume
unchanged", telling Git to ignore any local changes. If you want to commit changes to these files,
you will need to undo that.

```bash
$ git update-index --no-assume-unchanged $FILENAME
``` 
