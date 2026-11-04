FROM node:24-alpine AS build
WORKDIR /web

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# As VITE_* entram no bundle na hora do build (o navegador nao le variavel de ambiente).
ARG VITE_API_URL=/api
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_LEGAL_COMPANY=Sentinela
ARG VITE_LEGAL_CONTACT_EMAIL=privacidade@sentinela.com.br
ENV VITE_API_URL=$VITE_API_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_LEGAL_COMPANY=$VITE_LEGAL_COMPANY \
    VITE_LEGAL_CONTACT_EMAIL=$VITE_LEGAL_CONTACT_EMAIL

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

# O nginx gera o default.conf a partir do template trocando ${API_UPSTREAM} na subida.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /web/dist /usr/share/nginx/html

EXPOSE 80
