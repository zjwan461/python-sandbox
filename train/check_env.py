import torch
from transformers.utils.import_utils import is_torch_bf16_gpu_available

print("GPU算力:", torch.cuda.get_device_capability())
print("is_torch_bf16_gpu_available():", is_torch_bf16_gpu_available())
