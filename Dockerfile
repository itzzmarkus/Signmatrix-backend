FROM eclipse-temurin:11-jdk

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew

RUN ./gradlew build --no-daemon -Dorg.gradle.jvmargs="-Xmx192m -XX:MaxMetaspaceSize=256m"

CMD ["./gradlew", "run", "--no-daemon"]