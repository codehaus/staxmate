#!/bin/sh

java -Xmx16m -Xms16m -server\
 -cp lib/stax-api-1.0.1.jar:\
:lib/wstx/wstx-asl-3.2.6.jar:\
:lib/stax2-2.1.jar:\
:build/classes \
$*
