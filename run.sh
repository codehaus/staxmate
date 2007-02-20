#!/bin/sh

java -Xmx16m -Xms16m -server\
 -cp lib/stax-api-1.0.1.jar:\
:lib/wstx-asl-3.2.0.jar:\
:lib/stax2-3.0pr1.jar:\
:build/classes \
$*
