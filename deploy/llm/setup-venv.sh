#!/bin/bash
# Setup script for vLLM smoke test virtual environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Setting up Python virtual environment..."

# Create virtual environment if it doesn't exist
if [ ! -d ".venv" ]; then
    python3 -m venv .venv
fi

# Activate and install dependencies
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

echo "Virtual environment setup complete!"
echo ""
echo "Run the smoke test with:"
echo "  source .venv/bin/activate"
echo "  python smoke-test.py"
