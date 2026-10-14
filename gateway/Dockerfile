# Chatterbox Gateway Dockerfile
# Build: docker build -t chatterbox-gateway .
# Run: docker run -d -p 6667:6667 --env-file .env chatterbox-gateway

FROM python:3.11-slim

# Install runtime deps and security updates
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy gateway
COPY server.py /app/server.py

# Non-root user
RUN useradd -r -u 1000 -s /bin/false gateway && chown gateway:gateway /app/server.py
USER gateway

# Security: drop capabilities (handled by docker run --cap-drop=ALL)
# Security: read-only root filesystem (handled by docker run --read-only)

EXPOSE 6667

# Environment defaults (override with --env-file or -e)
ENV GATEWAY_LISTEN_HOST=0.0.0.0 \
    GATEWAY_LISTEN_PORT=6667 \
    GATEWAY_IRC_HOST=irc.libera.chat \
    GATEWAY_IRC_PORT=6697 \
    GATEWAY_RATE_LIMIT=10 \
    GATEWAY_MAX_CONN_PER_IP=10 \
    GATEWAY_LOG_LEVEL=INFO \
    GATEWAY_TLS_VERIFY=true \
    GATEWAY_MIN_PASSWORD_LEN=16

# GATEWAY_PASSWORD MUST be provided at runtime via --env-file or -e
# Example .env file:
# GATEWAY_PASSWORD=your-secure-random-password-here

ENTRYPOINT ["python3", "/app/server.py"]