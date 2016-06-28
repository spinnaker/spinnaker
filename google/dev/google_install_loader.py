#!/usr/bin/python
#
# Copyright 2015 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""
Acts as a "bootloader" for setting up GCE instances from scratch.

If this is run as a startup script, it will extract files from the
instance metadata, then run another startup script. This makes it convienent
to write startup scripts that use existing modules that may span multiple
files so that setting up a Google Compute Engine instance (such as an image)
can use the same basic scripts and procedures as non-GCE instances. This
bootloader is specific to GCE in that it "bootloads" off GCE metadata. However,
once it does that (thus preparing the filesystem with the files that are needed
for the "real" startup script), it forks the specified standard script.
If additional GCE specific initialization is required, the standard script
can still conditionally perform that.

To use this as a bootloader:

  add a metadata entry for each file to attach. The metadata key is the encoded
      filename to extract to. Because '.' is not a valid metadata key char,
      the encoding is in the form ext_basename where the first '_' acts as
      the suffix. So ext_basename will be extracted as basename.ext.
      Additional underscores are left as is. A leading '_' (or no '_' at all)
      indicates no extension.
  example: in your gcloud command, add the flag
    --metadata-from-file py_setup=setup.py,yml_gate-local=gate-local.yml

  set the "startup_loader_files" metadata value to the keys of the attached
      files that should be extracted into /opt/spinnaker/install. This should
      be a '+'-delimited list of metadata keys.
  example: in your gcloud command, add the flag
    --metadata startup_loader_files='py_setup+yml_clouddriver-local+yml_gate-local'

  set the "startup_command" metadata value to the shell command to execute after
      the bootloader extracts the files. This can include commandline
      arguments.
  example: in your gcloud command, add the flag (or append to the metadata flag)
    --metadata startup_command='python+/opt/spinnaker/install/setup.py'
    The '+' characters will be converted to spaces.

  set the "startup-script" metadata key to google_install_loader.py
      This is most easily done by adding to the '--metadata-from-file' flag.

  A full example of a command:
  gcloud compute instances create $INSTANCE_NAME \
  --project $MY_PROJECT --zone $ZONE --image $IMAGE_NAME \
  --image-project $IMAGE_PROJECT --machine-type n1-highmem-8 \
  --scopes "compute-rw,storage-rw" \
  --metadata startup_loader_files='py_setup+yml_clouddriver-local+yml_gate-local',startup_command='python+/opt/spinnaker/install/setup.py' \
  --metadata-from-file py_setup=setup.py,yml_clouddriver-local=clouddriver-local.yml,yml_gate-local=gate-local.yml,startup-script=spinnaker/google/dev/google_install_loader.py

The attached files will be extracted to /opt/spinnaker/install.
The attached file metadata and startup command will be cleared,
and the startup-script will be rewritten to a generated
/opt/spinnaker/install/startup_script.py that calls the specified command.
"""
import os
import shutil
import socket
import subprocess
import sys
import urllib2


GOOGLE_METADATA_URL = 'http://metadata.google.internal/computeMetadata/v1'
GOOGLE_INSTANCE_METADATA_URL = '{url}/instance'.format(url=GOOGLE_METADATA_URL)
_MY_ZONE = None


def fetch(url, google=False):
    request = urllib2.Request(url)
    if google:
      request.add_header('Metadata-Flavor', 'Google')
    try:
      response = urllib2.urlopen(request)
      return response.getcode(), response.read()
    except urllib2.HTTPError as e:
      return e.code, str(e.reason)
    except urllib2.URLError as e:
      return -1, str(e.reason)


def get_zone():
    global _MY_ZONE
    if _MY_ZONE != None:
        return _MY_ZONE
    code, output = fetch('{url}/zone'.format(url=GOOGLE_INSTANCE_METADATA_URL),
                         google=True)
    if code == 200:
       _MY_ZONE = os.path.basename(output)
    else:
       _MY_ZONE = ''
    return _MY_ZONE


def running_on_gce():
    return get_zone() != ''


def get_instance_metadata_attribute(name):
    code, output = fetch(
        '{url}/attributes/{name}'.format(url=GOOGLE_INSTANCE_METADATA_URL,
                                         name=name),
        google=True)
    if code == 200:
        return output
    else:
        return None


def clear_metadata_to_file(name, path):
    value = get_instance_metadata_attribute(name)
    if value != None:
      with open(os.path.join('/opt/spinnaker/install', path), 'w') as f:
        f.write(value)
      clear_instance_metadata(name)


def clear_instance_metadata(name):
    p = subprocess.Popen('gcloud compute instances remove-metadata'
                         ' {hostname} --zone={zone} --keys={name}'
                         .format(hostname=socket.gethostname(),
                                 zone=get_zone(),
                                 name=name),
                         shell=True, close_fds=True)
    if p.wait():
        raise SystemExit('Unexpected failure clearing metadata.')


def write_instance_metadata(name, value):
    p = subprocess.Popen('gcloud compute instances add-metadata'
                         ' {hostname} --zone={zone} --metadata={name}={value}'
                         .format(hostname=socket.gethostname(),
                                 zone=get_zone(),
                                 name=name, value=value),
                         shell=True, close_fds=True)
    if p.wait():
        raise SystemExit('Unexpected failure writing metadata.')


def unpack_files(key_list):
    """Args unpack and clear the specified keys into their corresponding files.

      Key names correspond to file names using the following encoding:
         '.' is not permitted in a metadata name, so we'll use a leading
         underscore separator in the file name to indicate the extension.
         a value in the form "ext_base_name" means the file "base_name.ext"
         a value in the form "_ext_base_name" means the file "ext_base_name"
         a value in the form "basename" means the file "basename"

    Args: key_list a list of strings denoting the metadata keys contianing the
       file content.
    """

    for key in key_list:
      underscore = key.find('_')
      if underscore <= 0:
          filename = key if underscore < 0 else key[1:]
      else:
        filename = '{basename}.{ext}'.format(
            basename = key[underscore + 1:],
            ext=key[:underscore])
      clear_metadata_to_file(key, filename)


def __unpack_and_run():
    """Unpack the files from metadata, and run the main script.

    This is intended to be used where a startup script needs a bunch
    of different files for a startup script that is passed through metadata
    in a GCE instance.

    The actual startup acts like a bootloader that unpacks all the
    files from the metadata, then passes control the specific startup script
    for the installation.

    The bootloader unpacks the files mentioned in the 'startup_loader_files'
    metadata, which is a space-delimited list of other metadata keys that
    contain the files. Because the keys cannot contain '.', we encode the
    filenames as <ext>_<basename> using the leading '_' to separate the
    extension, whichi s added as a prefix.

    The true startup script is denoted by the 'startup_command' attribute,
    which specifies the name of a python script to run (presumably packed
    into the startup_loader_files). The script uses the unencoded name once
    unpacked. The python command itself is ommited and will be added here.
    """

    script_keys = get_instance_metadata_attribute('startup_loader_files')
    key_list = script_keys.split('+') if script_keys else []
    unpack_files(key_list)
    if script_keys:
      clear_instance_metadata('startup_loader_files')

    startup_command = get_instance_metadata_attribute('startup_command')
    if not startup_command:
        sys.stderr.write('No "startup_command" metadata key.\n')
        raise SystemExit('No "startup_command" metadata key.')

    # Change the startup script to the final command that we run
    # so that future boots will just run that command. And take down
    # the rest of the boostrap metadata since we dont need it anymore.
    command = ('chmod gou+rx /opt/spinnaker/install/*.sh; '
               + startup_command.replace('+', ' '))

    with open('__startup_script__.sh', 'w') as f:
        f.write('#!/bin/bash\ncd /opt/spinnaker/install\n{command}\n'
                .format(command=command))

    os.chmod('__startup_script__.sh', 0555)
    write_instance_metadata('startup-script',
                            '/opt/spinnaker/install/__startup_script__.sh')
    clear_instance_metadata('startup_command')

    # Now run the command (which is also the future startup script).
    p = subprocess.Popen(command, shell=True, close_fds=True)
    p.wait()
    return p.returncode


if __name__ == '__main__':
    if not running_on_gce():
        sys.stderr.write('You do not appear to be on Google Compute Engine.\n')
        sys.exit(-1)

    try:
      os.makedirs('/opt/spinnaker/install')
      os.chdir('/opt/spinnaker/install')
    except OSError as e:
      sys.stderr.write('Startup mkdir failed: {e}'.format(e=e))

    # Copy this script to /opt/spinnaker/install as install_loader.py
    # since other scripts will reference it that way.
    try :
      shutil.copyfile('/var/run/google.startup.script',
                      '/opt/spinnaker/install/google_install_loader.py')
    except IOError as e:
      sys.stderr.write('Startup script copy failed: {e}'.format(e=e))

    sys.exit(__unpack_and_run())
