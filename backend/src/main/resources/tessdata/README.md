# OCR 语言包

- `eng.traineddata`、`chi_sim.traineddata` 来自
  [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)(Apache-2.0)。
- 应用启动时会把这两个文件复制到系统临时目录 `careerflow-ocr-tessdata`，
  供 Tess4J 调用本地 Tesseract 原生库使用。
- 如果只需要英文 OCR，可以删除 `chi_sim.traineddata`，应用会自动降级为 `eng`。
