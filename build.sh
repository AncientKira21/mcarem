#!/bin/zsh

# Ensure the builds directory exists
mkdir -p "/Users/ancientkira/Desktop/Desktop - George’s MacBook Pro/mca_workshop/builds"

# Ask for the version number
echo -n "Version number (e.g., 8.1.11) 💻🔥: "
read mod_version

# Pack the archive with the custom version string
cd "/Users/ancientkira/Desktop/Desktop - George’s MacBook Pro/mca_workshop/source"
echo "Building the mod... 🚀✨"
zip -r "../builds/mcarem-neoforge-${mod_version}+26.1.2.jar" .
cd -

echo "🎉 Successfully built mcarem-neoforge-${mod_version}+26.1.2.jar straight into builds! 🎉"
