# 第一階段：編譯
FROM maven:3.9.6-eclipse-temurin-21 AS build
# 修正這行：複製整個專案目錄的所有內容
COPY . .
RUN mvn clean package -DskipTests

# 第二階段：執行
FROM eclipse-temurin:21-jdk
# 確保打包後的 jar 檔被正確複製
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]