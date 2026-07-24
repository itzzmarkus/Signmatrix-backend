FROM eclipse-temurin:11-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew

RUN ./gradlew build --no-daemon -Dorg.gradle.jvmargs="-Xmx256m -XX:MaxMetaspaceSize=128m"

CMD ["./gradlew", "run", "--no-daemon"]