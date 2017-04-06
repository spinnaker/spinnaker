#!/usr/bin/env python

import argparse
import base64
import json
import logging
import subprocess
import sys


def authenticate(token):
    subprocess.check_call(["vault", "auth", token])
    log.info("Successfully authenticated against the vault server")

def read_secret(address, name):
    try:
        secret_data = json.loads(subprocess.check_output(["vault", "read", 
            "-address", address, 
            "-format", "json",
            "secret/spinnaker/{}".format(name)])
        )
    except Error as err:
        log.critical("Failed to load secret {name}: {err}".format(
            name=name,
            err=err)
        )
        sys.exit(1)

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

    authenticate(args.token)
    config_mount = read_secret(args.address, args.secret)

    for config in config_mount["configs"]:
        secret_id = "{secret}/{name}".format(secret=args.secret, name=config)
        mount = read_secret(args.address, secret_id)

        file_name = mount["file"]
        contents = base64.b64decode(mount["contents"])
        with open(file_name, "w") as f:
            logging.info("Writing config to {}".format(file_name))

            f.write(contents)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
