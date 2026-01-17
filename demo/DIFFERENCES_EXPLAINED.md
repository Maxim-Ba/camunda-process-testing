# 🎓 Два подхода к Service Tasks: HTTP Connector vs Java Delegate

## 📋 Обзор

В проекте демонстрируются **два разных подхода** к реализации Service Tasks в Camunda:

1. **HTTP Connector** - прямой HTTP запрос из BPMN (без Java кода)
2. **Java Delegate** - выполнение через Java класс (с возможностью мокирования)

---

## 🔄 Подход 1: HTTP Connector (create-registration-task)

### Что это?

HTTP Connector позволяет делать HTTP запросы **напрямую из BPMN**, без написания Java кода.

### Как выглядит в BPMN:

```xml
<bpmn:serviceTask id="Activity_118iyp4" name="create-registration-task">
  <bpmn:extensionElements>
    <camunda:connector>
      <camunda:inputOutput>
        <camunda:inputParameter name="method">POST</camunda:inputParameter>
        <camunda:inputParameter name="url">http://localhost:8084/create-user</camunda:inputParameter>
        <camunda:inputParameter name="headers">
          <camunda:map>
            <camunda:entry key="Content-Type">application/json</camunda:entry>
          </camunda:map>
        </camunda:inputParameter>
        <camunda:inputParameter name="payload">
          <camunda:script scriptFormat="groovy">
            import groovy.json.JsonOutput
            JsonOutput.toJson([
              userId: execution.getVariable("userId"),
              userName: execution.getVariable("userName"),
              userEmail: execution.getVariable("userEmail")
            ])
          </camunda:script>
        </camunda:inputParameter>
      </camunda:inputOutput>
      <camunda:connectorId>http-connector</camunda:connectorId>
    </camunda:connector>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

### ✅ Преимущества:

- ✅ Не нужно писать Java код
- ✅ Конфигурация прямо в BPMN
- ✅ Быстро для простых HTTP запросов
- ✅ Payload формируется через Groovy скрипт

### ❌ Недостатки:

- ❌ Сложно тестировать (делает реальные HTTP запросы)
- ❌ Нужен MockServer или WireMock для тестов
- ❌ Сложно добавить сложную логику
- ❌ Труднее отлаживать

### Тестирование HTTP Connector:

```java
@Test
public void testCreateRegistrationServiceTaskWithHttpConnector() {
    // Проблема: При запуске процесса будет попытка реального HTTP запроса!
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
        "user-registration-process",
        withVariables("userName", "Test User")
    );
    
    // ВАЖНО: Для изоляции нужен MockServer или WireMock
    // Пример с WireMock:
    // stubFor(post("/create-user").willReturn(ok().withBody("{\"status\":\"ok\"}")));
    
    // Можем только проверить переменные, которые были подготовлены
    assertThat(runtimeService.getVariable(processInstance.getId(), "userId"))
        .isNotNull();
}
```

---

## ☕ Подход 2: Java Delegate (send-email-confirmation)

### Что это?

Java Delegate - это Java класс, который выполняет логику Service Task. Camunda вызывает метод `execute()`.

### Java класс (делегат):

```java
@Component("sendEmailDelegate")
public class SendEmailDelegate implements JavaDelegate {
    
    private static final Logger logger = LoggerFactory.getLogger(SendEmailDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("=== Executing SendEmailDelegate ===");
        
        // 1. Получаем переменные из процесса
        String userEmail = (String) execution.getVariable("userEmail");
        String userName = (String) execution.getVariable("userName");
        String confirmationToken = (String) execution.getVariable("confirmationToken");
        
        logger.info("Sending email to: {}", userEmail);
        
        // 2. Выполняем бизнес-логику
        // В реальном приложении: emailService.send(...)
        
        // 3. Устанавливаем результат
        execution.setVariable("emailSent", true);
        execution.setVariable("emailSentDate", LocalDateTime.now().toString());
        
        logger.info("Email sent successfully");
    }
}
```

### Как выглядит в BPMN:

```xml
<bpmn:serviceTask id="Activity_0nlwkvw" 
                  name="send-email-confirmation" 
                  camunda:delegateExpression="${sendEmailDelegate}">
  <!-- Вся логика в Java классе SendEmailDelegate -->
  <bpmn:incoming>Flow_1jqmr28</bpmn:incoming>
  <bpmn:outgoing>Flow_1pzitqe</bpmn:outgoing>
</bpmn:serviceTask>
```

### ✅ Преимущества:

- ✅ **Легко тестировать** с моками
- ✅ Можно добавить сложную логику
- ✅ Легко отлаживать (точки останова в IDE)
- ✅ Можно использовать Spring DI
- ✅ Полный контроль над выполнением

### ❌ Недостатки:

- ❌ Нужно писать Java код
- ❌ Требуется компиляция
- ❌ Больше файлов в проекте

### Тестирование Java Delegate:

```java
@Test
public void testSendEmailServiceTask() {
    // Создаем мок делегата
    SendEmailDelegate mockDelegate = Mockito.mock(SendEmailDelegate.class);
    Mocks.register("sendEmailDelegate", mockDelegate);
    
    // Запускаем процесс - реальный email НЕ отправляется!
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
        "user-registration-process",
        withVariables("userName", "Test User", "userEmail", "test@example.com")
    );
    
    // Проверяем, что делегат был вызван с правильными параметрами
    verify(mockDelegate, times(1)).execute(argThat(execution -> {
        String userName = (String) execution.getVariable("userName");
        String userEmail = (String) execution.getVariable("userEmail");
        return "Test User".equals(userName) && "test@example.com".equals(userEmail);
    }));
}
```

---

## 📊 Сравнительная таблица

| Аспект | HTTP Connector | Java Delegate |
|--------|----------------|---------------|
| **Код** | Только BPMN + Groovy | Java класс + BPMN |
| **Тестирование** | Сложно (нужен MockServer) | Легко (Mockito моки) |
| **Отладка** | Сложно | Легко (IDE breakpoints) |
| **Сложная логика** | Неудобно | Удобно |
| **Spring DI** | Нет | Да |
| **Реальные запросы в тестах** | Да (проблема) | Нет (мокируется) |
| **Скорость разработки** | Быстро для простых случаев | Медленнее |
| **Переиспользование** | Сложно | Легко |

---

## 🎯 Когда использовать что?

### Используйте HTTP Connector когда:

✅ Простой HTTP запрос без сложной логики  
✅ Не нужны юнит-тесты (или есть MockServer)  
✅ Быстрый прототип  
✅ Integration testing с реальными сервисами  

**Пример**: Простой GET запрос для проверки статуса

### Используйте Java Delegate когда:

✅ Нужны юнит-тесты  
✅ Сложная бизнес-логика  
✅ Нужен Spring DI (репозитории, сервисы)  
✅ Нужна обработка ошибок  
✅ Переиспользование кода  

**Пример**: Отправка email с валидацией, логированием, обработкой ошибок

---

## 💡 Практический пример из проекта

### Сценарий: Регистрация пользователя

**Task 1: create-registration-task (HTTP Connector)**
```
Задача: Создать запись пользователя в базе через REST API
Подход: HTTP Connector
Почему: Простой POST запрос, микросервис уже существует
```

**Task 2: send-email-confirmation (Java Delegate)**
```
Задача: Отправить email с подтверждением
Подход: Java Delegate
Почему: Нужна валидация, логирование, обработка ошибок, тестирование
```

---

## 🧪 Как тестируются в проекте

### Тест 1: HTTP Connector (без MockServer)

```java
@Test
public void testCreateRegistrationServiceTaskWithHttpConnector() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
        "user-registration-process",
        withVariables("userName", "Test User")
    );
    
    // Можем проверить только переменные
    assertThat(runtimeService.getVariable(processInstance.getId(), "userId"))
        .isNotNull();
    
    // ВАЖНО: Реальный HTTP запрос выполнится (или упадет с ошибкой)
    // Для production тестов используйте WireMock
}
```

### Тест 2: Java Delegate (с моками)

```java
@BeforeEach
public void setUp() {
    SendEmailDelegate mockDelegate = Mockito.mock(SendEmailDelegate.class);
    Mocks.register("sendEmailDelegate", mockDelegate);
}

@Test
public void testSendEmailServiceTask() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
        "user-registration-process",
        withVariables("userName", "Test User")
    );
    
    // Проверяем вызов делегата с правильными параметрами
    verify(sendEmailDelegate, times(1)).execute(argThat(execution -> {
        String userName = (String) execution.getVariable("userName");
        return "Test User".equals(userName);
    }));
    
    // ВАЖНО: Реальный email НЕ отправляется!
}
```

---

## 📚 Дополнительные ресурсы

### Для HTTP Connector:
- [Camunda HTTP Connector Documentation](https://docs.camunda.org/manual/7.20/reference/connect/http-connector/)
- Используйте WireMock для тестов: [WireMock](http://wiremock.org/)

### Для Java Delegate:
- [Camunda Java Delegate Documentation](https://docs.camunda.org/manual/7.20/user-guide/process-engine/delegation-code/)
- См. `SendEmailDelegate.java` в проекте

---

## ✨ Вывод

**Оба подхода имеют место быть!**

- **HTTP Connector** - для простых REST вызовов
- **Java Delegate** - для бизнес-логики с тестированием

В учебных целях проект демонстрирует **оба подхода**, чтобы вы могли выбрать подходящий для вашей задачи.

---

**Happy Learning! 🎓**
