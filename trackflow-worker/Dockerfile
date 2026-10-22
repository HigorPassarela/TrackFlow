# Worker image (design D16): the JRE plus ffmpeg, running as an unprivileged user.
# Build from the repository root, which holds the parent POM and the contracts module:
#   docker build -f trackflow-worker/Dockerfile -t trackflow-worker .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY trackflow-contracts/ trackflow-contracts/
COPY trackflow-api/pom.xml trackflow-api/
COPY trackflow-worker/ trackflow-worker/
# Tests run in CI; the image only packages what already passed there.
RUN ./mvnw -B -q -pl trackflow-worker -am package -DskipTests -Dspotless.check.skip=true \
    && java -Djarmode=tools -jar trackflow-worker/target/trackflow-worker-*.jar extract --layers --destination extracted \
    && mv extracted/application/trackflow-worker-*.jar extracted/application/trackflow-worker.jar

FROM eclipse-temurin:25-jre
# ffmpeg comes from the distribution, so rebuilding the image picks up its security fixes.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system trackflow \
    && useradd --system --gid trackflow --home-dir /app --shell /usr/sbin/nologin trackflow
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER trackflow
# Work files go to the JVM temp dir and are deleted after each job.
ENTRYPOINT ["java", "-jar", "/app/trackflow-worker.jar"]
