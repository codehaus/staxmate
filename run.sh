#!/bin/sh

java -Xmx16m -Xms16m -server\
 -cp lib/stax-api-1.0.1.jar:\
:lib/wstx/woodstox-core-asl-3.9.9-1.jar:\
:lib/stax2-api-2.9.9-1.jar:\
:build/classes \
$*
