FROM python:3.12-slim
RUN useradd --create-home --uid 10001 rago && mkdir /data && chown rago /data
WORKDIR /app
COPY rago_network ./rago_network
COPY cosmetics.json config.example.json ./
COPY config.example.json ./config.json
USER rago
VOLUME /data
EXPOSE 8080
ENV RAGO_NETWORK_CONFIG=/app/config.json
CMD ["python", "-m", "rago_network"]
