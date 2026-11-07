FROM python:3.12-slim
RUN useradd --create-home --uid 10001 rago && mkdir /data && chown rago /data
WORKDIR /app
# the PostgreSQL driver, used when DATABASE_URL is set (with its own libpq, nothing else to install)
RUN pip install --no-cache-dir "psycopg[binary]==3.2.3"
COPY rago_network ./rago_network
COPY cosmetics.json catalogue.json config.example.json ./
COPY config.example.json ./config.json
USER rago
VOLUME /data
EXPOSE 8080
ENV RAGO_NETWORK_CONFIG=/app/config.json
CMD ["python", "-m", "rago_network"]
