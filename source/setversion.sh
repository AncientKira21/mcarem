
#!/bin/bash

export NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then

    echo "Usage: ./setversion.sh <version> (e.g., ./setversion.sh 8.1.14)"

    exit 1

fi

python3 -c '

import re, os

v = os.environ.get("NEW_VERSION")

with open("build.gradle", "r") as f:

    c = f.read()

nc = re.sub(r"def mod_version = \"[^\"]+\"", f"def mod_version = \"{v}\"", c)

with open("build.gradle", "w") as f:

    f.write(nc)

'

echo "Mod version successfully updated to $NEW_VERSION."

