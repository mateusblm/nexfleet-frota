# NexFleet — Frota (MS1, MongoDB Atlas)

Microsserviço de veículos, motoristas e vínculos motorista ↔ veículo do NexFleet, plataforma de gestão de frotas. Parte da v1.0.0; o sistema completo está no repositório guarda-chuva [nexfleet](https://github.com/mateusblm/nexfleet).

## Arquitetura

Frota é o **MS1** e persiste no **MongoDB Atlas** (coleções `veiculos`, `motoristas` e `vinculos`). Na inicialização garante índices únicos de placa, CPF e CNH e um índice parcial que permite **um só vínculo ativo por veículo**. Atende o BFF (cadastros e `GET /aggregated-data`) e Operações, que valida o vínculo antes de registrar um abastecimento (`GET /api/v1/frota/vinculos/validacao-operacao`). Rotas em `/api/v1/frota/{veiculos,motoristas,vinculos}`, protegidas pelo JWT da Identidade.

```text
Navegador → shell + microfrontends (Static Web Apps)
          → Azure API Management (APIM) → Gateway → BFF
              ├─ MS1 Frota ........ MongoDB Atlas
              ├─ MS2 Operações .... Azure SQL (schema operacoes)
              ├─ Identidade, Indicadores ... Azure SQL (um schema e um login por serviço)
              └─ Azure Function HTTP calcular-eficiencia
Operações → outbox → Service Bus (nuvem) / RabbitMQ (local) → Indicadores + Function (notificação)
```

**Clean Architecture + Vertical Slice.** Fatias em `br.com.nexfleet.frota`, cada uma com `api`, `application` e `infrastructure` (adaptadores MongoDB):

| Fatia | Responsabilidade |
|---|---|
| `gerenciarveiculos` | cadastrar, editar, consultar, inativar e excluir veículos |
| `gerenciarmotoristas` | cadastrar, editar, consultar e excluir motoristas (CPF e CNH únicos) |
| `gerenciarvinculos` | vincular, encerrar e validar vínculo para operação |
| `consultarservico` | informações do serviço |

Domínio em `veiculo/domain` e `motorista/domain`; pacotes técnicos `infrastructure` (índices MongoDB), `config` e `observability`.

**Testes:** unitários dos casos de uso e da API (ex.: `GerenciarVinculosTest`, `VeiculosHttpTest`, `VeiculosInjecaoHttpTest`), de integração com MongoDB real em Testcontainers (`MongoVeiculosIT`, `MongoVinculosIT`, `FrotaMongoIT`) e de arquitetura com ArchUnit (`ArchitectureTest`: MongoDB só em `infrastructure`; domínio e aplicação sem framework).

## Tecnologias

- Java 21, Spring Boot 4.1.1 (Web MVC, Security, OAuth2 Resource Server, Data MongoDB, Actuator)
- MongoDB (Atlas na nuvem; `mongo:7.0` local e nos testes)
- springdoc-openapi 3.2.0 (OpenAPI em `/v3/api-docs`)
- Testcontainers (MongoDB)
- Micrometer + Prometheus
- JUnit 5, ArchUnit 1.4.1
- Maven Wrapper (Maven 3.9.16)
- Docker (`eclipse-temurin:21`), GitHub Actions (testes em todo push; imagem no Docker Hub em tag `v*`)

## Como rodar localmente

```bash
./mvnw -B -ntp verify                                # unitários, ArchUnit e ITs (Testcontainers; exige Docker)
docker build -t mateusblm/nexfleet-frota:local .     # imagem (porta 8080)
```

Variáveis de ambiente: `PORT`, `MONGODB_URI`, `MONGODB_DATABASE`, `IDENTIDADE_URL` e `SPRING_PROFILES_ACTIVE=jwt-rsa`. Sem MongoDB, o readiness responde `503`.

**Ambiente completo** (todos os serviços, SQL Server, MongoDB e RabbitMQ locais) sobe pelo repositório guarda-chuva [nexfleet](https://github.com/mateusblm/nexfleet), com os repositórios clonados lado a lado (`scripts/clonar-tudo.sh`):

```bash
scripts/iniciar-local.sh                                     # 1ª vez: gera o .env e a chave RSA local
docker compose -f compose.yaml -f compose.build.yaml up -d --build
scripts/seed-demo.sh                                         # dados de demonstração
```

Sem o override `compose.build.yaml`, o `docker compose up -d` usa as imagens publicadas no Docker Hub.

## URLs na nuvem

Frota não tem URL pública direta: é acessada só pelo APIM (via Gateway e BFF). O OpenAPI aparece na Swagger UI abaixo, opção "Frota".

Imagem Docker: [`mateusblm/nexfleet-frota:v1.0.0`](https://hub.docker.com/r/mateusblm/nexfleet-frota)

| Recurso | Endereço |
|---|---|
| Frontend | https://white-mushroom-0cadda010.3.azurestaticapps.net |
| API (APIM) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net |
| Swagger UI (BFF, Frota, Operações, Identidade, Indicadores) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net/swagger-ui.html |
| Dados agregados (exige token) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net/aggregated-data |
| AsyncAPI dos eventos | https://white-mushroom-0cadda010.3.azurestaticapps.net/docs/eventos/ |
| Repositório guarda-chuva | https://github.com/mateusblm/nexfleet |

> **Partida a frio:** os Container Apps escalam a zero e o Azure SQL pausa sem uso. A primeira chamada após um período de inatividade pode levar de 1 a 2 minutos (ou responder 503/504); tente de novo em seguida.

## Equipe

- Vinícius dos Reis Góes
- Mateus Burlamaqui
- Lucas Roussenq
- Erik Carvalho
