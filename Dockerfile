FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew clean build \
    -x test \
    -x spotlessCheck \
    --no-daemon \
    -Dorg.gradle.internal.http.connectionTimeout=120000 \
    -Dorg.gradle.internal.http.socketTimeout=120000
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
