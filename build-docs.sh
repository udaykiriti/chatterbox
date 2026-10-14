#!/bin/bash
# Build script for Chatterbox documentation site
# Usage: ./build-docs.sh [serve|build|clean]

set -e

case "${1:-build}" in
    serve)
        echo "Starting MkDocs dev server at http://localhost:8000"
        mkdocs serve --dev-addr=0.0.0.0:8000
        ;;
    build)
        echo "Building documentation site..."
        mkdocs build
        echo "Site built in ./site/"
        ;;
    clean)
        echo "Cleaning build artifacts..."
        rm -rf site/
        ;;
    deploy)
        echo "Deploying to GitHub Pages..."
        mkdocs gh-deploy --force
        ;;
    *)
        echo "Usage: $0 [serve|build|clean|deploy]"
        exit 1
        ;;
esac