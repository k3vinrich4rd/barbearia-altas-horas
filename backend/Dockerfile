# =====================================================================================================================
# Imagem da API da Barbearia Altas Horas (Fatia 8, D115; Fatia 8B, E3 e E4). Contexto de build: a pasta backend/.
#   docker build -t altas-horas-api backend
# Em duas etapas: o JDK 25 e o Maven só existem na etapa de build; a imagem final leva só o JRE 25 e o jar.
# Fatia 8B: a etapa final extrai o jar (-Djarmode=tools), roda uma vez no perfil "treino" (sem banco) e grava um cache
# AOT/CDS do JDK 25 que encurta a subida. Se o cache não casar, a JVM o ignora e sobe normal.
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
    && mv target/altas-horas-api-*-SNAPSHOT.jar /build/app.jar \
    && java -Djarmode=tools -jar /build/app.jar extract --destination /build/extraido

# ---- Etapa 2: runtime (JRE 25, usuário sem privilégio) ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S altashoras && adduser -S -G altashoras -H -s /sbin/nologin altashoras
WORKDIR /app
RUN chown altashoras:altashoras /app
# O jar já chega extraído (etapa de build, modo "tools" do Spring Boot): app/app.jar + app/lib/*.jar. O cache AOT só vale
# para o MESMO classpath, por isso a imagem executa a partir deste diretório, tanto no treino quanto em produção.
COPY --from=build --chown=altashoras:altashoras /build/extraido /app/app
USER altashoras

# 512 MB do Render free: heap de ~60% da memória, GC serial (poucos núcleos, menos memória extra) e metaspace limitado.
# Se faltar memória, o processo sai e o Render reinicia, em vez de ficar meio vivo.
# -Xms64m: com o heap inicial padrão (1/64 dos 512 MB) a JVM não carrega os objetos do cache AOT e aborta (testado).
# -XX:TieredStopAtLevel=1 (E5, D-5): só o compilador C1; medido em scripts/testar-imagem.sh (8B-A10): com 1 CPU lenta o C2
# compete com a aplicação na subida. O preço é um pico de desempenho menor, irrelevante para a agenda de um barbeiro.
ENV JAVA_OPTS="-Xms64m -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:+ExitOnOutOfMemoryError"

# Treino (E4): sobe o contexto do Spring no perfil "treino" (sem banco, valores falsos em application-treino.yml) e sai
# sozinho (spring.context.exit=onRefresh); a JVM grava o cache em /app/app.aot ao sair. O perfil treino nunca roda fora
# deste RUN. Se o treino falhar, o build segue sem cache (a imagem continua válida, só perde o ganho).
# Sem MaxMetaspaceSize (o limite de 128m impede a JVM de gravar o cache) e com -Xmx fixo, para a JVM do treino ter o mesmo
# modo de ponteiros comprimidos da JVM de produção (512 MB), mesmo que o build rode numa máquina com muita memória.
RUN java -XX:+UseSerialGC -Xmx300m -Xss512k -XX:AOTCacheOutput=/app/app.aot -Dspring.profiles.active=treino -Dspring.context.exit=onRefresh \
        -jar /app/app/app.jar > /tmp/treino.log 2>&1 \
    || { tail -40 /tmp/treino.log; echo "AVISO: treino falhou, imagem sem cache AOT"; rm -f /app/app.aot; }; \
    ls -l /app/app.aot || true

# prod é o padrão da imagem: o seed fictício (perfil dev) nunca chega a um servidor.
ENV SPRING_PROFILES_ACTIVE=prod
# Cache AOT (E3): se o arquivo faltar, estiver corrompido ou for de outra JVM, a JVM avisa e sobe sem ele.
# AOT_OPTS e JAVA_EXTRA existem para medir (scripts/testar-imagem.sh): AOT_OPTS vazio = sem cache.
ENV AOT_OPTS="-XX:AOTCache=/app/app.aot"
ENV JAVA_EXTRA=""
# O Render injeta PORT (padrão 10000); localmente, 8080.
ENV PORT=8080
EXPOSE 8080

# Sem curl na imagem alpine: o wget do busybox basta. Liveness (E7): só "o processo vive", não consulta o banco.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health/liveness" | grep -q '"status":"UP"' || exit 1

# Fallback (A8): uma sonda de 1 JVM confere se o cache é aceito. Cache de outra JVM ou heap pequeno demais fazem a JVM abortar
# em vez de ignorar o cache; se a sonda falhar, a API sobe sem cache e avisa no log.
# exec: o java vira o processo principal e recebe o SIGTERM do Render (desligamento limpo).
ENTRYPOINT ["sh", "-c", "if [ -n \"$AOT_OPTS\" ] && ! java $JAVA_OPTS $AOT_OPTS -cp /app/app/app.jar -version >/dev/null 2>&1; then echo 'AVISO: cache AOT invalido, subindo sem ele' >&2; AOT_OPTS=; fi; exec java $JAVA_OPTS $AOT_OPTS $JAVA_EXTRA -Dserver.port=${PORT} -jar /app/app/app.jar"]
