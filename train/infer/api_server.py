from fastapi import FastAPI
from pydantic import BaseModel
from infer_detect import detect

app = FastAPI(title="Code Danger Detect Service")
JARVIS_CODER = "zjwan461/jarvis-coder"


class DetectReq(BaseModel):
    code: str


@app.post("/detect")
def detect(req: DetectReq):
    pred = detect(req.code)
    label = "DANGEROUS" if "DANGEROUS" in pred else "SAFE"
    return {"label": label, "raw_output": pred}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
