#!/bin/bash

ID=63190203

# check if the user has provided a phase
if (( $# == 0 )); then
    echo "Usage: $0 <phase>"
    exit 1
fi

# create a directory called prev23 and copy everything into it
find . -name '*Zone.Identifier' -delete
rm -rf prev23/
mkdir -p prev23
cp -r * prev23/

# remove unnecessary files
rm prev23/zipit.sh
rm -rf prev23/prev23/
find prev23/ -name '*.zip' -delete

# run make clean
make -C prev23/ clean

# clean antlr, test files, git files
rm -rf prev23/prg/*.p23
rm prev23/lib/antlr-4.11.1-complete.jar
rm -rf prev23/.git*

# zip it
zip -r "${ID}-$1.zip" prev23/

# remove prev23 directory
rm -rf prev23/

echo "Exported as: ${ID}-$1.zip"
