FROM node:26-alpine
WORKDIR /app
COPY tools/local_http_proxy.cjs tools/local_http_proxy.cjs
COPY frontend frontend
ENV HOST=0.0.0.0 PORT=8125
USER node
EXPOSE 8125
CMD ["node", "tools/local_http_proxy.cjs"]
