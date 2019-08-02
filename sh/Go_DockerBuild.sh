#!/bin/sh
###
set -e
#
sudo docker version
#
INAME="naese"
CNAME="${INAME}_container"
#
###
# Build image from Dockerfile.
sudo docker build -t ${INAME} .
#
set -x
sudo docker images
#
