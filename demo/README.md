# Camunda Process Testing Demo

Демонстрационный проект для тестирования BPMN процессов в Camunda 7.

## Описание проекта

Проект содержит примеры BPMN процессов и comprehensive тесты для них:

### BPMN Процессы

1. **user-registration-process** (`user-registration.bpmn`)
   - Процесс регистрации пользователя
   - Включает HTTP сервис таски, receive task для получения сообщения
   - Call activity для вызова подпроцесса

2. **chose-next-process** (`chose-next-process.bpmn`)
   - Подпроцесс выбора следующего шага
   - Вызывается из user-registration-process через CallActivity

## Структура проекта

```
demo/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/demo/
│   │   │       ├── DemoApplication.java
│   │   │       └── delegate/
│   │   │           ├── CreateRegistrationDelegate.java
│   │   │           └── SendEmailDelegate.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── bpmn/
│   │           ├── user-registration.bpmn
│   │           └── chose-next-process.bpmn
│   └── test/
│       ├── java/
│       │   └── com/example/demo/
│       │       ├── config/
│       │       │   └── TestConfiguration.java
│       │       └── process/
│       │           └── UserRegistrationProcessTest.java
│       └── resources/
│           └── application-test.properties
├── pom.xml
├── README.md
└── TESTING_README.md (подробное руководство по тестированию)
```

## Быстрый старт

### Требования

- Java 17+
- Maven 3.6+

### Установка зависимостей

```bash
cd demo
mvn clean install
```

### Запуск приложения

```bash
mvn spring-boot:run
```

После запуска Camunda доступна по адресу: http://localhost:8085

- **Camunda Cockpit**: http://localhost:8085/camunda/app/cockpit
- **Camunda Tasklist**: http://localhost:8085/camunda/app/tasklist
- **REST API**: http://localhost:8085/engine-rest

Логин: `admin` / Пароль: `admin`

### Запуск тестов

```bash
# Запуск всех тестов
mvn test

# Запуск конкретного теста
mvn test -Dtest=UserRegistrationProcessTest

# Запуск с детальным логированием
mvn test -Dlogging.level.org.camunda=DEBUG
```

## Что протестировано

В проекте реализованы тесты, покрывающие:

✅ **Инициализация переменных** - проверка скрипта инициализации в начале процесса  
✅ **Service Tasks** - проверка вызова делегатов с правильными параметрами  
✅ **HTTP коннекторы** - моки внешних HTTP вызовов  
✅ **Receive Task** - тестирование получения сообщений  
✅ **Call Activity** - проверка вызова подпроцесса без реального выполнения  
✅ **Передача переменных** - проверка корректности передачи переменных между задачами  
✅ **Полный цикл процесса** - end-to-end тестирование  

## Архитектура процесса user-registration

```
[Start] 
   ↓
[Initialize Variables] (Script Task)
   ↓
[Create Registration] (Service Task + Delegate)
   ↓
[Send Email Confirmation] (Service Task + Delegate)
   ↓
[Wait for Email Confirmation] (Receive Task)
   ↓ (message: email_confirmed_message)
[Choose Next Process] (Call Activity)
   ↓
[End]
```

### Переменные процесса

| Переменная | Тип | Описание | Инициализация |
|-----------|-----|----------|---------------|
| `userId` | String | Уникальный ID пользователя | UUID генерируется в скрипте |
| `userName` | String | Имя пользователя | Входной параметр или "John Doe" |
| `userEmail` | String | Email пользователя | Входной параметр или "john.doe@example.com" |
| `registrationDate` | String | Дата регистрации | Текущее время в скрипте |
| `confirmationToken` | String | Токен подтверждения email | UUID генерируется в скрипте |
| `emailConfirmed` | Boolean | Флаг подтверждения email | Устанавливается при получении сообщения |

### Делегаты

#### CreateRegistrationDelegate
- **Bean ID**: `createRegistrationDelegate`
- **Назначение**: Создание записи регистрации пользователя
- **Входные переменные**: `userId`, `userName`, `userEmail`, `registrationDate`
- **Выходные переменные**: `registrationId`, `registrationStatus`

#### SendEmailDelegate
- **Bean ID**: `sendEmailDelegate`
- **Назначение**: Отправка email подтверждения
- **Входные переменные**: `userEmail`, `userName`, `confirmationToken`
- **Выходные переменные**: `emailSent`, `emailSentDate`

## Примеры использования

### Запуск процесса через REST API

```bash
curl -X POST http://localhost:8085/engine-rest/process-definition/key/user-registration-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "userName": {"value": "John Doe", "type": "String"},
      "userEmail": {"value": "john@example.com", "type": "String"}
    }
  }'
```

### Отправка сообщения для продолжения процесса

```bash
curl -X POST http://localhost:8085/engine-rest/message \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "email_confirmed_message",
    "processInstanceId": "YOUR_PROCESS_INSTANCE_ID",
    "processVariables": {
      "emailConfirmed": {"value": true, "type": "Boolean"}
    }
  }'
```

## Документация

Подробное руководство по тестированию BPMN процессов находится в файле **[TESTING_README.md](TESTING_README.md)**.

Оно включает:
- Настройку проекта для тестирования
- Структуру тестов
- Лучшие практики
- Примеры кода
- Решение частых проблем

## Технологии

- **Camunda BPM 7.20.0** - BPMN движок
- **Spring Boot 3.5.9** - Application framework
- **H2 Database** - In-memory база для тестов
- **JUnit 5** - Тестовый фреймворк
- **Camunda BPM Assert** - Assertions для тестов процессов
- **Mockito** - Мокирование компонентов
- **AssertJ** - Fluent assertions

## Лицензия

MIT

## Автор

Demo Project for Camunda Process Testing
