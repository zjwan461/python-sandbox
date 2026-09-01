import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel
from infer_detect import init_model, release_model, detect as detect_fn

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("服务启动, 开始加载模型...")
    init_model()
    yield
    logger.info("服务关闭, 开始释放资源...")
    release_model()


app = FastAPI(title="Code Danger Detect Service", lifespan=lifespan)


class DetectReq(BaseModel):
    code: str


@app.post("/detect")
def detect_endpoint(req: DetectReq):
    pred = detect_fn(req.code)
    label = "DANGEROUS" if "DANGEROUS" in pred else "SAFE"
    return {"label": label, "raw_output": pred}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
