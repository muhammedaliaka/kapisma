#!/bin/bash
# Canli sunucuyu bozmadan test: server -> server_test kopyasi (dunya/log haric), ayri port, guncel jar.
cd "$(dirname "$0")/.."
mkdir -p server_test
robocopy server server_test /E /XD world logs crash-reports /NFL /NDL /NJH /NJS /NP > /dev/null
sed -i 's/^server-port=.*/server-port=25570/;s/^query.port=.*/query.port=25570/' server_test/server.properties
cp mod/kapisma/build/libs/kapisma-0.1.0.jar server_test/mods/ 2>/dev/null
ls server_test/mods
