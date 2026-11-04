# Deploy de produção com HTTPS

O `docker-compose.yaml` serve para desenvolvimento local. Para publicar o sistema,
use o override de produção, que remove a porta pública do Nginx e coloca o Caddy
na frente da aplicação. O Caddy solicita e renova certificados TLS automaticamente.

1. Configure `SITE_DOMAIN` e `FRONTEND_URL=https://<seu-dominio>` no `.env`.
2. Aponte o DNS do domínio para o servidor e libere as portas TCP 80/443 e UDP 443.
3. Suba os serviços com:

   ```sh
   docker compose -f docker-compose.yaml -f docker-compose.prod.yaml up -d --build
   ```

Não publique diretamente as portas 8080 (API) ou 5432 (PostgreSQL). O Compose base
já as limita ao loopback; o override também deixa o Nginx acessível somente pela
rede interna do Compose.

O override usa a diretiva `!reset` do Docker Compose 2.24 ou superior. O IP do Caddy
na rede privada é fixo para o Nginx confiar no cabeçalho de cliente apenas desse proxy.
