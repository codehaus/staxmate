#!/bin/sh

java -Xmx16m -Xms16m -server\
 -cp lib/stax-api-1.0.1.jar:\
:lib/wstx-asl-3.0.0.jar:\
:build/classes \
$*
