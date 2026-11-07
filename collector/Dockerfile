FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN groupadd --system collector && useradd --system --gid collector collector

COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir .

USER collector

ENTRYPOINT ["python", "-m", "collector"]
CMD ["--help"]
