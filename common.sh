#! /bin/bash

function setup_home () {
  eclipse_home=$(dirname $(readlink -f $(which eclipse)))
  if [ -z ${eclipse_home} ]; then
    echo "Failed to locate the Eclipse Home directory."
    exit 0
  fi
  osgiJar=`find ${eclipse_home} -name "org.eclipse.osgi_*.jar"`
}

function setup_jar () {
  p2qJar=$(find /usr/share/java/ /opt/ $HOME/git/org.fedoraproject.p2query -name "org.fedoraproject.p2query*.jar" 2>/dev/null | head -1)
  fpp2Jar=$(find /usr/share/java/ /opt/ $HOME/git/fedoraproject-p2 -name "org.fedoraproject.p2-*.jar" 2>/dev/null | head -1)
  if [ -z ${p2qJar} ]; then
    echo "Failed to locate the p2 Query Jar."
    exit 0
  fi
}
