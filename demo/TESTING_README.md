# Руководство по тестированию BPMN процессов в Camunda 7

## Содержание
1. [Введение](#введение)
2. [Настройка проекта](#настройка-проекта)
3. [Структура тестов](#структура-тестов)
4. [Написание тестов](#написание-тестов)
5. [Лучшие практики](#лучшие-практики)
6. [Запуск тестов](#запуск-тестов)
7. [Примеры](#примеры)

---

## Введение

Данное руководство описывает подход к тестированию BPMN процессов в Camunda 7. Тестирование процессов критически важно для обеспечения корректности бизнес-логики и предотвращения ошибок в production окружении.

### Что тестируется в BPMN процессах:
- ✅ Корректность прохождения процесса по всем путям
- ✅ Правильность передачи переменных между задачами
- ✅ Вызовы внешних сервисов (HTTP, REST API)
- ✅ Обработка сообщений и событий
- ✅ Условные переходы (gateways)
- ✅ Вызовы подпроцессов (CallActivity)

---

## Настройка проекта

### Зависимости Maven

В `pom.xml` должны быть добавлены следующие зависимости:

```xml
<!-- Camunda BPM Spring Boot Starter -->
<dependency>
    <groupId>org.camunda.bpm.springboot</groupId>
    <artifactId>camunda-bpm-spring-boot-starter</artifactId>
    <version>7.20.0</version>
</dependency>

<!-- Camunda BPM Assert для тестирования -->
<dependency>
    <groupId>org.camunda.bpm.assert</groupId>
    <artifactId>camunda-bpm-assert</artifactId>
    <version>15.0.0</version>
    <scope>test</scope>
</dependency>

<!-- H2 Database для in-memory тестов -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Spring Boot Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- AssertJ для удобных assertions -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

### Конфигурация тестов

Создайте файл `src/test/resources/application-test.properties`:

```properties
# H2 in-memory database для тестов
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver

# Camunda конфигурация
camunda.bpm.history-level=full
camunda.bpm.deployment-resource-pattern=classpath*:**/*.bpmn

# Логирование
logging.level.org.camunda=INFO
logging.level.com.example.demo=DEBUG
```

---

## Структура тестов

### Базовая структура теста

```java
@SpringBootTest
@Deployment(resources = "bpmn/your-process.bpmn")
public class YourProcessTest extends AbstractProcessEngineRuleTest {

    @Autowired
    private RuntimeService runtimeService;

    @MockBean
    private YourDelegate yourDelegate;

    @BeforeEach
    public void setUp() {
        // Регистрация моков
        Mocks.register("yourDelegate", yourDelegate);
    }

    @Test
    public void testYourProcess() {
        // Given - подготовка данных
        
        // When - выполнение действий
        
        // Then - проверка результатов
    }
}
```

### Основные компоненты:

1. **@SpringBootTest** - запускает Spring контекст для тестов
2. **@Deployment** - автоматически деплоит BPMN процесс
3. **AbstractProcessEngineRuleTest** - базовый класс для тестов Camunda
4. **@MockBean** - создает моки Spring bean'ов (делегатов)
5. **Mocks.register()** - регистрирует моки в Camunda engine

---

## Написание тестов

### 1. Запуск процесса

```java
ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
    "process-key",
    withVariables(
        "variableName", "value",
        "anotherVariable", 123
    )
);
```

### 2. Проверка текущего состояния процесса

```java
// Проверка, что процесс ожидает на определенной задаче
assertThat(processInstance).isWaitingAt("Activity_ID");

// Проверка, что процесс завершен
assertThat(processInstance).isEnded();

// Проверка, что процесс запущен
assertThat(processInstance.isEnded()).isFalse();
```

### 3. Проверка переменных

```java
// Получение переменной
String value = (String) runtimeService.getVariable(processInstance.getId(), "variableName");

// Проверка значения
assertThat(value).isEqualTo("expected");
assertThat(value).isNotNull();
```

### 4. Мокирование делегатов

```java
@MockBean
private CreateUserDelegate createUserDelegate;

@BeforeEach
public void setUp() {
    Mocks.register("createUserDelegate", createUserDelegate);
}

@Test
public void testDelegateCall() {
    // Запуск процесса
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("process-key");
    
    // Проверка, что делегат был вызван
    verify(createUserDelegate, times(1)).execute(any());
    
    // Проверка параметров делегата
    verify(createUserDelegate).execute(argThat(execution -> {
        String userId = (String) execution.getVariable("userId");
        return userId != null && userId.startsWith("USER-");
    }));
}
```

### 5. Обработка User Tasks

```java
// Получение активных User Tasks
List<Task> tasks = taskService.createTaskQuery()
    .processInstanceId(processInstance.getId())
    .list();

assertThat(tasks).hasSize(1);
Task task = tasks.get(0);

// Выполнение задачи
taskService.complete(task.getId(), 
    withVariables("decision", "approved"));
```

### 6. Отправка сообщений (Message Events)

```java
// Корреляция сообщения с процессом
runtimeService.createMessageCorrelation("message-name")
    .processInstanceId(processInstance.getId())
    .setVariable("additionalData", "value")
    .correlate();

// Проверка, что процесс продолжился
assertThat(processInstance).isWaitingAt("NextActivity");
```

### 7. Тестирование CallActivity

Для CallActivity есть два подхода:

#### Подход 1: Проверка вызова без выполнения
```java
// Процесс остановится на CallActivity, если вызываемый процесс не задеплоен
assertThat(processInstance).isWaitingAt("CallActivity_ID");

// Проверяем, что переменные переданы корректно
assertThat(runtimeService.getVariable(processInstance.getId(), "var")).isNotNull();
```

#### Подход 2: Деплой обоих процессов
```java
@Deployment(resources = {
    "bpmn/parent-process.bpmn",
    "bpmn/child-process.bpmn"
})
public class ParentProcessTest {
    // Тест выполнит оба процесса
}
```

### 8. Проверка HTTP запросов (Service Tasks с коннекторами)

```java
@Test
public void testHttpServiceTask() {
    // Используем делегат вместо реального HTTP вызова
    @MockBean
    private HttpServiceDelegate httpServiceDelegate;
    
    // Проверяем, что делегат вызван с правильными параметрами
    verify(httpServiceDelegate).execute(argThat(execution -> {
        String url = (String) execution.getVariable("url");
        String method = (String) execution.getVariable("method");
        return "POST".equals(method) && url.contains("create-user");
    }));
}
```

---

## Лучшие практики

### 1. Изоляция тестов
- Каждый тест должен быть независимым
- Используйте `@BeforeEach` для инициализации
- Очищайте данные после тестов при необходимости

### 2. Моки внешних зависимостей
- Всегда мокируйте HTTP вызовы, базы данных, внешние API
- Используйте `@MockBean` для Spring компонентов
- Регистрируйте моки в Camunda через `Mocks.register()`

### 3. Понятные имена тестов
```java
// ✅ Хорошо
@Test
public void testUserRegistrationWithEmailConfirmation() { }

// ❌ Плохо
@Test
public void test1() { }
```

### 4. Структура Given-When-Then
```java
@Test
public void testProcessFlow() {
    // Given: Подготовка данных
    String userId = "USER-123";
    
    // When: Выполнение действий
    ProcessInstance process = runtimeService.startProcessInstanceByKey(
        "process", withVariables("userId", userId));
    
    // Then: Проверка результатов
    assertThat(process).isWaitingAt("UserTask");
}
```

### 5. Проверка критических путей
- Тестируйте happy path (успешный путь)
- Тестируйте error handling (обработка ошибок)
- Тестируйте граничные условия

### 6. Документирование тестов
```java
/**
 * Тест проверяет полный цикл регистрации пользователя:
 * 1. Инициализация переменных
 * 2. Создание записи пользователя
 * 3. Отправка email подтверждения
 * 4. Получение подтверждения
 * 5. Вызов следующего процесса
 */
@Test
public void testCompleteUserRegistrationFlow() { }
```

---

## Запуск тестов

### Через Maven
```bash
# Запуск всех тестов
mvn test

# Запуск конкретного теста
mvn test -Dtest=UserRegistrationProcessTest

# Запуск конкретного метода
mvn test -Dtest=UserRegistrationProcessTest#testCompleteUserRegistrationProcess
```

### Через IDE
- **IntelliJ IDEA**: Правый клик на тестовом классе → "Run Tests"
- **Eclipse**: Правый клик → "Run As" → "JUnit Test"

### С профилем
```bash
mvn test -Dspring.profiles.active=test
```

---

## Примеры

### Пример 1: Простой линейный процесс

```java
@Test
public void testLinearProcess() {
    // Given
    ProcessInstance process = runtimeService.startProcessInstanceByKey(
        "linear-process",
        withVariables("orderId", "ORD-001")
    );
    
    // When - процесс проходит через Service Task
    assertThat(process).isWaitingAt("UserTask_Approve");
    
    // Complete user task
    Task task = taskService.createTaskQuery()
        .processInstanceId(process.getId())
        .singleResult();
    taskService.complete(task.getId());
    
    // Then
    assertThat(process).isEnded();
}
```

### Пример 2: Процесс с условным шлюзом

```java
@Test
public void testGatewayProcess_ApprovedPath() {
    // Given
    ProcessInstance process = runtimeService.startProcessInstanceByKey(
        "approval-process",
        withVariables("amount", 1000)
    );
    
    // When - малая сумма идет по быстрому пути
    assertThat(process).isWaitingAt("AutoApprove");
    
    // Then
    String status = (String) runtimeService.getVariable(
        process.getId(), "status");
    assertThat(status).isEqualTo("approved");
}

@Test
public void testGatewayProcess_ManagerApprovalPath() {
    // Given - большая сумма требует одобрения
    ProcessInstance process = runtimeService.startProcessInstanceByKey(
        "approval-process",
        withVariables("amount", 10000)
    );
    
    // When
    assertThat(process).isWaitingAt("ManagerApproval");
    
    // Then - менеджер одобряет
    Task task = taskService.createTaskQuery()
        .processInstanceId(process.getId())
        .singleResult();
    taskService.complete(task.getId(), 
        withVariables("approved", true));
        
    assertThat(process).isEnded();
}
```

### Пример 3: Тестирование процесса user-registration

См. полный пример в `UserRegistrationProcessTest.java`:

```java
@Test
public void testCompleteUserRegistrationProcess() throws Exception {
    // Given: Подготовка входных данных
    String testUserName = "Test User";
    String testUserEmail = "test.user@example.com";

    // When: Запуск процесса
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
        "user-registration-process",
        withVariables(
            "userName", testUserName,
            "userEmail", testUserEmail
        )
    );

    // Then: Проверки
    assertThat(processInstance).isNotNull();
    assertThat(processInstance).isWaitingAt("Activity_0g1mra7"); // ReceiveTask
    
    // Проверка вызова делегатов
    verify(createRegistrationDelegate, times(1)).execute(any());
    verify(sendEmailDelegate, times(1)).execute(any());
    
    // Отправка сообщения
    runtimeService.createMessageCorrelation("email_confirmed_message")
        .processInstanceId(processInstance.getId())
        .setVariable("emailConfirmed", true)
        .correlate();
    
    // Проверка CallActivity
    assertThat(processInstance).isWaitingAt("Activity_0k5uvzl");
}
```

---

## Отладка тестов

### Логирование
Добавьте в `application-test.properties`:
```properties
logging.level.org.camunda.bpm.engine=DEBUG
logging.level.com.example.demo=DEBUG
```

### Проверка истории выполнения
```java
List<HistoricActivityInstance> activities = historyService
    .createHistoricActivityInstanceQuery()
    .processInstanceId(processInstance.getId())
    .orderByHistoricActivityInstanceStartTime().asc()
    .list();

activities.forEach(activity -> 
    System.out.println(activity.getActivityId() + " - " + activity.getActivityName())
);
```

### Дамп переменных
```java
Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
variables.forEach((key, value) -> 
    System.out.println(key + " = " + value)
);
```

---

## Частые ошибки и решения

### Ошибка: "Unknown property used in expression"
**Причина**: Переменная не инициализирована  
**Решение**: Убедитесь, что все переменные установлены перед использованием

### Ошибка: "No process definition found"
**Причина**: BPMN файл не задеплоен  
**Решение**: Проверьте аннотацию `@Deployment` и путь к файлу

### Ошибка: "Delegate not found"
**Причина**: Делегат не зарегистрирован  
**Решение**: Используйте `Mocks.register("beanName", mockBean)`

### Ошибка: "Process instance is not waiting at activity"
**Причина**: Процесс уже прошел эту точку или завершился  
**Решение**: Проверьте логику процесса и предыдущие шаги

---

## Полезные ссылки

- [Camunda BPM Assert Documentation](https://github.com/camunda/camunda-bpm-assert)
- [Camunda 7 Testing Best Practices](https://docs.camunda.org/manual/7.20/user-guide/testing/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Mockito Documentation](https://site.mockito.org/)

---

## Заключение

Тестирование BPMN процессов - это критически важная часть разработки. Следуя данному руководству, вы сможете:
- Писать надежные тесты для любых процессов
- Изолировать внешние зависимости
- Проверять корректность бизнес-логики
- Обеспечить стабильность системы

**Помните**: Хороший тест - это тест, который легко читается и понятен другим разработчикам!
