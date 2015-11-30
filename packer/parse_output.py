#!/usr/bin/env python

import sys
import re
import pprint
import os

try:
    import json
except ImportError, e:
    print "Missing json module.  Install with: sudo pip install json"
    print "If you don't have pip, do this first: sudo easy_install pip"
    exit(2)


builders = [ 'hvm_builder' ] #might add pv_builds later... not sure.

md_header="""---
layout: toc-page
title: AMI ID table
id: ami_table
lang: en
---

* Table of contents. This line is required to start the list.
{:toc}

# AMI ID Table


| Region         | Name                     | Ubuntu Version | Instance Type | AMI ID       |
|----------------|--------------------------|----------------|---------------|--------------|
"""

def main(argv):
    if len(sys.argv) < 3:
        print "ERROR: You did not give me the Jenkins output file.\n\tusage: " + sys.argv[0] + " <jenkinsoutputfilelocation> <wheretoputtheartifacts>\n"
        exit(1)

    jenkins_output_file = sys.argv[1]
    artifact_file_location = sys.argv[2]
    build_number = sys.argv[3]
    ubuntu_version = sys.argv[4]

    if not os.path.isfile(jenkins_output_file):
        print "ERROR: Jenkins output file does not exist (" + jenkins_output_file + ").\n\tusage: " + sys.argv[0] + " <jenkinsoutputfilelocation> <wheretoputtheartifacts> <build_number> <ubuntu_version>\n"
        exit(1)

    if not os.path.isdir(artifact_file_location):
        print "ERROR: Artifact directory given does not exist (" + artifact_file_location + ").\n\tusage: " + sys.argv[0] + " <jenkinsoutputfilelocation> <wheretoputtheartifacts> <build_number> <ubuntu_version>\n"
        exit(1)


    pp = pprint.PrettyPrinter(indent=4)
    amis = {}

    artifact_file_md = "ami_table.md"
    artifact_file_json = "ami_table.json"

    fo = open(jenkins_output_file, 'r')

    for line in fo:
        for builder in builders:
            if re.match('^hvm', builder):
                instance_type = 'HVM'
            else:
                instance_type = 'PV'

            if instance_type not in amis:
                amis[instance_type] = {}

            string_to_search_for = ".*" + builder + ': AMIs were created:'

            if re.match(string_to_search_for, line):
                temp_line = re.sub(string_to_search_for, '', line)

                data = temp_line.split("\\n")
                for block in data:
                    if block != '':
                        block = re.sub('\s+', '', block)

                        ( region, ami_id ) = block.split(':')

                        amis[instance_type][region] = ami_id

    fo.close()

    name = 'Spinnaker-Ubuntu-' + ubuntu_version + '-' + build_number
    ubuntu_version = '14.04 LTS'
    amazon_console_prefix = 'https://console.aws.amazon.com/ec2/home?region='


    md_fo = open(artifact_file_location + '/' + artifact_file_md, 'w+')

    md_fo.write(md_header)

    for instance_type in amis:
        for region in amis[instance_type]:
            ami_id = amis[instance_type][region]

            line_to_write = '|' + region + '|' + name + '|' + ubuntu_version + '|' + instance_type + '|' + '[' + ami_id + '](' + amazon_console_prefix + region + '#launchAmi=' + ami_id + ') |\n'
            md_fo.write(line_to_write)

    md_fo.close()

    json_fo = open(artifact_file_location + '/' + artifact_file_json, 'w+')

    json_fo.write(json.dumps(amis, indent=4, sort_keys=True))

    json_fo.close()


if __name__ == "__main__":
    main(sys.argv)
