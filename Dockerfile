FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build -x test
EXPOSE 8080
ENTRYPOINT ["java","-jar","build/libs/linkvoid-0.0.1-SNAPSHOT.jar"]