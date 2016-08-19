#! /usr/bin/python2.7

import argparse
from interface import Console
from clouddriver import Clouddriver

def main():
    parser = argparse.ArgumentParser(description="Generate Spinnaker Config")
    parser.add_argument("--clouddriver", action="store_true",
            help="Configure clouddriver")
    parser.add_argument("--account_file", 
            default="clouddriver/accounts/clouddriver.yaml",
            help="path to account file.")

    args = parser.parse_args()

    try:
        if args.clouddriver:
            cd = Clouddriver(args.account_file)
            cd.configure()
    except EOFError:
        Console.warn("\nNo unsaved changes stored, goodbye.")
    except KeyboardInterrupt:
        Console.warn("\nNo unsaved changes stored, goodbye.")

if __name__ == "__main__":
    main()
