#!/usr/bin/env bash

set -e

sbt +clean
sbt +compile
sbt +test
sbt +macrosJS/publishSigned +macrosJVM/publishSigned +coreJS/publishSigned +coreJVM/publishSigned ++2.12.8 plugin/publishSigned sonatypeRelease