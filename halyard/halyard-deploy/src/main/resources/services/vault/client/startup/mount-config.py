#!/usr/bin/env python

import argparse
import base64
import json
import logging
import subprocess
import os
import grp
import pwd
import time

def mkdirs(path):
    subprocess.check_call(["mkdir", "-p", path])


def subprocess_retry(supplier, retries, process_cmd):
    if retries < 0:
        logging.fatal("All retries of " + process_cmd + " attempted")
        raise subprocess.CalledProcessError(process_cmd)
    try:
        return supplier()
    except subprocess.CalledProcessError as err:
        logging.warning("Subprocess failed " + str(err))
        time.sleep(5)
        subprocess_retry(supplier, retries - 1, process_cmd)

def authenticate(address, token):
    process = ["vault", "auth", "-address", address, token]
    subprocess_retry(lambda: subprocess.check_call(process), 5, ' '.join(process))
    logging.info("Successfully authenticated against the vault server")

def read_secret(address, name):
    process = ["vault", "read",
        "-address", address,
        "-format", "json",
        "secret/{}".format(name)]
    secret_data = subprocess_retry(lambda: json.loads(subprocess.check_output(process)
    ), 5, ' '.join(process))

    logging.info("Retrieved secret {name} with request_id {rid}".format(
        name=name,
        rid=secret_data["request_id"])
    )

    warning = secret_data.get("warnings", None)

    if not warning is None:
        logging.warning("Warning: {}".format(warning))

    return secret_data["data"]

def main():
    parser = argparse.ArgumentParser(
            description="Download secrets for Spinnaker stored by Halyard"
    )

    parser.add_argument("--token",
            type=str,
            help="Vault token for authentication.",
            required=True
    )

    parser.add_argument("--address", 
            type=str, 
            help="Vault server's address.",
            required=True
    )

    parser.add_argument("--secret", 
            type=str, 
            help="The secret name this instance config can be found in.",
            required=True
    )

    args = parser.parse_args()

    authenticate(args.address, args.token)
    config_mount = read_secret(args.address, args.secret)

    spinnaker_user = pwd.getpwnam("spinnaker").pw_uid
    spinnaker_group = grp.getgrnam("spinnaker").gr_gid

    for config in config_mount["configs"]:
        secret_id = "{name}".format(name=config)
        mount = read_secret(args.address, secret_id)

        file_name = mount["file"]
        contents = base64.b64decode(mount["contents"])

        dir_name = os.path.dirname(file_name)
        if not os.path.isdir(dir_name):
            mkdirs(dir_name)

        os.chown(dir_name, spinnaker_user, spinnaker_group)

        with open(file_name, "w") as f:
            logging.info("Writing config to {}".format(file_name))

            f.write(contents)

        os.chown(dir_name, spinnaker_user, spinnaker_group)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
