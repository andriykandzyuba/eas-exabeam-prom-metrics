# Stage 1: Build dependencies
FROM python:3.12-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

# Stage 2: Final runtime image
FROM python:3.12-slim
WORKDIR /app

# Run as a non-root user
RUN groupadd -r appuser && useradd -r -m -g appuser appuser
USER appuser

COPY --from=builder /root/.local /home/appuser/.local
COPY app ./app

ENV PATH=/home/appuser/.local/bin:$PATH \
    PYTHONUNBUFFERED=1

EXPOSE 8080

CMD ["python", "-u", "app/main.py"]
