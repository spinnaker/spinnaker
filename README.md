Rosco
=====
[![Build Status](https://api.travis-ci.org/spinnaker/rosco.svg?branch=master)](https://travis-ci.org/spinnaker/rosco)

A bakery for use by Spinnaker to produce machine images.

It presently supports producing Google Compute Engine images and AWS amis. It relies on packer and can be easily extended to support additional platforms.

It exposes a REST api which can be experimented with via the Swagger UI: http://localhost:8087/swagger/index.html
