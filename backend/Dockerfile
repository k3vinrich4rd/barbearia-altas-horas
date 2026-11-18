# =====================================================================================================================
# Imagem da API da Barbearia Altas Horas (Fatia 8, D115). Contexto de build: a pasta backend/.
#   docker build -t altas-horas-api backend
# Em duas etapas: o JDK 25 e o Maven só existem na etapa de build; a imagem final leva só o JRE 25 e o jar.
# Nenhum segredo entra na imagem: tudo vem de variáveis de ambiente na hora de rodar (veja docs/sdd/fatia-8-deploy.md).
# =====================================================================================================================

# ---- Etapa 1: build (JDK 25 + Maven Wrapper) ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Primeiro só o que define as dependências: se o código mudar mas o pom.xml não, esta camada vem do cache.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B -ntp dependency:go-offline

COPY src src
# Os testes rodam no CI (precisam de Docker para o Testcontainers); aqui só empacota.
RUN sh mvnw -B -ntp -o -DskipTests package \
    && mv target/altas-horas-api-*-SNAPSHOT.jar /build/app.jar

# ---- Etapa 2: runtime (JRE 25, usuário sem privilégio) ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S altashoras && adduser -S -G altashoras -H -s /sbin/nologin altashoras
WORKDIR /app
COPY --from=build --chown=altashoras:altashoras /build/app.jar app.jar
USER altashoras

# prod é o padrão da imagem: o seed fictício (perfil dev) nunca chega a um servidor.
ENV SPRING_PROFILES_ACTIVE=prod
# 512 MB do Render free: heap de ~60% da memória, GC serial (poucos núcleos, menos memória extra) e metaspace limitado.
# Se faltar memória, o processo sai e o Render reinicia, em vez de ficar meio vivo.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:+ExitOnOutOfMemoryError"
# O Render injeta PORT (padrão 10000); localmente, 8080.
ENV PORT=8080
EXPOSE 8080

# Sem curl na imagem alpine: o wget do busybox basta. start-period cobre a subida (Flyway + jobs).
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# exec: o java vira o processo principal e recebe o SIGTERM do Render (desligamento limpo).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]
