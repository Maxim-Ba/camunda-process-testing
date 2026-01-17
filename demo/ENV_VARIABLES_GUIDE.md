# 🔧 Руководство по использованию Environment Variables в Camunda

## 📋 Обзор

В проекте используются **environment variables** для конфигурации внешних сервисов. Это позволяет:
- ✅ Менять URL сервисов без изменения BPMN
- ✅ Использовать разные настройки для dev/test/prod
- ✅ Хранить конфигурацию отдельно от кода

---

## 🎯 Переменная SERVICE_API

### Что это?

`SERVICE_API` - это URL базового адреса внешнего сервиса регистрации пользователей.

### Где используется?

В BPMN процессе `user-registration.bpmn`:

```xml
<bpmn:serviceTask id="Activity_118iyp4" name="create-registration-task">
  <camunda:connector>
    <camunda:inputOutput>
      <camunda:inputParameter name="url">${SERVICE_API}/create-user</camunda:inputParameter>
      <!-- HTTP POST запрос к ${SERVICE_API}/create-user -->
    </camunda:inputOutput>
  </camunda:connector>
</bpmn:serviceTask>
```

### Как это работает?

```
1. Camunda читает переменную ${SERVICE_API}
2. Подставляет значение из конфигурации
3. Формирует полный URL: ${SERVICE_API}/create-user
4. Выполняет HTTP запрос
```

---

## ⚙️ Конфигурация

### 1. Production (application.properties)

```properties
# External Service API URL (можно переопределить через environment variable)
SERVICE_API=${SERVICE_API:http://localhost:8084}
```

**Синтаксис**: `${VARIABLE_NAME:default_value}`
- `SERVICE_API` - имя переменной окружения
- `http://localhost:8084` - значение по умолчанию (если переменная не установлена)

### 2. Testing (application-test.properties)

```properties
# External Service API URL для тестов (mock endpoint)
SERVICE_API=http://mock-service:9999
```

В тестах используется несуществующий адрес для демонстрации изоляции.

---

## 🚀 Способы установки SERVICE_API

### Способ 1: Environment Variable (рекомендуется для production)

**Windows:**
```cmd
set SERVICE_API=http://production-api.example.com:8080
mvn spring-boot:run
```

**Linux/Mac:**
```bash
export SERVICE_API=http://production-api.example.com:8080
mvn spring-boot:run
```

**Docker:**
```yaml
version: '3'
services:
  camunda-app:
    image: your-app
    environment:
      - SERVICE_API=http://api-service:8080
```

### Способ 2: application.properties

```properties
SERVICE_API=http://my-custom-api:8080
```

### Способ 3: Command Line Argument

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--SERVICE_API=http://custom-api:8080
```

### Способ 4: System Property

```bash
mvn spring-boot:run -DSERVICE_API=http://custom-api:8080
```

---

## 🧪 Использование в тестах

### Проблема с HTTP Connector в тестах

HTTP Connector делает **реальные HTTP запросы**:

```java
@Test
public void testProcess() {
    // HTTP connector попытается сделать запрос к http://mock-service:9999/create-user
    ProcessInstance process = runtimeService.startProcessInstanceByKey("user-registration-process");
    
    // ❌ Тест упадет с ConnectorException (Connection refused)
}
```

### Решение 1: WireMock (рекомендуется)

Добавьте в `pom.xml`:
```xml
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8</artifactId>
    <version>2.35.0</version>
    <scope>test</scope>
</dependency>
```

Используйте в тесте:
```java
@SpringBootTest
@AutoConfigureWireMock(port = 9999) // Запустит mock на порту 9999
public class UserRegistrationProcessTest {

    @BeforeEach
    public void setUp() {
        // Настройка mock endpoint
        stubFor(post(urlEqualTo("/create-user"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\",\"id\":\"123\"}")));
    }

    @Test
    public void testProcess() {
        // Теперь HTTP запрос пойдет в WireMock и вернет mock ответ
        ProcessInstance process = runtimeService.startProcessInstanceByKey("user-registration-process");
        
        // ✅ Тест пройдет успешно
        assertThat(process).isWaitingAt("Activity_0g1mra7");
    }
}
```

### Решение 2: Переопределение через @TestPropertySource

```java
@SpringBootTest
@TestPropertySource(properties = {
    "SERVICE_API=http://localhost:8888"
})
public class UserRegistrationProcessTest {
    // Теперь SERVICE_API = http://localhost:8888
}
```

### Решение 3: Использование Java Delegate (как в send-email-confirmation)

Вместо HTTP Connector используйте Java Delegate с моками:

```java
@Component("myServiceDelegate")
public class MyServiceDelegate implements JavaDelegate {
    @Value("${SERVICE_API}")
    private String serviceApi;
    
    @Override
    public void execute(DelegateExecution execution) {
        // Делаем HTTP запрос к ${serviceApi}/create-user
        // В тестах этот делегат мокируется и реальный запрос не выполняется
    }
}
```

---

## 📊 Сравнение подходов

| Подход | HTTP Connector | Java Delegate |
|--------|----------------|---------------|
| **Конфигурация URL** | `${SERVICE_API}` в BPMN | `@Value("${SERVICE_API}")` в Java |
| **Тестирование** | Нужен WireMock/MockServer | Легко мокируется |
| **Реальные запросы в тестах** | Да (проблема) | Нет (мокируется) |
| **Гибкость** | Ограничена BPMN | Полная в Java |

---

## 💡 Примеры использования

### Пример 1: Development окружение

```properties
# application-dev.properties
SERVICE_API=http://localhost:8084
```

```bash
mvn spring-boot:run -Dspring.profiles.active=dev
```

### Пример 2: Production окружение

```bash
# Установка через environment variable
export SERVICE_API=https://api.production.com
mvn spring-boot:run -Dspring.profiles.active=prod
```

### Пример 3: Docker Compose

```yaml
version: '3'
services:
  camunda-app:
    build: .
    environment:
      - SERVICE_API=http://registration-service:8080
      - SPRING_PROFILES_ACTIVE=prod
    ports:
      - "8085:8085"
  
  registration-service:
    image: registration-api:latest
    ports:
      - "8080:8080"
```

### Пример 4: Kubernetes

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: camunda-config
data:
  SERVICE_API: "http://registration-service.default.svc.cluster.local:8080"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camunda-app
spec:
  template:
    spec:
      containers:
      - name: camunda
        image: camunda-app:latest
        env:
        - name: SERVICE_API
          valueFrom:
            configMapKeyRef:
              name: camunda-config
              key: SERVICE_API
```

---

## 🔍 Отладка

### Проверка значения переменной

Добавьте в `DemoApplication.java`:

```java
@SpringBootApplication
public class DemoApplication {
    
    @Value("${SERVICE_API}")
    private String serviceApi;
    
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
    
    @PostConstruct
    public void init() {
        System.out.println("SERVICE_API = " + serviceApi);
    }
}
```

### Логирование в BPMN

Добавьте Script Task перед HTTP Connector:

```xml
<bpmn:scriptTask id="LogUrl" scriptFormat="groovy">
  <bpmn:script>
    println "SERVICE_API = " + execution.getVariable("SERVICE_API")
    println "Full URL = ${SERVICE_API}/create-user"
  </bpmn:script>
</bpmn:scriptTask>
```

---

## 🎓 Лучшие практики

### ✅ DO (Делайте):

1. **Используйте значения по умолчанию**
   ```properties
   SERVICE_API=${SERVICE_API:http://localhost:8084}
   ```

2. **Документируйте переменные**
   ```properties
   # External Service API URL
   # Default: http://localhost:8084
   # Production: https://api.production.com
   SERVICE_API=${SERVICE_API:http://localhost:8084}
   ```

3. **Используйте разные значения для разных окружений**
   ```
   application-dev.properties:  SERVICE_API=http://localhost:8084
   application-test.properties: SERVICE_API=http://mock-service:9999
   application-prod.properties: SERVICE_API=https://api.production.com
   ```

4. **Для тестов используйте WireMock**
   ```java
   @AutoConfigureWireMock(port = 9999)
   ```

### ❌ DON'T (Не делайте):

1. **Не хардкодите URL в BPMN**
   ```xml
   <!-- ❌ Плохо -->
   <camunda:inputParameter name="url">http://localhost:8084/create-user</camunda:inputParameter>
   
   <!-- ✅ Хорошо -->
   <camunda:inputParameter name="url">${SERVICE_API}/create-user</camunda:inputParameter>
   ```

2. **Не храните секреты в application.properties**
   ```properties
   # ❌ Плохо
   API_KEY=secret123
   
   # ✅ Хорошо - используйте environment variables
   API_KEY=${API_KEY}
   ```

3. **Не используйте production URL в тестах**
   ```properties
   # ❌ Плохо в application-test.properties
   SERVICE_API=https://api.production.com
   
   # ✅ Хорошо
   SERVICE_API=http://mock-service:9999
   ```

---

## 📚 Дополнительные ресурсы

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Camunda Expression Language](https://docs.camunda.org/manual/7.20/user-guide/process-engine/expression-language/)
- [WireMock Documentation](http://wiremock.org/docs/)

---

## ✨ Итог

**Environment Variables** позволяют:
- ✅ Гибко конфигурировать приложение
- ✅ Использовать разные настройки для разных окружений
- ✅ Не хардкодить URL в BPMN
- ✅ Легко деплоить в Docker/Kubernetes

**В проекте демонстрируется:**
- HTTP Connector с `${SERVICE_API}` (create-registration-task)
- Java Delegate с моками (send-email-confirmation)

**Для тестов рекомендуется:**
- WireMock для HTTP Connector
- Mockito для Java Delegate

---

**Happy Configuration! 🎉**
