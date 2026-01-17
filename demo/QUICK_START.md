# Быстрый старт - Тестирование BPMN процессов

## 🚀 Запуск тестов (Quick Commands)

### Все тесты
```bash
cd demo
mvn clean test
```

### Конкретный тест (без CallActivity)
```bash
mvn test -Dtest=UserRegistrationProcessTest
```

### Тест с реальным выполнением CallActivity
```bash
mvn test -Dtest=UserRegistrationProcessWithSubprocessTest
```

### Конкретный метод теста
```bash
mvn test -Dtest=UserRegistrationProcessTest#testCompleteUserRegistrationProcess
```

### С детальным логированием
```bash
mvn test -Dlogging.level.org.camunda=DEBUG -Dlogging.level.com.example.demo=DEBUG
```

## 📋 Список тестов

### UserRegistrationProcessTest
Основной набор тестов для процесса user-registration:

1. ✅ `testCompleteUserRegistrationProcess()` - Полное прохождение процесса
2. ✅ `testVariablesInitialization()` - Инициализация переменных
3. ✅ `testCreateRegistrationServiceTask()` - Тест сервис таска создания регистрации
4. ✅ `testSendEmailServiceTask()` - Тест сервис таска отправки email
5. ✅ `testEmailConfirmationMessageReceive()` - Тест получения сообщения
6. ✅ `testCallActivityInvocation()` - Тест вызова CallActivity (без выполнения)
7. ✅ `testCallActivityVariablesPropagation()` - Тест передачи переменных в CallActivity

### UserRegistrationProcessWithSubprocessTest
Тесты с реальным выполнением подпроцесса:

1. ✅ `testCompleteFlowWithSubprocess()` - Полный цикл с выполнением подпроцесса
2. ✅ `testVariablesPropagationToSubprocess()` - Передача переменных в подпроцесс и обратно

## 🎯 Что проверяется

| Аспект | Описание | Тест |
|--------|----------|------|
| **Инициализация** | Скрипт создает начальные переменные | `testVariablesInitialization` |
| **Service Tasks** | Делегаты вызываются с правильными параметрами | `testCreateRegistrationServiceTask`, `testSendEmailServiceTask` |
| **Receive Task** | Процесс корректно получает сообщения | `testEmailConfirmationMessageReceive` |
| **CallActivity (мок)** | Проверка вызова без выполнения | `testCallActivityInvocation` |
| **CallActivity (реальный)** | Выполнение подпроцесса | `testCompleteFlowWithSubprocess` |
| **Переменные** | Передача переменных между задачами | `testCallActivityVariablesPropagation` |
| **End-to-end** | Полный цикл процесса | `testCompleteUserRegistrationProcess` |

## 🔍 Ожидаемый результат

При успешном выполнении всех тестов вы должны увидеть:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.demo.process.UserRegistrationProcessTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.demo.process.UserRegistrationProcessWithSubprocessTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

## 🐛 Отладка

### Просмотр логов процесса
```bash
mvn test -Dlogging.level.org.camunda.bpm.engine=DEBUG
```

### Просмотр SQL запросов
```bash
mvn test -Dlogging.level.org.hibernate.SQL=DEBUG
```

### Просмотр переменных процесса
Добавьте в тест:
```java
Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
variables.forEach((key, value) -> System.out.println(key + " = " + value));
```

### Просмотр истории выполнения
Добавьте в тест:
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

## 📦 Структура тестовых данных

### Дефолтные переменные (если не переданы)
```java
userName = "John Doe"
userEmail = "john.doe@example.com"
userId = <generated UUID>
registrationDate = <current timestamp>
confirmationToken = <generated UUID>
```

### Пример запуска с кастомными переменными
```java
ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
    "user-registration-process",
    withVariables(
        "userName", "Custom User",
        "userEmail", "custom@example.com"
    )
);
```

## 🛠️ Troubleshooting

### Ошибка: "No process definition found"
**Решение**: Убедитесь, что аннотация `@Deployment` указывает на правильный путь к BPMN файлу.

### Ошибка: "Delegate not found"
**Решение**: Проверьте, что делегат зарегистрирован в `setUp()` методе:
```java
Mocks.register("createRegistrationDelegate", createRegistrationDelegate);
```

### Тест зависает
**Причина**: Процесс ожидает на User Task или Receive Task  
**Решение**: Проверьте, что вы отправляете сообщение или завершаете задачу

### Ошибка в CallActivity
**Причина**: Подпроцесс не найден  
**Решение**: 
- Для теста БЕЗ выполнения - это нормально, процесс остановится на CallActivity
- Для теста С выполнением - добавьте подпроцесс в `@Deployment`

## 📚 Дополнительная информация

- **Полное руководство**: См. [TESTING_README.md](TESTING_README.md)
- **Общая информация**: См. [README.md](README.md)
- **Camunda Docs**: https://docs.camunda.org/manual/7.20/

## 💡 Полезные команды Maven

```bash
# Пропустить тесты при сборке
mvn clean install -DskipTests

# Запустить только integration тесты
mvn verify

# Сгенерировать отчет о покрытии
mvn test jacoco:report

# Запустить тесты параллельно
mvn test -T 4
```

---

**Happy Testing! 🎉**
