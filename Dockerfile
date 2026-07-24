FROM eclipse-temurin:11-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew

RUN ./gradlew build --no-daemon

CMD ["./gradlew", "run", "--no-daemon"]