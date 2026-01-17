# 🌍 Environment Variables - Краткая инструкция

## ✅ Что было сделано

### Изменен URL в BPMN:

**Было:**
```xml
<camunda:inputParameter name="url">http://localhost:8084/create-user</camunda:inputParameter>
```

**Стало:**
```xml
<camunda:inputParameter name="url">${SERVICE_API}/create-user</camunda:inputParameter>
```

### Добавлена конфигурация:

**application.properties:**
```properties
# External Service API URL (можно переопределить через environment variable)
SERVICE_API=${SERVICE_API:http://localhost:8084}
```

**application-test.properties:**
```properties
# External Service API URL для тестов (mock endpoint)
SERVICE_API=http://mock-service:9999
```

---

## 🎯 Как это работает

### 1. В Production:

```bash
# Запуск с дефолтным значением
mvn spring-boot:run
# SERVICE_API = http://localhost:8084

# Запуск с переопределением
export SERVICE_API=https://api.production.com
mvn spring-boot:run
# SERVICE_API = https://api.production.com
```

### 2. В тестах:

```bash
mvn test
# SERVICE_API = http://mock-service:9999 (из application-test.properties)
```

---

## 🚀 Способы установки SERVICE_API

### Windows:
```cmd
set SERVICE_API=http://my-api:8080
mvn spring-boot:run
```

### Linux/Mac:
```bash
export SERVICE_API=http://my-api:8080
mvn spring-boot:run
```

### Docker:
```yaml
environment:
  - SERVICE_API=http://api-service:8080
```

### application.properties:
```properties
SERVICE_API=http://my-custom-api:8080
```

---

## 🧪 Тестирование

### Проблема:
HTTP Connector делает **реальные HTTP запросы** в тестах!

```java
@Test
public void test() {
    // ❌ Попытается сделать запрос к http://mock-service:9999/create-user
    // ❌ Упадет с Connection refused
    ProcessInstance process = runtimeService.startProcessInstanceByKey("user-registration-process");
}
```

### Решение 1: WireMock (рекомендуется)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8</artifactId>
    <version>2.35.0</version>
    <scope>test</scope>
</dependency>
```

```java
@SpringBootTest
@AutoConfigureWireMock(port = 9999)
public class UserRegistrationProcessTest {
    
    @BeforeEach
    public void setUp() {
        // Mock HTTP endpoint
        stubFor(post("/create-user")
            .willReturn(ok().withBody("{\"status\":\"ok\"}")));
    }
    
    @Test
    public void test() {
        // ✅ Запрос пойдет в WireMock, тест пройдет
        ProcessInstance process = runtimeService.startProcessInstanceByKey("user-registration-process");
    }
}
```

### Решение 2: Java Delegate (как send-email-confirmation)

Используйте делегат вместо HTTP Connector - легко мокируется!

---

## 📊 Два подхода в проекте

### 1. HTTP Connector (create-registration-task)
- ✅ Простота - не нужен Java код
- ✅ Использует `${SERVICE_API}` из env
- ❌ Сложно тестировать - нужен WireMock
- ❌ Делает реальные HTTP запросы в тестах

### 2. Java Delegate (send-email-confirmation)
- ✅ Легко тестировать - мокируется
- ✅ Гибкость - полный контроль в Java
- ✅ Не делает реальных запросов в тестах
- ❌ Нужно писать Java код

---

## 💡 Примеры

### Пример 1: Development
```bash
# Локальная разработка
export SERVICE_API=http://localhost:8084
mvn spring-boot:run
```

### Пример 2: Production
```bash
# Production окружение
export SERVICE_API=https://api.production.com
mvn spring-boot:run -Dspring.profiles.active=prod
```

### Пример 3: Docker Compose
```yaml
version: '3'
services:
  camunda-app:
    image: your-app
    environment:
      - SERVICE_API=http://registration-service:8080
  
  registration-service:
    image: registration-api:latest
```

---

## 🔍 Проверка значения

Добавьте в `DemoApplication.java`:

```java
@Value("${SERVICE_API}")
private String serviceApi;

@PostConstruct
public void init() {
    System.out.println("SERVICE_API = " + serviceApi);
}
```

---

## 📚 Документация

- **ENV_VARIABLES_GUIDE.md** - Подробное руководство (английский)
- **DIFFERENCES_EXPLAINED.md** - Сравнение HTTP Connector vs Java Delegate

---

## ✨ Итог

**Теперь URL конфигурируется через environment variable!**

- ✅ `${SERVICE_API}` в BPMN
- ✅ Дефолтное значение: `http://localhost:8084`
- ✅ Для тестов: `http://mock-service:9999`
- ✅ Легко переопределить через `export SERVICE_API=...`

**Для тестов рекомендуется:**
- WireMock для HTTP Connector
- Mockito для Java Delegate

---

**Happy Testing! 🎉**
