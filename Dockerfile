# Stage 1: Build dependencies
FROM python:3.12-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

# Stage 2: Final runtime image
FROM python:3.12-slim
WORKDIR /app

# Create and switch to a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser

# Copy installed dependencies and source code
COPY --from=builder /root/.local /home/appuser/.local
COPY app ./app

ENV PATH=/home/appuser/.local/bin:$PATH

EXPOSE 8000

CMD ["python", "-u", "app/main.py"]
