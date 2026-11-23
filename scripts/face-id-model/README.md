# 아이 얼굴 구분 모델 (face-ID model)

앱이 '아이 얼굴 구분'을 켤 때 내려받는 파일을 만드는 방법입니다. 결정 배경은 `DECISIONS.md`의
"아이 얼굴 구분" 항목을 보세요.

| 항목 | 값 |
| --- | --- |
| 원본 | OpenCV Zoo `models/face_recognition_sface/face_recognition_sface_2021dec.onnx` (SFace, Apache-2.0) |
| 결과 | `bebecup-face-id-sface-v1.tflite` — NHWC 1×112×112×3 RGB 0..255 → 1×128, int8 가중치(dynamic range) |
| 크기 | 9,865,200 바이트 |
| SHA-256 | `0fd54bf710145b33bd43963443fd1c4c658a998cf8ac86d7d387d6d908697ff6` |
| 배포 | GitHub 릴리스 `face-id-model-v1`(prerelease, latest 아님 — 앱 업데이트 확인의 `releases/latest`를 건드리지 않음) |

크기·SHA-256은 `FaceIdModelStore`에 고정돼 있어, 파일을 바꾸면 앱은 받지 않습니다. 새 파일을 내려면
`MODEL_VERSION`·파일 이름·태그를 함께 올리고(예전 얼굴 특징값과는 비교할 수 없음), 릴리스에 `LICENSE`와
변경 내용(`NOTICE.txt`)을 같이 올립니다.

## 다시 만들기

onnx2tf는 이 PC(Windows)에서 `ai_edge_litert`가 없어 돌지 않았습니다. 네트워크가 Conv/PReLU 27단 직렬이라
`convert_sface.py`가 ONNX 가중치를 Keras 층에 그대로 옮겨 TFLite로 내보내고 ONNX Runtime 결과와 비교합니다.

```bash
python -m venv venv && venv/Scripts/python -m pip install "tensorflow==2.19.0" "tf-keras==2.19.0" "onnx==1.17.0" onnxruntime onnxsim
curl -L -o sface.onnx https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx
venv/Scripts/python -c "import onnx;from onnx import numpy_helper as h;m=onnx.load('sface.onnx');n={i.name for i in m.graph.initializer};k=[i for i in m.graph.input if i.name not in n];del m.graph.input[:];m.graph.input.extend(k);onnx.save(m,'sface_clean.onnx')"
venv/Scripts/onnxsim sface_clean.onnx sface_sim.onnx
venv/Scripts/python -I convert_sface.py sface_sim.onnx out/   # out/sface_drq.tflite = 배포 파일
```

2026-11-23 결과: float32는 ONNX와 코사인 1.0, int8은 무작위 입력에서 0.984~0.990, 실제 얼굴(LFW)에서 0.997 이상.
에뮬레이터에서 LFW 3명 24장으로 앱 전체 흐름(ML Kit 눈·입 위치 → 정렬 → 이 모델)을 돌렸을 때 같은 사람 0.53~,
다른 사람 최대 0.39로 나뉘어 세 그룹으로 정확히 묶였습니다. 아기·닮은 형제자매는 실제 사진으로 확인하지 못했습니다.
