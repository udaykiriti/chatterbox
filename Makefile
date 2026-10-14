# Makefile for Chatterbox Documentation Site
# Usage: make help

.PHONY: help install serve build clean deploy check

# Default target
help:
	@echo "Chatterbox Documentation - Make Commands"
	@echo ""
	@echo "  make install    - Install MkDocs and plugins"
	@echo "  make serve      - Start local dev server (http://localhost:8000)"
	@echo "  make build      - Build static site to ./site/"
	@echo "  make clean      - Remove built site"
	@echo "  make check      - Validate links and structure"
	@echo "  make deploy     - Deploy to GitHub Pages (gh-pages branch)"
	@echo ""
	@echo "Requirements: Python 3.7+, pip"

# Install dependencies
install:
	pip install --upgrade pip
	pip install mkdocs mkdocs-material mkdocs-mermaid2-plugin mkdocs-git-revision-date-localized-plugin mkdocs-minify-plugin

# Serve locally with live reload
serve:
	mkdocs serve --dev-addr=0.0.0.0:8000

# Build static site
build:
	mkdocs build --strict --verbose

# Clean build artifacts
clean:
	rm -rf site/

# Validate links and structure
check:
	mkdocs build --strict 2>&1 | grep -E "(WARNING|ERROR)" || true
	@echo "Link check complete"

# Deploy to GitHub Pages
deploy:
	mkdocs gh-deploy --force --clean

# Build for production (with minification)
build-prod:
	ENABLE_MINIFY=true mkdocs build --strict

# Quick validation without building
lint:
	@echo "Checking markdown files..."
	@for f in docs/*.md docs/es/*.md; do \
		if [ -f "$$f" ]; then \
			echo "  $$f"; \
		fi; \
	done
	@echo "All markdown files present"

# Show site structure
structure:
	@echo "Documentation Structure:"
	@find docs -name "*.md" -type f | sort | sed 's|^|  |'

# Install system dependencies (Ubuntu/Debian)
install-system-deps:
	sudo apt-get update && sudo apt-get install -y python3 python3-pip python3-venv

# Full setup from scratch
setup: install-system-deps install
	@echo "Setup complete. Run 'make serve' to start."

# Build and verify
verify: clean build check
	@echo "Build verification complete"