#!/bin/bash
# User-level toolchain environment (extracted via apt-get download + dpkg -x)
export TC=/home/z/my-project/toolchain
export JAVA_HOME=$TC/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$TC/usr/bin:$JAVA_HOME/bin:$PATH
