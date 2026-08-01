# vLLM Configuration Reference

This document describes the configuration tunables available for the vLLM deployment.

## Docker Compose Configuration

The main configuration file is `docker-compose.yml`. Key parameters are:

### Model Selection

```yaml
command: >
  python -m vllm.entrypoints.openai.api_server
    --model google/gemma-4-26B-A4B-it
    ...
```

Replace `google/gemma-4-26B-A4B-it` with any Hugging Face model ID.

### Quantization

Controls memory usage and inference speed:

| Option | Memory | Speed | Quality | Use Case |
|--------|--------|-------|---------|----------|
| `aqlm` | Lowest | Fast | Good | Single GPU, high throughput |
| `awq` | Low | Fast | Very Good | Single GPU, best quality |
| `gguf` | Low | Variable | Good | Portability, CPU fallback |
| `none` | High | Fast | Best | Multi-GPU, max quality |

Example:
```yaml
command: >
  python -m vllm.entrypoints.openai.api_server
    --model google/gemma-4-26B-A4B-it
    --quantization aqlm
    ...
```

### Context Length (max-model-len)

Controls maximum token count for inputs and outputs:

```yaml
command: >
  python -m vllm.entrypoints.openai.api_server
    --model google/gemma-4-26B-A4B-it
    --max-model-len 32768
    ...
```

Gemma-4-26B supports up to 32768 tokens. Reduce this if you see OOM errors:

```yaml
--max-model-len 16384  # Half context, half memory
```

### Tensor Parallelism

For multi-GPU setups:

```yaml
command: >
  python -m vllm.entrypoints.openai.api_server
    --model google/gemma-4-26B-A4B-it
    --tensor-parallel-size 2
    ...
```

Set to the number of available GPUs. For single GPU, keep at `1`.

### Data Type (dtype)

Controls numerical precision:

| Option | Memory | Speed | Notes |
|--------|--------|-------|-------|
| `half` | Low | Fast | FP16, recommended default |
| `bfloat16` | Low | Fast | Better numerical stability |
| `auto` | Variable | Variable | Auto-select based on hardware |

```yaml
command: >
  python -m vllm.entrypoints.openai.api_server
    --model google/gemma-4-26B-A4B-it
    --dtype half
    ...
```

## Complete Recommended Configuration

For a single NVIDIA GPU (24GB+):

```yaml
vllm:
  image: vllm/vllm-openai:latest
  runtime: nvidia
  ports:
    - "8000:8000"
  environment:
    - HUGGINGFACE_HUB_CACHE=/data
  volumes:
    - vllm-data:/data
  command: >
    python -m vllm.entrypoints.openai.api_server
      --model google/gemma-4-26B-A4B-it
      --quantization aqlm
      --max-model-len 32768
      --tensor-parallel-size 1
      --dtype half
```

## Memory Planning

Gemma-4-26B requires approximately:

| Quantization | VRAM Required | Notes |
|--------------|---------------|-------|
| none (FP16) | ~52 GB | Full precision |
| awq | ~26 GB | Weight-only |
| aqlm | ~18 GB | Full quantization |
| gguf (Q4_K_M) | ~15 GB | 4-bit quantization |

For single-GPU setups, use `aqlm` or `gguf` quantization.
