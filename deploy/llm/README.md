# vLLM Deployment for Gemma-4

This directory contains configuration for running vLLM with the `google/gemma-4-26B-A4B-it` model behind an OpenAI-compatible API.

## Stop the other vLLM first

> **This host runs one vLLM at a time.** GPU and system memory are a single 121 GB pool, and
> each server claims its share at startup. Starting this one while another is up will OOM
> the machine — which is why `qwen-coder.service` and `vllm.service` carry `Conflicts=` in
> systemd. Docker Compose knows nothing about those units, so the check is yours:

```bash
systemctl is-active qwen-coder vllm     # expect: inactive
sudo systemctl stop qwen-coder          # if it is running
```

Note that `qwen-coder` is what runs the agent loop, so no agent run can be in flight while
this model is up.

## Requirements

- Docker and Docker Compose
- NVIDIA Container Toolkit (for GPU acceleration)
- **aarch64 host** — the compose file uses NVIDIA's NGC image (`nvcr.io/nvidia/vllm`)
  because the stock `vllm/vllm-openai` images do not cover this platform

## Quick Start

```bash
# Start the vLLM server
docker-compose up -d

# Check logs
docker-compose logs -f

# Stop the server
docker-compose down
```

## Configuration Tunables

The following parameters can be adjusted in `docker-compose.yml`:

| Parameter | Description | Typical Values |
|-----------|-------------|----------------|
| `--quantization` | Quantization method for memory efficiency | `aqlm`, `awq`, `gguf`, `none` |
| `--max-model-len` | Maximum context length in tokens | 32768 (default for Gemma-4-26B) |
| `--tensor-parallel-size` | Number of GPUs for parallel inference | 1 (single GPU), 2+ (multi-GPU) |
| `--dtype` | Data type for inference | `half` (FP16), `bfloat16`, `auto` |

### Quantization Options

- **aqlm**: Activations and weights quantized (best for single-GPU)
- **awq**: Weights only quantized (slightly higher quality)
- **gguf**: GGUF format from llama.cpp (portability)
- **none**: Full FP16 precision (highest quality, most memory)

### Context Length

Gemma-4-26B supports up to 32768 tokens. Reduce `--max-model-len` if you encounter OOM errors.

### Concurrency

vLLM automatically handles multiple concurrent requests. For high-concurrency workloads:

1. Increase `--max-model-len` only as needed
2. Use quantization (`aqlm` or `awq`)
3. For multi-GPU, increase `--tensor-parallel-size`

## API Endpoint

The OpenAI-compatible API is available at:

```
http://localhost:8000/v1
```

### Health Check

```bash
curl http://localhost:8000/v1/models
```

### Chat Completion Example

```bash
curl http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "google/gemma-4-26B-A4B-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

## App Configuration

Update your Spring Boot application's `application.yml`:

```yaml
baibyname:
  llm:
    base-url: http://localhost:8000/v1
    model-name: google/gemma-4-26B-A4B-it
    api-key: ""  # Optional, for authenticated endpoints
    timeout-ms: 30000
```

## Using a Rented GPU Provider

To use a cloud GPU provider (OpenAI, Anyscale, etc.):

1. Update `base-url` to the provider's endpoint
2. Add your API key to `baibyname.llm.api-key`
3. Update `model-name` if the provider uses a different model identifier

Example for a generic OpenAI-compatible endpoint:

```yaml
baibyname:
  llm:
    base-url: https://api.openai.com/v1  # or your provider's URL
    model-name: gpt-4  # or provider-specific model ID
    api-key: ${LLM_API_KEY}  # use environment variable
    timeout-ms: 30000
```

## Troubleshooting

### OOM Errors

Reduce memory usage by:
- Using quantization (`aqlm` or `awq`)
- Reducing `--max-model-len`
- Reducing `--tensor-parallel-size` (ensure it doesn't exceed GPU count)

### Container Won't Start

Check NVIDIA runtime:
```bash
docker run --rm --runtime=nvidia --gpus all nvidia/cuda:12.2.0-base-ubuntu22.04 nvidia-smi
```

If this fails, install [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html).
