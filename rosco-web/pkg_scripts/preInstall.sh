#!/bin/sh

# backup install_packages.sh
if [ -f /opt/rosco/config/packer/install_packages.sh ]; then
	cp /opt/rosco/config/packer/install_packages.sh /opt/rosco/config/packer/install_packages.sh.orig
fi
