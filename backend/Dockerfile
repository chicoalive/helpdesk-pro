FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew ./
COPY gradle/wrapper/ gradle/wrapper/
COPY build.gradle settings.gradle ./
COPY src/ src/

RUN chmod +x ./gradlew \
    && ./gradlew bootJar --no-daemon -x test \
    && set -eu \
    && boot_jar="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$boot_jar" \
    && cp "$boot_jar" /app/app.jar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
