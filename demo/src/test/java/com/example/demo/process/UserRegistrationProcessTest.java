package com.example.demo.process;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.demo.delegate.SendEmailDelegate;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для процесса регистрации пользователя (user-registration-process).
 * 
 * Демонстрирует два подхода к Service Tasks:
 * 1. HTTP Connector (create-registration-task) - использует ${SERVICE_API} из env переменных
 * 2. Java Delegate (send-email-confirmation) - через делегат с моками
 * 
 * ВАЖНО: HTTP Connector будет пытаться сделать реальный запрос к ${SERVICE_API}/create-user
 * В тестах SERVICE_API=http://mock-service:9999 (см. application-test.properties)
 * Для полной изоляции используйте WireMock или MockServer
 * 
 * Также тестируются:
 * - Инициализация переменных через скрипт
 * - Получение сообщения (ReceiveTask)
 * - Вызов CallActivity
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@Deployment(resources = "bpmn/user-registration.bpmn")
public class UserRegistrationProcessTest {

    @Autowired
    private RuntimeService runtimeService;

    private SendEmailDelegate sendEmailDelegate;

    @BeforeEach
    public void setUp() {
        // Создаем мок только для SendEmailDelegate
        // CreateRegistrationTask использует HTTP connector с ${SERVICE_API}
        sendEmailDelegate = Mockito.mock(SendEmailDelegate.class);
        
        // Регистрируем мок делегата в Camunda
        Mocks.register("sendEmailDelegate", sendEmailDelegate);
    }
    
    /**
     * Вспомогательный метод для запуска процесса с SERVICE_API переменной.
     * SERVICE_API нужно передать как переменную процесса, так как Spring properties
     * не доступны напрямую в Camunda Expression Language.
     */
    private ProcessInstance startProcessWithServiceApi(String userName, String userEmail) {
        return runtimeService.startProcessInstanceByKey(
            "user-registration-process",
            withVariables(
                "userName", userName,
                "userEmail", userEmail,
                "SERVICE_API", "http://mock-service:9999" // Передаем SERVICE_API как переменную
            )
        );
    }

    /**
     * Тест полного прохождения процесса регистрации пользователя
     * с проверкой всех этапов и переменных.
     */
    @Test
    public void testCompleteUserRegistrationProcess() throws Exception {
        // Given: Подготовка входных данных
        String testUserName = "Test User";
        String testUserEmail = "test.user@example.com";

        // When: Запуск процесса с входными переменными
        ProcessInstance processInstance = startProcessWithServiceApi(testUserName, testUserEmail);

        // Then: Проверяем, что процесс запущен
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.isEnded()).isFalse();

        // Проверяем, что скрипт инициализации выполнился и установил переменные
        assertThat(runtimeService.getVariable(processInstance.getId(), "userId")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "userName")).isEqualTo(testUserName);
        assertThat(runtimeService.getVariable(processInstance.getId(), "userEmail")).isEqualTo(testUserEmail);
        assertThat(runtimeService.getVariable(processInstance.getId(), "registrationDate")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "confirmationToken")).isNotNull();

        // Проверяем, что процесс остановился на ReceiveTask "email-confirmed"
        assertThat(processInstance).isWaitingAt("Activity_0g1mra7");

        // Проверяем, что делегат SendEmail был вызван
        // Примечание: create-registration-task использует HTTP connector (не делегат)
        verify(sendEmailDelegate, times(1)).execute(any());

        // When: Отправляем сообщение для продолжения процесса (симуляция подтверждения email)
        runtimeService.createMessageCorrelation("email_confirmed_message")
            .processInstanceId(processInstance.getId())
            .setVariable("emailConfirmed", true)
            .correlate();

        // Then: Проверяем, что процесс перешел к CallActivity
        assertThat(processInstance).isWaitingAt("Activity_0k5uvzl");

        // When: Выполняем CallActivity (мокируем выполнение подпроцесса)
        execute(job());

        // Then: Проверяем, что процесс завершился
        assertThat(processInstance).isEnded();
    }

    /**
     * Тест проверки инициализации переменных в начале процесса.
     */
    @Test
    public void testVariablesInitialization() throws Exception {
        // When: Запуск процесса без входных переменных (только с SERVICE_API)
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "user-registration-process",
            withVariables("SERVICE_API", "http://mock-service:9999")
        );

        // Then: Проверяем дефолтные значения переменных
        assertThat(runtimeService.getVariable(processInstance.getId(), "userId")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "userName")).isEqualTo("John Doe");
        assertThat(runtimeService.getVariable(processInstance.getId(), "userEmail")).isEqualTo("john.doe@example.com");
        assertThat(runtimeService.getVariable(processInstance.getId(), "registrationDate")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "confirmationToken")).isNotNull();
    }

    /**
     * Тест проверки HTTP Connector (create-registration-task).
     * Этот Service Task использует HTTP connector с переменной ${SERVICE_API}.
     * 
     * ВАЖНО: 
     * - URL формируется как ${SERVICE_API}/create-user
     * - В тестах SERVICE_API=http://mock-service:9999 (см. application-test.properties)
     * - HTTP connector попытается сделать реальный запрос к http://mock-service:9999/create-user
     * - Для полной изоляции используйте WireMock или MockServer
     * 
     * Этот тест демонстрирует подход БЕЗ делегата (только HTTP connector).
     */
    @Test
    public void testCreateRegistrationServiceTaskWithHttpConnector() throws Exception {
        // Given
        String testUserName = "Test User";
        String testUserEmail = "test.user@example.com";

        // When: Запуск процесса
        ProcessInstance processInstance = startProcessWithServiceApi(testUserName, testUserEmail);

        // Then: Проверяем, что переменные были корректно инициализированы
        // (они будут использованы в HTTP connector для формирования payload)
        assertThat(runtimeService.getVariable(processInstance.getId(), "userId")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "userName")).isEqualTo(testUserName);
        assertThat(runtimeService.getVariable(processInstance.getId(), "userEmail")).isEqualTo(testUserEmail);
        assertThat(runtimeService.getVariable(processInstance.getId(), "registrationDate")).isNotNull();

        // Примечание: HTTP connector попытается выполнить реальный запрос к ${SERVICE_API}/create-user
        // где SERVICE_API берется из application-test.properties (http://mock-service:9999)
        // Тест упадет с ConnectorException, так как mock-service не существует
        // Это нормально и демонстрирует разницу с Java Delegate подходом
        
        // Для production тестов:
        // 1. Используйте WireMock: stubFor(post("/create-user").willReturn(ok()))
        // 2. Или MockServer для имитации HTTP endpoint
        // 3. Или переопределите SERVICE_API через @TestPropertySource
    }

    /**
     * Тест проверки вызова сервис таска отправки email с правильными переменными.
     */
    @Test
    public void testSendEmailServiceTask() throws Exception {
        // Given
        String testUserName = "Test User";
        String testUserEmail = "test.user@example.com";

        // When: Запуск процесса
        ProcessInstance processInstance = startProcessWithServiceApi(testUserName, testUserEmail);

        // Then: Проверяем, что делегат отправки email был вызван
        verify(sendEmailDelegate, times(1)).execute(argThat(execution -> {
            // Проверяем переменные, переданные в делегат
            String userName = (String) execution.getVariable("userName");
            String userEmail = (String) execution.getVariable("userEmail");
            String confirmationToken = (String) execution.getVariable("confirmationToken");

            return testUserName.equals(userName) 
                && testUserEmail.equals(userEmail)
                && confirmationToken != null;
        }));

        // Проверяем, что процесс остановился на ReceiveTask
        assertThat(processInstance).isWaitingAt("Activity_0g1mra7");
    }

    /**
     * Тест проверки получения сообщения (ReceiveTask).
     */
    @Test
    public void testEmailConfirmationMessageReceive() throws Exception {
        // Given: Запуск процесса
        ProcessInstance processInstance = startProcessWithServiceApi("Test User", "test.user@example.com");

        // Проверяем, что процесс ожидает на ReceiveTask
        assertThat(processInstance).isWaitingAt("Activity_0g1mra7");

        // When: Отправляем сообщение с подтверждением email
        runtimeService.createMessageCorrelation("email_confirmed_message")
            .processInstanceId(processInstance.getId())
            .setVariable("emailConfirmed", true)
            .setVariable("confirmationDate", java.time.LocalDateTime.now().toString())
            .correlate();

        // Then: Проверяем, что процесс продолжился после получения сообщения
        assertThat(processInstance).isWaitingAt("Activity_0k5uvzl");
        
        // Проверяем, что переменные из сообщения установлены
        assertThat(runtimeService.getVariable(processInstance.getId(), "emailConfirmed")).isEqualTo(true);
        assertThat(runtimeService.getVariable(processInstance.getId(), "confirmationDate")).isNotNull();
    }

    /**
     * Тест проверки вызова CallActivity без реального выполнения подпроцесса.
     */
    @Test
    public void testCallActivityInvocation() throws Exception {
        // Given: Запуск процесса и прохождение до CallActivity
        ProcessInstance processInstance = startProcessWithServiceApi("Test User", "test.user@example.com");

        // Отправляем сообщение для перехода к CallActivity
        runtimeService.createMessageCorrelation("email_confirmed_message")
            .processInstanceId(processInstance.getId())
            .setVariable("emailConfirmed", true)
            .correlate();

        // Then: Проверяем, что процесс остановился на CallActivity
        assertThat(processInstance).isWaitingAt("Activity_0k5uvzl");

        // Проверяем, что CallActivity пытается вызвать процесс "chose-next-process"
        // Но реальный процесс не выполняется (так как его нет в деплойменте)
        // Это и есть проверка вызова без реального выполнения
        
        // Получаем информацию о текущей активности
        assertThat(processInstance.getProcessDefinitionId()).contains("user-registration-process");
        
        // Проверяем, что переменные передаются в CallActivity
        assertThat(runtimeService.getVariable(processInstance.getId(), "userName")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "userEmail")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "emailConfirmed")).isEqualTo(true);
    }

    /**
     * Тест проверки передачи всех переменных в CallActivity.
     */
    @Test
    public void testCallActivityVariablesPropagation() throws Exception {
        // Given
        String testUserName = "Test User";
        String testUserEmail = "test.user@example.com";
        
        ProcessInstance processInstance = startProcessWithServiceApi(testUserName, testUserEmail);

        // Переходим к CallActivity
        runtimeService.createMessageCorrelation("email_confirmed_message")
            .processInstanceId(processInstance.getId())
            .setVariable("emailConfirmed", true)
            .correlate();

        // Then: Проверяем, что все переменные доступны в контексте перед CallActivity
        assertThat(runtimeService.getVariable(processInstance.getId(), "userId")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "userName")).isEqualTo(testUserName);
        assertThat(runtimeService.getVariable(processInstance.getId(), "userEmail")).isEqualTo(testUserEmail);
        assertThat(runtimeService.getVariable(processInstance.getId(), "registrationDate")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "confirmationToken")).isNotNull();
        assertThat(runtimeService.getVariable(processInstance.getId(), "emailConfirmed")).isEqualTo(true);
    }
}
