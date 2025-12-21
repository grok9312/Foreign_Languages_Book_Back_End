# 第一階段：編譯
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY src/main/resources .
RUN mvn clean package -DskipTests

# 第二階段：執行
FROM eclipse-temurin:21-jdk
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]