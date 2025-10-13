@echo off
setlocal
cd /d %~dp0

if not exist .venv (
  py -m venv .venv
)

call .venv\Scripts\activate

REM Make sure we have wheel/setuptools to use prebuilt wheels
pip install --upgrade pip wheel setuptools

REM Install from wheels only (no source builds)
pip install --only-binary=:all: -r requirements.txt

uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
endlocal
