# vLLM Smoke Test

This directory contains a smoke test script for the vLLM OpenAI-compatible API.

## Setup

Create a Python virtual environment and install dependencies:

```bash
cd deploy/llm
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Usage

### With vLLM running locally:

```bash
# Using defaults (localhost:8000, google/gemma-4-26B-A4B-it)
python smoke-test.py

# Or specify custom URL and model
python smoke-test.py --base-url http://localhost:8000/v1 --model google/gemma-4-26B-A4B-it

# Or via environment variables
LLM_BASE_URL=http://localhost:8000/v1 LLM_MODEL=google/gemma-4-26B-A4B-it python smoke-test.py
```

### With Docker Compose:

```bash
# Start vLLM
docker-compose up -d

# Wait for server to be ready (should start within 30-60 seconds)
sleep 30

# Run smoke test
python smoke-test.py

# Stop when done
docker-compose down
```

## Test Coverage

The smoke test verifies:

1. **Health Check**: `/v1/models` endpoint responds correctly
2. **Chat Completion**: Basic message round-trip works
3. **Tool Call**: Tool/function calling works (required for the Interview feature)

## Exit Codes

- `0`: All tests passed
- `1`: One or more tests failed
