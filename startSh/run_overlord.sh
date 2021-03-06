#!/usr/bin/env bash
set +e
set +u
shopt -s xpg_echo
shopt -s expand_aliases
trap "exit 1" 1 2 3 15

SCRIPT_DIR=`dirname $0`
MAVEN_DIR="${SCRIPT_DIR}/extensions-repo"
SERVER_TYPE=overlord

if [ "x${SERVER_TYPE}" = "x" ]
then
  echo "usage: $0 server-type" >& 2
  exit 2
fi

if [[ ! -d "${SCRIPT_DIR}/lib" || ! -d "${SCRIPT_DIR}/config" ]]; then
  echo "This script appears to be running from the source location. It must be run from its deployed location."
  echo "After building, unpack services/target/druid-services-*-SNAPSHOT-bin.tar.gz, and run the script unpacked there."
  exit 2
fi

CURR_DIR=`pwd`
cd ${SCRIPT_DIR}
SCRIPT_DIR=`pwd`
cd ${CURR_DIR}

# start process
#JAVA_ARGS="${JAVA_ARGS} -Xmx512m -Duser.timezone=UTC -Dfile.encoding=UTF-8"
#JAVA_ARGS="${JAVA_ARGS} -Ddruid.extensions.localRepository=${MAVEN_DIR}"
JAVA_ARGS="${JAVA_ARGS} -server"
JAVA_ARGS="${JAVA_ARGS} -Xmx4g -Xms4g -XX:NewSize=256m -XX:MaxNewSize=256m -XX:+UseConcMarkSweepGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
JAVA_ARGS="${JAVA_ARGS} -Duser.timezone=utc -Dfile.encoding=UTF-8"
JAVA_ARGS="${JAVA_ARGS} -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -Djava.io.tmpdir=/tmp/druid"

DRUID_CP="${SCRIPT_DIR}/config/_common"
DRUID_CP="${DRUID_CP}:${SCRIPT_DIR}/config/$SERVER_TYPE"
DRUID_CP="${DRUID_CP}:${SCRIPT_DIR}/lib/*"

exec java ${JAVA_ARGS} -classpath "${DRUID_CP}" io.druid.cli.Main server "$SERVER_TYPE"
